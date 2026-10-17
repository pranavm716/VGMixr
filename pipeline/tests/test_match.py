"""Matching draft tracks to YouTube candidates (docs/roadmap.md, step 17)."""

from typing import Any

import pytest

from vgmixr_pipeline.catalog.build import Draft, DraftTrack
from vgmixr_pipeline.catalog.config import Overrides, YouTube
from vgmixr_pipeline.catalog.match import Match, candidates_json, match_tracks
from vgmixr_pipeline.catalog.score import Names
from vgmixr_pipeline.catalog.youtube import Chapter, Entry, Video
from vgmixr_pipeline.manifest.models import Game, LocalSource, StreamSource


def game(id_: str, name: str, short: str, family: str, generation: int) -> Game:
    return Game(id=id_, name=name, shortName=short, color="#000000", generation=generation,
                platform="GBA", family=family)  # fmt: skip


GAMES = [
    game("ruby", "Ruby", "R", "rse", 3),
    game("sapphire", "Sapphire", "S", "rse", 3),
    game("emerald", "Emerald", "E", "rse", 3),
    game("omega-ruby", "Omega Ruby", "OR", "oras", 6),
    game("alpha-sapphire", "Alpha Sapphire", "AS", "oras", 6),
]
NAMES = Names.build(GAMES, {})
RS_LIST = "PLrubysapphire"
E_LIST = "PLemeraldonly"
ORAS_LIST = "PLomegaalpha"


def track(
    title: str,
    order: int = 0,
    games: list[str] | None = None,
    sources: list[Any] | None = None,
    family: str = "rse",
    duration_ms: int | None = 60_000,
) -> DraftTrack:
    from vgmixr_pipeline.catalog.slug import track_id

    return DraftTrack(id=track_id(family, title), family=family, games=games, title=title,
                      category="other", order=order, sources=sources or [],
                      durationMs=duration_ms, origins=[])  # fmt: skip


def draft(*tracks: DraftTrack) -> Draft:
    return Draft(generatedAt="2026-01-01T00:00:00Z", games=GAMES, tracks=list(tracks))


def vid(n: int) -> str:
    return f"video{n:06d}"


class FakeVideos:
    def __init__(
        self,
        playlists: dict[str, list[Entry]] | None = None,
        searches: dict[str, list[Entry]] | None = None,
        videos: dict[str, Video] | None = None,
    ) -> None:
        self.playlists = playlists or {}
        self.searches = searches or {}
        self.videos = videos or {}
        self.calls: list[str] = []

    def playlist(self, playlist_id: str) -> list[Entry]:
        self.calls.append(f"playlist {playlist_id}")
        return self.playlists[playlist_id]

    def search(self, query: str, n: int = 10) -> list[Entry]:
        self.calls.append(f"search {query}")
        return self.searches.get(query, [])

    def video(self, video_id: str) -> Video:
        self.calls.append(f"video {video_id}")
        return self.videos[video_id]


def entries(*titles: str, start: int = 1, duration_ms: int = 60_000) -> list[Entry]:
    return [Entry(vid(start + i), t, duration_ms, "Uploader", i + 1) for i, t in enumerate(titles)]


def config(**kwargs: Any) -> YouTube:
    return YouTube.model_validate(kwargs)


RS_CONFIG = config(playlists=[{"games": ["ruby", "sapphire"], "playlist": RS_LIST}])


def run(
    d: Draft,
    videos: FakeVideos,
    youtube: YouTube = RS_CONFIG,
    overrides: Overrides | None = None,
    search: bool = True,
) -> dict[str, Match]:
    results = match_tracks(d, youtube, overrides or Overrides(), videos, NAMES, search=search)
    return {m.track_id: m for m in results}


def stream(video_id: str, confidence: float, start: int = 0, end: int | None = None) -> Any:
    return StreamSource(type="stream", videoId=video_id, startMs=start, endMs=end,
                        confidence=confidence)  # fmt: skip


def ids(match: Match) -> list[str]:
    return [s.videoId for s in match.streams]


def test_a_playlist_entry_with_the_tracks_title_is_accepted_without_searching() -> None:
    videos = FakeVideos({RS_LIST: entries("Littleroot Town", "Route 101")})
    result = run(draft(track("Littleroot Town", 0), track("Route 101", 1)), videos)
    assert ids(result["rse-route-101"]) == [vid(2)]
    assert result["rse-route-101"].status == "matched"
    assert result["rse-route-101"].streams[0].confidence == pytest.approx(1.0)
    assert not any(c.startswith("search") for c in videos.calls)
    assert videos.calls.count(f"playlist {RS_LIST}") == 1


def test_only_playlists_of_the_tracks_games_are_used() -> None:
    youtube = config(playlists=[
        {"games": ["emerald"], "playlist": E_LIST},
        {"games": ["omega-ruby", "alpha-sapphire"], "playlist": ORAS_LIST},
    ])  # fmt: skip
    videos = FakeVideos({E_LIST: entries("Battle Frontier"), ORAS_LIST: entries("Route 101")})
    ruby_only = track("Route 101", games=["ruby"])
    emerald_only = track("Battle Frontier", 1, games=["emerald"])
    result = run(draft(ruby_only, emerald_only), videos, youtube)
    assert ids(result["rse-battle-frontier"]) == [vid(1)]
    assert result["rse-route-101"].status == "unmatched"


def test_a_playlist_range_limits_its_entries() -> None:
    youtube = config(playlists=[{"games": ["ruby"], "playlist": RS_LIST, "first": 2, "last": 2}])
    videos = FakeVideos({RS_LIST: entries("Route 101", "Route 102", "Route 101")})
    result = run(draft(track("Route 101"), track("Route 102", 1)), videos, youtube)
    assert result["rse-route-101"].status == "unmatched"
    assert ids(result["rse-route-102"]) == [vid(2)]


def test_search_runs_only_for_tracks_with_nothing_accepted() -> None:
    query = "Pokémon Ruby Sapphire Emerald Route 102"
    videos = FakeVideos(
        {RS_LIST: entries("Route 101")},
        {query: [Entry(vid(9), "Pokémon Ruby & Sapphire - Route 102", 60_000, "U", 1)]},
    )
    result = run(draft(track("Route 101"), track("Route 102", 1)), videos)
    assert ids(result["rse-route-102"]) == [vid(9)]
    assert [c for c in videos.calls if c.startswith("search")] == [f"search {query}"]


def test_search_can_be_turned_off() -> None:
    videos = FakeVideos({RS_LIST: entries("Route 101")})
    result = run(draft(track("Route 102")), videos, search=False)
    assert result["rse-route-102"].status == "unmatched"
    assert not any(c.startswith("search") for c in videos.calls)


def test_album_chapters_come_last_with_their_slice() -> None:
    album = Video(vid(50), "RSE Full OST", 300_000, [
        Chapter("01. Littleroot Town", 0, 60_000),
        Chapter("02. Route 101", 60_000, 120_000),
        Chapter("03. Route 102", 120_000, None),
    ])  # fmt: skip
    youtube = config(
        playlists=[{"games": ["ruby"], "playlist": RS_LIST}],
        albums=[{"games": ["ruby", "sapphire", "emerald"], "video": vid(50)}],
    )
    videos = FakeVideos({RS_LIST: entries("Littleroot Town")}, videos={vid(50): album})
    result = run(draft(track("Littleroot Town"), track("Route 101", 1), track("Route 102", 2,
                       duration_ms=None)), videos, youtube)  # fmt: skip
    assert ids(result["rse-littleroot-town"]) == [vid(1)]
    route_101 = result["rse-route-101"].streams[0]
    assert (route_101.videoId, route_101.startMs, route_101.endMs) == (vid(50), 60_000, 120_000)
    route_102 = result["rse-route-102"].streams[0]
    assert (route_102.startMs, route_102.endMs) == (120_000, None)
    assert videos.calls.count(f"video {vid(50)}") == 1


def test_at_most_three_accepted_sources_best_first() -> None:
    youtube = config(playlists=[
        {"games": ["ruby"], "playlist": RS_LIST},
        {"games": ["sapphire"], "playlist": E_LIST},
    ])  # fmt: skip
    videos = FakeVideos({
        RS_LIST: entries("Route 101", "Route 101 (Ruby)", "Route 101"),
        E_LIST: entries("Route 101", "Route 101", start=3),
    })  # fmt: skip
    match = run(draft(track("Route 101")), videos, youtube)["rse-route-101"]
    assert len(match.streams) == 3
    assert len(set(ids(match))) == 3
    confidences = [s.confidence or 0 for s in match.streams]
    assert confidences == sorted(confidences, reverse=True)
    assert vid(2) not in ids(match)


def test_tracks_with_local_files_still_get_streams() -> None:
    local = LocalSource(type="local", path="rse/route-101.m4a")
    videos = FakeVideos({RS_LIST: entries("Route 101")})
    match = run(draft(track("Route 101", sources=[local])), videos)["rse-route-101"]
    assert (match.status, ids(match)) == ("matched", [vid(1)])


def test_accepted_overrides_go_first_and_auto_matches_fill_up() -> None:
    overrides = Overrides.model_validate({"tracks": {"rse-route-101": {"streams": {
        "accept": [{"videoId": vid(7), "startMs": 5000}], "reject": [vid(1)]}}}})  # fmt: skip
    videos = FakeVideos({RS_LIST: entries("Route 101", "Route 101")})
    match = run(draft(track("Route 101")), videos, overrides=overrides)["rse-route-101"]
    assert match.streams[0] == stream(vid(7), 1.0, start=5000)
    assert ids(match) == [vid(7), vid(2)]


def test_a_review_band_track_needs_review_and_has_no_streams() -> None:
    videos = FakeVideos({RS_LIST: entries("Rte 101")})
    match = run(draft(track("Route 101")), videos, search=False)["rse-route-101"]
    assert (match.status, match.streams) == ("review", [])
    assert match.candidates[0].candidate.video_id == vid(1)


def test_rejecting_every_review_candidate_leaves_the_track_unmatched() -> None:
    overrides = Overrides.model_validate(
        {"tracks": {"rse-route-101": {"streams": {"reject": [vid(1)]}}}}
    )
    videos = FakeVideos({RS_LIST: entries("Rte 101")})
    match = run(draft(track("Route 101")), videos, overrides=overrides, search=False)
    assert match["rse-route-101"].status == "unmatched"
    assert match["rse-route-101"].candidates == []


def test_low_scoring_candidates_are_kept_for_review_and_labeling() -> None:
    videos = FakeVideos({RS_LIST: entries("Littleroot Town")})
    match = run(draft(track("Route 101")), videos, search=False)["rse-route-101"]
    assert match.status == "unmatched"
    assert [c.candidate.title for c in match.candidates] == ["Littleroot Town"]


def test_candidates_serialize_for_the_review_tool() -> None:
    videos = FakeVideos({RS_LIST: entries("Rte 101")})
    match = run(draft(track("Route 101")), videos, search=False)["rse-route-101"]
    (data,) = candidates_json([match])
    assert data["id"] == "rse-route-101"
    assert data["status"] == "review"
    (candidate,) = data["candidates"]
    assert candidate["videoId"] == vid(1)
    assert candidate["title"] == "Rte 101"
    assert candidate["kind"] == "playlist"
    assert (candidate["startMs"], candidate["endMs"], candidate["durationMs"]) == (0, None, 60_000)
    assert 0.5 <= candidate["score"] < 0.8
    assert "title" in candidate["reasons"][0]
    assert candidate["noise"] == []
    assert data["track"] == {
        "games": ["emerald", "ruby", "sapphire"],
        "durationMs": 60_000,
        "position": None,
        "gameBoy": False,
    }


def test_words_common_to_a_playlist_do_not_count_against_titles() -> None:
    suffix = " - Johto Sounds Remastered Collection"
    titles = [t + suffix for t in ("Route 1", "Route 2", "Pallet Town", "Viridian City")]
    videos = FakeVideos({RS_LIST: entries(*titles)})
    match = run(draft(track("Pallet Town", 2), track("Route 1", 0)), videos, search=False)
    assert match["rse-pallet-town"].status == "matched"
    assert match["rse-route-1"].status == "matched"
