"""The review tool's Broken tab: the app's reports joined to the draft (docs/broken.md)."""

import json
from pathlib import Path
from typing import Any

import pytest
from test_match import RS_LIST, vid
from test_recheck import FINE_INFO, REFUSED_INFO, FakeProbe, checks
from test_review_service import (
    PLAYLIST,
    TRACKS,
    CachedVideos,
    Clock,
    LiveVideos,
    make,
    write_inputs,
)

from vgmixr_pipeline.broken.recheck import CheckFailed
from vgmixr_pipeline.catalog.review.service import (
    BLOCK_COOLDOWN_SECONDS,
    LIVE_SPACING_SECONDS,
    Review,
    ReviewError,
)
from vgmixr_pipeline.catalog.youtube import YouTubeBlocked

BUILT_AT = "2026-10-22T03:39:13Z"
OLDER = "2026-10-21T03:12:40Z"


def mark(track_id: str, video_id: str, **kw: Any) -> dict[str, Any]:
    return {
        "trackId": track_id,
        "videoId": video_id,
        "reason": "no-audio",
        "detail": "no playable audio stream",
        "markedAt": "2026-10-22T08:04:03.067Z",
        "catalogGeneratedAt": BUILT_AT,
    } | kw


def pull(tmp_path: Path, *marks: dict[str, Any]) -> None:
    text = json.dumps({"version": 1, "marks": list(marks)})
    (tmp_path / "out" / "broken.json").write_text(text, encoding="utf-8")


def write_manifest(tmp_path: Path, generated_at: str, videos: list[str]) -> Path:
    path = tmp_path / "manifest.json"
    tracks = [{"id": f"t{i}", "sources": [{"type": "stream", "videoId": v}]}
              for i, v in enumerate(videos)]  # fmt: skip
    path.write_text(json.dumps({"generatedAt": generated_at, "tracks": tracks}), encoding="utf-8")
    return path


@pytest.fixture
def clock() -> Clock:
    return Clock()


@pytest.fixture
def live() -> LiveVideos:
    return LiveVideos()


@pytest.fixture
def probe() -> FakeProbe:
    return FakeProbe({vid(1): (REFUSED_INFO, ["This video is not available"])})


@pytest.fixture
def review(tmp_path: Path, clock: Clock, live: LiveVideos, probe: FakeProbe) -> Review:
    # Littleroot Town plays vid(1) then vid(3); Route 101 and Abandoned Ship have no source.
    manifest = write_manifest(tmp_path, BUILT_AT, [vid(1), vid(3)])
    return make(tmp_path, clock, live, manifest_path=manifest, app_checks=checks(tmp_path, probe))


def test_nothing_pulled_yet(review: Review) -> None:
    broken = review.broken()

    assert broken["pulled"] == {"exists": False, "at": None, "error": None}
    assert broken["reports"] == []
    assert broken["manifestGeneratedAt"] == BUILT_AT


def test_an_invalid_pull_says_why(review: Review, tmp_path: Path) -> None:
    (tmp_path / "out" / "broken.json").write_text('{"version": 9}', encoding="utf-8")

    broken = review.broken()

    assert broken["pulled"]["exists"] is True
    assert "not a valid broken.json" in broken["pulled"]["error"]
    assert broken["reports"] == []


def test_a_report_finds_its_track_by_catalog_id(review: Review, tmp_path: Path) -> None:
    pull(tmp_path, mark("rse-littleroot-town", vid(1)))

    broken = review.broken()

    assert broken["pulled"]["exists"] is True and broken["pulled"]["at"] is not None
    (report,) = broken["reports"]
    assert report["trackId"] == "rse-littleroot-town"
    assert (report["videoId"], report["reason"]) == (vid(1), "no-audio")
    assert report["detail"] == "no playable audio stream"
    assert report["markedAt"] == "2026-10-22T08:04:03.067000Z"
    assert report["olderCatalog"] is False
    assert report["track"] == {"id": "rse-littleroot-town", "family": "rse",
                               "title": "Littleroot Town"}  # fmt: skip
    assert report["family"] == "rse"
    assert report["status"] == "needs-fix"
    assert report["inManifest"] is True
    assert report["usedBy"] == [report["track"]]
    assert [s["videoId"] for s in report["sources"]] == [vid(1), vid(3)]


def test_a_report_finds_a_track_renamed_at_build_or_since(tmp_path: Path) -> None:
    # The draft keys tracks by source key; the phone knows them by their final title.
    ship = TRACKS[2].model_copy(update={"title": "Abandoned Ship (Inside)"})
    data, out = write_inputs(tmp_path, [TRACKS[0], TRACKS[1], ship])
    review = Review(data, out, CachedVideos({RS_LIST: PLAYLIST}), None)
    review.rename("rse-route-101", "Route 101 (Day)")
    pull(
        tmp_path,
        mark("rse-abandoned-ship-inside", vid(9)),
        mark("rse-route-101", vid(8)),  # the id the phone has: the rename isn't built yet
        mark("rse-route-101-day", vid(7)),
    )

    tracks = [r["track"]["id"] for r in review.broken()["reports"]]

    assert sorted(tracks) == ["rse-abandoned-ship", "rse-route-101", "rse-route-101"]


def test_an_unknown_track_id_falls_back_to_the_tracks_using_the_video(
    review: Review, tmp_path: Path
) -> None:
    pull(tmp_path, mark("rse-littleroot", vid(3)))

    (report,) = review.broken()["reports"]

    assert report["track"]["id"] == "rse-littleroot-town"
    assert report["status"] == "needs-fix"


def test_a_report_without_a_track_in_the_draft(review: Review, tmp_path: Path) -> None:
    pull(tmp_path, mark("oras-gone-track", "zzzzzzzzzzz"))

    (report,) = review.broken()["reports"]

    assert report["track"] is None
    assert report["family"] == "oras"
    assert report["status"] == "fixed"
    assert report["inManifest"] is False
    assert report["usedBy"] == [] and report["sources"] == []


def test_rejecting_the_video_fixes_it_in_the_draft_until_rebuild(
    review: Review, tmp_path: Path
) -> None:
    pull(tmp_path, mark("rse-littleroot-town", vid(1)))
    review.set_streams("rse-littleroot-town", {"reject": [vid(1)]})

    (report,) = review.broken()["reports"]

    assert report["status"] == "fixed"
    assert report["usedBy"] == []
    assert report["inManifest"] is True  # the manifest drops it at the next Rebuild
    assert [s["videoId"] for s in report["sources"]] == [vid(3)]


def test_reports_from_an_older_catalog_or_none_are_tagged(review: Review, tmp_path: Path) -> None:
    no_catalog = mark("rse-littleroot-town", vid(3), markedAt="2026-10-20T00:00:00Z")
    del no_catalog["catalogGeneratedAt"]
    pull(tmp_path, mark("rse-littleroot-town", vid(1), catalogGeneratedAt=OLDER), no_catalog)

    reports = review.broken()["reports"]

    assert [(r["videoId"], r["olderCatalog"]) for r in reports] == [(vid(1), True), (vid(3), True)]
    assert [r["catalogGeneratedAt"] for r in reports] == ["2026-10-21T03:12:40Z", None]


def test_reports_needing_a_fix_come_first_newest_first(review: Review, tmp_path: Path) -> None:
    pull(
        tmp_path,
        mark("rse-littleroot-town", vid(1), markedAt="2026-10-20T00:00:00Z"),
        mark("oras-gone-track", "zzzzzzzzzzz", markedAt="2026-10-23T00:00:00Z"),
        mark("rse-littleroot-town", vid(3), markedAt="2026-10-21T00:00:00Z"),
    )

    reports = review.broken()["reports"]

    assert [r["videoId"] for r in reports] == [vid(3), vid(1), "zzzzzzzzzzz"]


def test_reports_and_sources_show_cached_checks_only(
    review: Review, tmp_path: Path, probe: FakeProbe
) -> None:
    checks(tmp_path, probe).fresh(vid(1))
    probe.calls.clear()
    pull(tmp_path, mark("rse-littleroot-town", vid(1)))

    (report,) = review.broken()["reports"]

    assert report["check"]["verdict"] == "refused"
    assert report["check"]["detail"] == "This video is not available"
    assert [s["check"] and s["check"]["verdict"] for s in report["sources"]] == ["refused", None]
    assert probe.calls == []


def test_without_a_manifest_the_catalog_facts_are_unknown(
    tmp_path: Path, clock: Clock, live: LiveVideos
) -> None:
    review = make(tmp_path, clock, live)
    pull(tmp_path, mark("rse-littleroot-town", vid(1)))

    broken = review.broken()

    assert broken["manifestGeneratedAt"] is None
    assert broken["reports"][0]["olderCatalog"] is None
    assert broken["reports"][0]["inManifest"] is None
    assert broken["reports"][0]["check"] is None


def test_a_rebuild_rereads_the_manifest(review: Review, tmp_path: Path) -> None:
    write_manifest(tmp_path, "2026-10-23T00:00:00Z", [vid(3)])
    review._rebuild = lambda: (True, "built")

    review.rebuild()

    assert review.broken()["manifestGeneratedAt"] == "2026-10-23T00:00:00Z"


# Re-checks on request


def test_an_app_check_asks_youtube_and_caches_the_result(
    review: Review, probe: FakeProbe, tmp_path: Path
) -> None:
    result = review.app_check(f"https://youtu.be/{vid(1)}")

    assert result["check"]["videoId"] == vid(1)
    assert result["check"]["verdict"] == "refused"
    assert result["youtube"] == {"mode": "live"}
    assert probe.calls == [vid(1)]
    assert checks(tmp_path, probe).cached(vid(1)) is not None


def test_app_checks_are_spaced_like_other_live_calls(
    review: Review, probe: FakeProbe, clock: Clock
) -> None:
    probe.answers[vid(3)] = (FINE_INFO, [])
    review.app_check(vid(1))
    clock.now += 1

    review.app_check(vid(3))

    assert clock.sleeps == [LIVE_SPACING_SECONDS - 1]


def test_an_app_check_needs_a_video_id(review: Review) -> None:
    with pytest.raises(ReviewError, match="not a YouTube video id"):
        review.app_check("hello")


def test_cached_only_never_asks_youtube_for_an_app_check(
    tmp_path: Path, clock: Clock, live: LiveVideos, probe: FakeProbe
) -> None:
    review = make(tmp_path, clock, live, cached_only=True, app_checks=checks(tmp_path, probe))

    with pytest.raises(ReviewError, match="cached-only"):
        review.app_check(vid(1))
    assert probe.calls == []


def test_a_bot_check_starts_the_cooldown_and_keeps_the_last_check(
    review: Review, probe: FakeProbe, clock: Clock
) -> None:
    review.app_check(vid(1))
    probe.answers[vid(1)] = YouTubeBlocked("Sign in to confirm you're not a bot")
    clock.now += 60

    result = review.app_check(vid(1))

    assert result["check"]["verdict"] == "refused"  # the last one, kept
    assert "blocking" in result["notice"]
    assert result["youtube"]["mode"] == "blocked"
    assert review.app_check(vid(1))["check"]["verdict"] == "refused"
    assert probe.calls == [vid(1), vid(1)]  # not asked again while blocked
    clock.now += BLOCK_COOLDOWN_SECONDS
    probe.answers[vid(1)] = (FINE_INFO, [])
    assert review.app_check(vid(1))["check"]["verdict"] == "plays"  # asked again after it


def test_a_failed_app_check_is_an_error_and_not_cached(
    review: Review, probe: FakeProbe, tmp_path: Path
) -> None:
    probe.answers[vid(3)] = CheckFailed("Unable to download webpage")

    with pytest.raises(ReviewError, match="Couldn't check"):
        review.app_check(vid(3))
    assert checks(tmp_path, probe).cached(vid(3)) is None


# The track view


def test_a_reported_track_shows_its_reports_and_cached_checks(
    review: Review, probe: FakeProbe, tmp_path: Path
) -> None:
    review.app_check(vid(1))
    pull(tmp_path, mark("rse-littleroot-town", vid(1)), mark("rse-route-101", "zzzzzzzzzzz"))

    detail = review.track("rse-littleroot-town")

    (reported,) = detail["reported"]
    assert (reported["videoId"], reported["reason"]) == (vid(1), "no-audio")
    assert reported["check"]["verdict"] == "refused"
    assert detail["checks"][vid(1)]["verdict"] == "refused"
    assert vid(3) not in detail["checks"]  # unchecked videos have no entry
    assert review.track("rse-abandoned-ship")["reported"] == []


def test_a_video_reported_on_another_track_shows_on_every_track_using_it(
    review: Review, tmp_path: Path
) -> None:
    pull(tmp_path, mark("rse-old-title", vid(3)))

    assert [r["videoId"] for r in review.track("rse-littleroot-town")["reported"]] == [vid(3)]


def test_candidates_show_their_cached_checks(review: Review, probe: FakeProbe) -> None:
    probe.answers[vid(2)] = (FINE_INFO, [])
    review.app_check(vid(2))

    detail = review.track("rse-route-101")

    assert detail["candidates"][0]["videoId"] == vid(2)
    assert detail["checks"][vid(2)]["verdict"] == "plays"
