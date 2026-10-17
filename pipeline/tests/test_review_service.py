"""The review tool's request handlers, as plain functions on tmp copies of the data files."""

import json
from pathlib import Path
from typing import Any

import pytest
from test_match import GAMES, RS_LIST, FakeVideos, entries, track, vid

from vgmixr_pipeline.catalog.build import Draft
from vgmixr_pipeline.catalog.jsonfmt import format_games
from vgmixr_pipeline.catalog.review.service import (
    BLOCK_COOLDOWN_SECONDS,
    LIVE_SPACING_SECONDS,
    Review,
    ReviewError,
)
from vgmixr_pipeline.catalog.youtube import Entry, Video, YouTubeBlocked

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
