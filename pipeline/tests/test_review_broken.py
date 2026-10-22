"""The review tool's Broken tab: the app's reports joined to the draft (docs/broken.md)."""

import json
from pathlib import Path
from typing import Any

import pytest
from test_match import RS_LIST, vid
from test_recheck import REFUSED_INFO, FakeProbe, checks
from test_review_service import (
    PLAYLIST,
    TRACKS,
    CachedVideos,
    Clock,
    LiveVideos,
    make,
    write_inputs,
)

from vgmixr_pipeline.catalog.review.service import Review

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
