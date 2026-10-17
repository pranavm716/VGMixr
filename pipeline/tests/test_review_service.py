"""The review tool's request handlers, as plain functions on tmp copies of the data files."""

import json
from pathlib import Path
from typing import Any

import pytest
from test_match import GAMES, ORAS_LIST, RS_LIST, FakeVideos, entries, track, vid

from vgmixr_pipeline.catalog.build import Draft
from vgmixr_pipeline.catalog.jsonfmt import format_games
from vgmixr_pipeline.catalog.review.service import (
    BLOCK_COOLDOWN_SECONDS,
    LIVE_SPACING_SECONDS,
    Review,
    ReviewError,
)
from vgmixr_pipeline.catalog.youtube import Entry, PlaylistHit, Video, YouTubeBlocked

TRACKS = [track("Littleroot Town", 0), track("Route 101", 1), track("Abandoned Ship", 2)]
PLAYLIST = entries("Littleroot Town", "Rte 101", "Littleroot Town")


class CachedVideos(FakeVideos):
    """Like the cached-only client: an uncached video has no title or length."""

    def video(self, video_id: str) -> Video:
        self.calls.append(f"video {video_id}")
        return self.videos.get(video_id, Video(video_id, "", None, []))


class LiveVideos(CachedVideos):
    def __init__(self, blocked: bool = False, **kwargs: Any) -> None:
        super().__init__(**kwargs)
        self.blocked = blocked

    def search(self, query: str, n: int = 10) -> list[Entry]:
        if self.blocked:
            raise YouTubeBlocked("Sign in to confirm you're not a bot")
        return super().search(query, n)


class Clock:
    def __init__(self) -> None:
        self.now = 1_000_000.0
        self.sleeps: list[float] = []

    def __call__(self) -> float:
        return self.now

    def sleep(self, seconds: float) -> None:
        self.sleeps.append(seconds)
        self.now += seconds


def write_inputs(tmp_path: Path, tracks: list[Any]) -> tuple[Path, Path]:
    data, out = tmp_path / "data", tmp_path / "out"
    data.mkdir()
    out.mkdir(exist_ok=True)
    games = {
        "games": [g.model_dump(exclude_none=True) for g in GAMES],
        "tracklists": [],
        "youtube": {"playlists": [{"games": ["ruby", "sapphire"], "playlist": RS_LIST}]},
    }
    (data / "games.json").write_text(format_games(games), encoding="utf-8")
    (data / "overrides.json").write_text('{\n  "tracks": {},\n  "extras": []\n}\n')
    write_draft(out, tracks)
    return data, out


def write_draft(out: Path, tracks: list[Any]) -> None:
    draft = Draft(generatedAt="2026-01-01T00:00:00Z", games=GAMES, tracks=tracks)
    (out / "draft.json").write_text(draft.model_dump_json(indent=2), encoding="utf-8")


@pytest.fixture
def clock() -> Clock:
    return Clock()


@pytest.fixture
def live() -> LiveVideos:
    return LiveVideos(searches={"my query": entries("Route 101", start=50)})


def make(
    tmp_path: Path, clock: Clock, live: LiveVideos | None, cached_only: bool = False, **kw: Any
) -> Review:
    data, out = write_inputs(tmp_path, TRACKS)
    cached = CachedVideos({RS_LIST: PLAYLIST})
    return Review(data, out, cached, live, clock=clock, sleep=clock.sleep,
                  cached_only=cached_only, **kw)  # fmt: skip


@pytest.fixture
def review(tmp_path: Path, clock: Clock, live: LiveVideos) -> Review:
    return make(tmp_path, clock, live)


def test_state_lists_families_with_their_counts_and_playlists(review: Review) -> None:
    state = review.state()
    rse, oras = state["families"]
    assert (rse["id"], rse["label"], rse["playlists"]) == ("rse", "RSE", 1)
    assert rse["counts"] == {"matched": 1, "review": 1, "unmatched": 1}
    assert [g["shortName"] for g in rse["games"]] == ["R", "S", "E"]
    assert (oras["id"], oras["playlists"], oras["counts"]["matched"]) == ("oras", 0, 0)
    assert state["youtube"] == {"mode": "live"}


def test_track_list_puts_needs_review_first_then_unmatched_then_matched(review: Review) -> None:
    rows = review.tracks("rse", None)
    assert [r["id"] for r in rows] == ["rse-route-101", "rse-abandoned-ship", "rse-littleroot-town"]
    assert 0.5 <= rows[0]["best"] < 0.8
    assert rows[2]["sources"] == 2 and rows[2]["decided"] is False
    assert [r["id"] for r in review.tracks("rse", "review")] == ["rse-route-101"]
    assert review.tracks("oras", None) == []


def test_track_detail_has_facts_candidates_decision_and_query(review: Review) -> None:
    detail = review.track("rse-route-101")
    assert detail["status"] == "review"
    assert detail["games"] == ["ruby", "sapphire", "emerald"]
    assert detail["durationMs"] == 60_000
    assert detail["candidates"][0]["videoId"] == vid(2)
    assert detail["candidates"][0]["accepted"] is False
    assert detail["streams"] == []
    assert detail["decision"] == {"accept": [], "reject": []}
    assert detail["query"] == "Pokémon Ruby Sapphire Emerald Route 101"


def test_an_unknown_track_is_an_error(review: Review) -> None:
    with pytest.raises(ReviewError):
        review.track("rse-nope")


def test_accepting_a_candidate_matches_the_track_and_writes_the_override(
    review: Review, tmp_path: Path
) -> None:
    detail = review.set_streams("rse-route-101", {"accept": [{"videoId": vid(2)}]})
    assert detail["status"] == "matched"
    assert detail["streams"] == [
        {"videoId": vid(2), "startMs": 0, "endMs": None, "confidence": 1.0, "auto": False}
    ]
    assert detail["decision"]["accept"] == [{"videoId": vid(2), "startMs": 0, "endMs": None}]
    assert detail["candidates"][0]["accepted"] is True
    written = json.loads((tmp_path / "data" / "overrides.json").read_text(encoding="utf-8"))
    assert written["tracks"] == {"rse-route-101": {"streams": {"accept": [{"videoId": vid(2)}]}}}
    assert review.state()["families"][0]["counts"] == {"matched": 2, "review": 0, "unmatched": 1}
    assert review.tracks("rse", "review") == []


def test_rejecting_a_wrong_auto_accept_moves_the_next_one_up(review: Review) -> None:
    before = review.track("rse-littleroot-town")
    assert [(s["videoId"], s["auto"]) for s in before["streams"]] == [
        (vid(1), True),
        (vid(3), True),
    ]
    after = review.set_streams("rse-littleroot-town", {"reject": [vid(1)]})
    assert [s["videoId"] for s in after["streams"]] == [vid(3)]
    assert vid(1) not in [c["videoId"] for c in after["candidates"]]
    assert after["decision"] == {"accept": [], "reject": [vid(1)]}


def test_an_invalid_decision_is_refused_and_nothing_changes(review: Review, tmp_path: Path) -> None:
    path = tmp_path / "data" / "overrides.json"
    before = path.read_bytes()
    with pytest.raises(ReviewError) as raised:
        review.set_streams("rse-route-101", {"accept": [{"videoId": vid(2)}], "reject": [vid(2)]})
    assert raised.value.errors
    assert path.read_bytes() == before
    assert review.track("rse-route-101")["status"] == "review"


def test_search_scores_live_results_against_the_track(review: Review, live: LiveVideos) -> None:
    result = review.search("rse-route-101", "my query")
    assert [r["videoId"] for r in result["results"]] == [vid(50)]
    assert result["results"][0]["score"] >= 0.8
    assert result["results"][0]["kind"] == "search"
    assert live.calls == ["search my query"]
    assert result["youtube"] == {"mode": "live"}


def test_an_empty_search_uses_the_builders_query(review: Review, live: LiveVideos) -> None:
    review.search("rse-route-101", " ")
    assert live.calls == ["search Pokémon Ruby Sapphire Emerald Route 101"]


def test_cached_only_never_asks_youtube(tmp_path: Path, clock: Clock, live: LiveVideos) -> None:
    review = make(tmp_path, clock, live, cached_only=True)
    result = review.search("rse-route-101", "my query")
    assert result["results"] == []
    assert live.calls == []
    assert review.state()["youtube"] == {"mode": "cached-only"}


def test_a_block_falls_back_to_the_cache_until_the_cooldown_ends(
    tmp_path: Path, clock: Clock
) -> None:
    live = LiveVideos(blocked=True)
    review = make(tmp_path, clock, live)
    result = review.search("rse-route-101", "my query")
    assert result["results"] == []
    until = clock.now + BLOCK_COOLDOWN_SECONDS
    assert result["youtube"] == {"mode": "blocked", "until": until}
    assert "not a bot" in result["notice"]

    live.blocked = False
    review.search("rse-route-101", "my query")
    assert live.calls == []  # still blocked
    clock.now = until
    review.search("rse-route-101", "my query")
    assert live.calls == ["search my query"]


def test_retry_lifts_a_block(tmp_path: Path, clock: Clock) -> None:
    review = make(tmp_path, clock, LiveVideos(blocked=True))
    review.search("rse-route-101", "my query")
    assert review.retry_youtube() == {"mode": "live"}


def test_live_requests_are_spaced_out(review: Review, clock: Clock) -> None:
    review.search("rse-route-101", "my query")
    clock.now += 1
    review.search("rse-route-101", "other query")
    assert clock.sleeps == [LIVE_SPACING_SECONDS - 1]


def test_a_pasted_link_becomes_a_scored_candidate(review: Review, live: LiveVideos) -> None:
    live.videos[vid(60)] = Video(vid(60), "Route 101", 60_000, [])
    result = review.video("rse-route-101", f"https://youtu.be/{vid(60)}?t=3", None, None)
    candidate = result["candidate"]
    assert (candidate["videoId"], candidate["title"]) == (vid(60), "Route 101")
    assert candidate["score"] >= 0.8


def test_a_pasted_slice_is_scored_by_its_own_length(review: Review, live: LiveVideos) -> None:
    live.videos[vid(60)] = Video(vid(60), "Route 101", 600_000, [])
    candidate = review.video("rse-route-101", vid(60), 60_000, 120_000)["candidate"]
    assert (candidate["startMs"], candidate["endMs"], candidate["durationMs"]) == (
        60_000,
        120_000,
        60_000,
    )
    assert not any(r.startswith("length") for r in candidate["reasons"])


def test_a_pasted_video_without_details_is_unscored(
    tmp_path: Path, clock: Clock, live: LiveVideos
) -> None:
    review = make(tmp_path, clock, live, cached_only=True)
    candidate = review.video("rse-route-101", vid(60), None, None)["candidate"]
    assert (candidate["videoId"], candidate["title"], candidate["score"]) == (vid(60), None, None)


@pytest.mark.parametrize(("text", "start", "end"), [("not a link", None, None),
                                                    (vid(60), 5000, 1000)])  # fmt: skip
def test_bad_paste_is_an_error(
    review: Review, text: str, start: int | None, end: int | None
) -> None:
    with pytest.raises(ReviewError):
        review.video("rse-route-101", text, start, end)


def test_rebuild_runs_the_builder_and_reloads_the_draft(
    tmp_path: Path, clock: Clock, live: LiveVideos
) -> None:
    def rebuild() -> tuple[bool, str]:
        write_draft(tmp_path / "out", [*TRACKS, track("Petalburg City", 3)])
        return True, "Wrote catalog/manifest.json."

    review = make(tmp_path, clock, live, rebuild=rebuild)
    result = review.rebuild()
    assert (result["ok"], result["output"]) == (True, "Wrote catalog/manifest.json.")
    assert "rse-petalburg-city" in [r["id"] for r in review.tracks("rse", None)]


def test_a_removed_track_is_excluded_until_restored(review: Review, tmp_path: Path) -> None:
    detail = review.exclude("rse-abandoned-ship", True)
    assert detail["status"] == "excluded"
    written = json.loads((tmp_path / "data" / "overrides.json").read_text(encoding="utf-8"))
    assert written["tracks"] == {"rse-abandoned-ship": {"exclude": True}}
    assert "rse-abandoned-ship" not in [r["id"] for r in review.tracks("rse", "unmatched")]
    assert [r["id"] for r in review.tracks("rse", "excluded")] == ["rse-abandoned-ship"]
    counts = review.state()["families"][0]["counts"]
    assert (counts["unmatched"], counts["excluded"]) == (0, 1)

    assert review.exclude("rse-abandoned-ship", False)["status"] == "unmatched"
    written = json.loads((tmp_path / "data" / "overrides.json").read_text(encoding="utf-8"))
    assert written["tracks"] == {}


def test_restoring_a_short_track_keeps_it_out_of_the_jingle_rule(
    tmp_path: Path, clock: Clock, live: LiveVideos
) -> None:
    data, out = write_inputs(tmp_path, [track("Cable Car", 0, duration_ms=12_000)])
    review = Review(data, out, CachedVideos({RS_LIST: []}), live, clock=clock, sleep=clock.sleep)
    review.exclude("rse-cable-car", True)
    review.exclude("rse-cable-car", False)
    written = json.loads((data / "overrides.json").read_text(encoding="utf-8"))
    assert written["tracks"] == {"rse-cable-car": {"exclude": False}}


def test_hand_accepted_videos_come_with_details_in_order(
    tmp_path: Path, clock: Clock, live: LiveVideos
) -> None:
    data, out = write_inputs(tmp_path, TRACKS)
    cached = CachedVideos(
        {RS_LIST: PLAYLIST}, videos={vid(60): Video(vid(60), "Route 101", 60_000, [])}
    )
    review = Review(data, out, cached, live, clock=clock, sleep=clock.sleep)
    accept = [{"videoId": vid(60), "startMs": 3500}, {"videoId": vid(2)}, {"videoId": vid(61)}]
    detail = review.set_streams("rse-route-101", {"accept": accept})
    pasted, candidate, unknown = detail["accepted"]
    assert (pasted["videoId"], pasted["startMs"], pasted["title"]) == (vid(60), 3500, "Route 101")
    assert pasted["durationMs"] == 56_500 and pasted["score"] is not None
    assert (candidate["videoId"], candidate["title"]) == (vid(2), "Rte 101")
    assert (unknown["videoId"], unknown["title"], unknown["score"]) == (vid(61), None, None)
    assert review.track("rse-littleroot-town")["accepted"] == []


def test_a_title_override_shows_before_the_next_build(
    tmp_path: Path, clock: Clock, live: LiveVideos
) -> None:
    data, out = write_inputs(tmp_path, TRACKS)
    (data / "overrides.json").write_text(
        '{ "tracks": { "rse-route-101": { "title": "Battle! (Wally)" } }, "extras": [] }'
    )
    review = Review(data, out, CachedVideos({RS_LIST: PLAYLIST}), live, clock=clock,
                    sleep=clock.sleep)  # fmt: skip
    (row,) = [r for r in review.tracks("rse", None) if r["id"] == "rse-route-101"]
    assert row["title"] == "Battle! (Wally)"
    detail = review.track("rse-route-101")
    assert detail["title"] == "Battle! (Wally)"
    assert detail["query"] == "Pokémon Ruby Sapphire Emerald Battle! (Wally)"


def test_accepting_an_unknown_video_looks_up_its_details_once(
    review: Review, live: LiveVideos
) -> None:
    live.videos[vid(70)] = Video(vid(70), "Route 101", 60_000, [])
    detail = review.set_streams("rse-route-101", {"accept": [{"videoId": vid(70)}]})
    assert (detail["accepted"][0]["title"], detail["accepted"][0]["durationMs"]) == (
        "Route 101",
        60_000,
    )
    review.track("rse-route-101")
    assert live.calls.count(f"video {vid(70)}") == 1


def test_a_known_video_is_not_looked_up_again(review: Review, live: LiveVideos) -> None:
    review.set_streams("rse-route-101", {"accept": [{"videoId": vid(2)}]})
    assert live.calls == []


def test_a_matched_tracks_row_shows_its_first_sources_confidence(review: Review) -> None:
    review.set_streams("rse-route-101", {"accept": [{"videoId": vid(2)}]})
    rows = {r["id"]: r for r in review.tracks("rse", None)}
    assert rows["rse-route-101"]["confidence"] == 1.0
    assert rows["rse-littleroot-town"]["confidence"] == pytest.approx(
        review.track("rse-littleroot-town")["streams"][0]["confidence"]
    )
    assert rows["rse-abandoned-ship"]["confidence"] is None


# Playlists

ORAS_TRACKS = [
    track("Littleroot Town", 0, family="oras"),
    track("Battle! Wild Pokémon", 1, family="oras"),
    track("Route 101", 2, family="oras"),
]
ORAS_PLAYLIST = entries("Littleroot Town", "Battle! Wild Pokémon", "Petalburg Woods", start=80)
ORAS_QUERY = "Pokémon Omega Ruby Alpha Sapphire soundtrack"


class CachedPlaylists(CachedVideos):
    """Like the cached-only client: an uncached playlist is empty."""

    def playlist(self, playlist_id: str) -> list[Entry]:
        self.calls.append(f"playlist {playlist_id}")
        return self.playlists.get(playlist_id, [])


class SharedLive(LiveVideos):
    """Like the live client: what it fetches is cached, so the cached client sees it next."""

    def __init__(self, cache: FakeVideos, **kwargs: Any) -> None:
        super().__init__(**kwargs)
        self.cache = cache

    def playlist(self, playlist_id: str) -> list[Entry]:
        if self.blocked:
            raise YouTubeBlocked("Sign in to confirm you're not a bot")
        found = super().playlist(playlist_id)
        self.cache.playlists[playlist_id] = found
        return found

    def search_playlists(self, query: str, n: int = 15) -> list[PlaylistHit]:
        if self.blocked:
            raise YouTubeBlocked("Sign in to confirm you're not a bot")
        return super().search_playlists(query, n)


def make_playlists(
    tmp_path: Path, clock: Clock, cached_only: bool = False, blocked: bool = False
) -> tuple[Review, CachedPlaylists, SharedLive]:
    data, out = write_inputs(tmp_path, [*TRACKS, *ORAS_TRACKS])
    cached = CachedPlaylists({RS_LIST: PLAYLIST})
    live = SharedLive(
        cached,
        blocked=blocked,
        playlists={ORAS_LIST: ORAS_PLAYLIST},
        playlist_searches={ORAS_QUERY: [PlaylistHit(ORAS_LIST, "ORAS OST", "Uploader"),
                                        PlaylistHit(RS_LIST, "RS rip", "Daintii")]},
    )  # fmt: skip
    review = Review(data, out, cached, live, clock=clock, sleep=clock.sleep,
                    cached_only=cached_only)  # fmt: skip
    return review, cached, live


def games_file(tmp_path: Path) -> dict[str, Any]:
    path = tmp_path / "data" / "games.json"
    data: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
    return data


def test_coverage_ranks_families_without_playlists_first_with_per_game_gaps(
    tmp_path: Path, clock: Clock
) -> None:
    review, _, _ = make_playlists(tmp_path, clock)
    oras, rse = review.playlists()["families"]
    assert (oras["id"], oras["playlists"], rse["id"]) == ("oras", [], "rse")
    assert oras["counts"] == {"matched": 0, "review": 0, "unmatched": 3}
    assert oras["games"][0] == {
        "id": "omega-ruby", "shortName": "OR", "color": "#000000",
        "playlists": 0, "tracks": 3, "review": 0, "unmatched": 3,
    }  # fmt: skip
    ruby, _, emerald = rse["games"]
    assert (ruby["playlists"], ruby["review"], ruby["unmatched"]) == (1, 1, 1)
    assert emerald["playlists"] == 0
    assert rse["playlists"] == [{
        "index": 0, "playlist": RS_LIST, "games": ["ruby", "sapphire"],
        "first": None, "last": None, "note": None, "entries": 3,
    }]  # fmt: skip


def test_coverage_ranks_by_the_share_of_tracks_without_sources(
    tmp_path: Path, clock: Clock
) -> None:
    review, _, _ = make_playlists(tmp_path, clock)
    review.add_playlist(ORAS_LIST, ["omega-ruby", "alpha-sapphire"], None, None, None)
    # oras: 2 of 3 matched now; rse: 1 of 3.
    assert [f["id"] for f in review.playlists()["families"]] == ["rse", "oras"]


def test_a_playlist_search_marks_listed_and_cached_playlists(tmp_path: Path, clock: Clock) -> None:
    review, _, live = make_playlists(tmp_path, clock)
    result = review.search_playlists("oras", " ")
    assert result["query"] == ORAS_QUERY
    assert live.calls == [f"playlists {ORAS_QUERY}"]
    assert result["results"] == [
        {
            "id": ORAS_LIST,
            "title": "ORAS OST",
            "channel": "Uploader",
            "listed": [],
            "entries": None,
        },
        {"id": RS_LIST, "title": "RS rip", "channel": "Daintii", "listed": [0], "entries": 3},
    ]
    assert result["youtube"] == {"mode": "live"}


def test_playlist_searches_are_spaced_out_and_fall_back_to_the_cache(
    tmp_path: Path, clock: Clock
) -> None:
    review, cached, live = make_playlists(tmp_path, clock)
    review.search_playlists("oras", "a")
    clock.now += 2
    review.search_playlists("oras", "b")
    assert clock.sleeps == [LIVE_SPACING_SECONDS - 2]

    live.blocked = True
    cached.playlist_searches["c"] = [PlaylistHit(ORAS_LIST, "Cached", None)]
    result = review.search_playlists("oras", "c")
    assert [r["title"] for r in result["results"]] == ["Cached"]
    assert "not a bot" in result["notice"]
    assert result["youtube"]["mode"] == "blocked"


def test_cached_only_never_searches_youtube_for_playlists(tmp_path: Path, clock: Clock) -> None:
    review, _, live = make_playlists(tmp_path, clock, cached_only=True)
    assert review.search_playlists("oras", "")["results"] == []
    assert live.calls == []


def test_a_preview_matches_entries_to_tracks_and_writes_nothing(
    tmp_path: Path, clock: Clock
) -> None:
    review, _, _ = make_playlists(tmp_path, clock)
    before = (tmp_path / "data" / "games.json").read_bytes()
    url = f"https://www.youtube.com/playlist?list={ORAS_LIST}"
    preview = review.preview_playlist(url, ["omega-ruby", "alpha-sapphire"], None, 2)
    assert preview["playlist"] == ORAS_LIST
    littleroot, battle, woods = preview["entries"]
    assert (littleroot["position"], littleroot["videoId"], littleroot["inRange"]) == (
        1, vid(80), True
    )  # fmt: skip
    assert littleroot["match"]["trackId"] == "oras-littleroot-town"
    assert (littleroot["match"]["verdict"], littleroot["match"]["source"]) == ("accept", True)
    assert battle["match"]["trackId"] == "oras-battle-wild-pokemon"
    assert woods["inRange"] is False and woods["match"] is None
    assert preview["counts"] == {"oras": {
        "before": {"matched": 0, "review": 0, "unmatched": 3},
        "after": {"matched": 2, "review": 0, "unmatched": 1},
    }}  # fmt: skip
    assert [c["id"] for c in preview["changes"]] == [
        "oras-littleroot-town", "oras-battle-wild-pokemon"
    ]  # fmt: skip
    change = preview["changes"][0]
    assert (change["hand"], change["before"]["status"], change["after"]["status"]) == (
        False, "unmatched", "matched"
    )  # fmt: skip
    assert change["after"]["sources"][0]["videoId"] == vid(80)
    # Nothing was written and the tool's own matches are unchanged.
    assert (tmp_path / "data" / "games.json").read_bytes() == before
    assert review.state()["families"][1]["counts"]["matched"] == 0


def test_a_playlist_is_fetched_once_then_read_from_the_cache(tmp_path: Path, clock: Clock) -> None:
    review, _, live = make_playlists(tmp_path, clock)
    review.preview_playlist(ORAS_LIST, ["omega-ruby"], None, None)
    review.preview_playlist(ORAS_LIST, ["omega-ruby", "alpha-sapphire"], 1, 2)
    review.add_playlist(ORAS_LIST, ["omega-ruby", "alpha-sapphire"], None, None, None)
    assert live.calls == [f"playlist {ORAS_LIST}"]
    assert clock.sleeps == []


def test_hand_decisions_stay_first_when_a_playlist_is_added(tmp_path: Path, clock: Clock) -> None:
    review, _, _ = make_playlists(tmp_path, clock)
    review.set_streams("oras-littleroot-town", {"accept": [{"videoId": vid(70)}]})
    preview = review.preview_playlist(ORAS_LIST, ["omega-ruby", "alpha-sapphire"], None, None)
    change = next(c for c in preview["changes"] if c["id"] == "oras-littleroot-town")
    assert change["hand"] is True
    assert [s["videoId"] for s in change["before"]["sources"]] == [vid(70)]
    assert [(s["videoId"], s["auto"]) for s in change["after"]["sources"]] == [
        (vid(70), False), (vid(80), True)
    ]  # fmt: skip

    review.add_playlist(ORAS_LIST, ["omega-ruby", "alpha-sapphire"], None, None, None)
    streams = review.track("oras-littleroot-town")["streams"]
    assert [(s["videoId"], s["auto"]) for s in streams] == [(vid(70), False), (vid(80), True)]


def test_a_playlist_spanning_families_previews_both(tmp_path: Path, clock: Clock) -> None:
    review, _, _ = make_playlists(tmp_path, clock)
    preview = review.preview_playlist(ORAS_LIST, ["omega-ruby", "emerald"], None, None)
    assert list(preview["counts"]) == ["rse", "oras"]
    assert preview["counts"]["oras"]["after"]["matched"] == 2


def test_adding_a_playlist_writes_it_and_rematches_its_families(
    tmp_path: Path, clock: Clock
) -> None:
    review, _, _ = make_playlists(tmp_path, clock)
    result = review.add_playlist(ORAS_LIST, ["omega-ruby", "alpha-sapphire"], None, 2, "ORAS OST")
    written = games_file(tmp_path)["youtube"]["playlists"]
    assert written[-1] == {"games": ["omega-ruby", "alpha-sapphire"], "playlist": ORAS_LIST,
                           "last": 2, "note": "ORAS OST"}  # fmt: skip
    oras = next(f for f in result["families"] if f["id"] == "oras")
    assert oras["counts"] == {"matched": 2, "review": 0, "unmatched": 1}
    assert oras["playlists"][0]["index"] == 1
    assert review.state()["families"][1]["playlists"] == 1
    assert [r["id"] for r in review.tracks("oras", "matched")] == [
        "oras-littleroot-town", "oras-battle-wild-pokemon"
    ]  # fmt: skip


def test_removing_a_playlist_previews_then_rematches(tmp_path: Path, clock: Clock) -> None:
    review, _, _ = make_playlists(tmp_path, clock)
    preview = review.preview_removal(0, RS_LIST)
    assert preview["counts"]["rse"] == {
        "before": {"matched": 1, "review": 1, "unmatched": 1},
        "after": {"matched": 0, "review": 0, "unmatched": 3},
    }
    assert preview["entries"][0]["match"]["trackId"] == "rse-littleroot-town"
    assert {c["id"] for c in preview["changes"]} == {"rse-littleroot-town", "rse-route-101"}

    result = review.remove_playlist(0, RS_LIST)
    assert games_file(tmp_path)["youtube"]["playlists"] == []
    assert next(f for f in result["families"] if f["id"] == "rse")["counts"]["matched"] == 0
    with pytest.raises(ReviewError):
        review.remove_playlist(0, RS_LIST)


def test_a_hand_accepted_video_stays_when_its_playlist_is_removed(
    tmp_path: Path, clock: Clock
) -> None:
    review, _, _ = make_playlists(tmp_path, clock)
    review.set_streams("rse-littleroot-town", {"accept": [{"videoId": vid(1)}]})
    preview = review.preview_removal(0, RS_LIST)
    change = next(c for c in preview["changes"] if c["id"] == "rse-littleroot-town")
    assert change["hand"] is True and change["after"]["status"] == "matched"
    assert [s["videoId"] for s in change["before"]["sources"]] == [vid(1), vid(3)]
    assert [s["videoId"] for s in change["after"]["sources"]] == [vid(1)]
    review.remove_playlist(0, RS_LIST)
    streams = review.track("rse-littleroot-town")["streams"]
    assert [(s["videoId"], s["auto"]) for s in streams] == [(vid(1), False)]


@pytest.mark.parametrize("mode", ["blocked", "cached-only"])
def test_an_uncached_playlist_cant_be_previewed_without_youtube(
    tmp_path: Path, clock: Clock, mode: str
) -> None:
    review, _, _ = make_playlists(
        tmp_path, clock, cached_only=mode == "cached-only", blocked=mode == "blocked"
    )
    with pytest.raises(ReviewError) as raised:
        review.preview_playlist(ORAS_LIST, ["omega-ruby"], None, None)
    assert "not cached" in raised.value.errors[0]


@pytest.mark.parametrize(
    ("text", "games", "first", "last"),
    [
        ("not a playlist", ["omega-ruby"], None, None),
        (ORAS_LIST, [], None, None),
        (ORAS_LIST, ["unknown"], None, None),
        (ORAS_LIST, ["omega-ruby"], 3, 1),
        (ORAS_LIST, "omega-ruby", None, None),
    ],
)
def test_a_bad_playlist_preview_is_an_error(
    tmp_path: Path, clock: Clock, text: str, games: Any, first: int | None, last: int | None
) -> None:
    review, _, live = make_playlists(tmp_path, clock)
    with pytest.raises(ReviewError):
        review.preview_playlist(text, games, first, last)
    assert live.calls == []
