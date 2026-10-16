"""The whole step 17 path offline: the trimmed Bulbapedia pages and committed YouTube responses
(tests/data/youtube/, trimmed from a real run) to a manifest and a match report."""

from pathlib import Path
from typing import Any

import pytest
from test_build_from_fixtures import GAMES, wikitext

from vgmixr_pipeline.catalog.build import build_draft
from vgmixr_pipeline.catalog.config import Overrides, YouTube
from vgmixr_pipeline.catalog.manifest_out import write_manifest
from vgmixr_pipeline.catalog.match import Match, match_tracks
from vgmixr_pipeline.catalog.report import match_report
from vgmixr_pipeline.catalog.score import Names
from vgmixr_pipeline.catalog.youtube import YouTube as YouTubeClient
from vgmixr_pipeline.manifest.models import LocalSource, StreamSource
from vgmixr_pipeline.manifest.parse import Valid, parse

RESPONSES = Path(__file__).parent / "data" / "youtube"
RSE_PLAYLIST = "PL2uxd6YWj7PIOlswxbt16G63Klr_3lsbR"
LOCAL = LocalSource(type="local", path="rse/route-101.m4a")
OVERRIDES = Overrides.model_validate(
    {
        "tracks": {
            "rse-route-101": {"local": [LOCAL.model_dump(exclude_none=True)]},
            "rse-opening-movie-double-battles": {
                "streams": {"accept": [{"videoId": "ID2Iv5HP-0I"}], "reject": ["cnzggrebNS0"]}
            },
        }
    }
)


def offline(url: str, flat: bool) -> dict[str, Any]:
    raise AssertionError(f"not in tests/data/youtube: {url}")


@pytest.fixture(scope="module")
def run() -> tuple[Any, list[Match]]:
    draft = build_draft(GAMES, OVERRIDES, wikitext, "2026-10-16T12:00:00Z")[0]
    youtube = YouTube.model_validate(
        {"playlists": [{"games": ["ruby", "sapphire", "emerald"], "playlist": RSE_PLAYLIST}]}
    )
    client = YouTubeClient(RESPONSES, extract=offline)
    names = Names.build(GAMES.games, {})
    return draft, match_tracks(draft, youtube, OVERRIDES, client, names)


def test_playlist_entries_match_and_the_rest_need_review(run: tuple[Any, list[Match]]) -> None:
    _, matches = run
    status = {m.track_id: m.status for m in matches}
    assert status == {
        "rse-opening-movie-setting-out-on-a-journey-in-the-hoenn-region": "review",
        "rse-opening-movie-double-battles": "matched",
        "rse-littleroot-town": "matched",
        "rse-route-101": "matched",
        "rse-surf": "matched",
        "rse-route-119": "matched",
        "rse-fortree-city": "matched",
        "rse-battle-mew": "matched",
    }


def test_writes_a_valid_manifest(run: tuple[Any, list[Match]], tmp_path: Path) -> None:
    draft, matches = run
    path = tmp_path / "manifest.json"
    assert write_manifest(path, draft, matches, "2026-10-16T12:00:00Z") == []
    result = parse(path.read_text(encoding="utf-8"))
    assert isinstance(result, Valid), result
    tracks = {t.id: t for t in result.manifest.tracks}
    assert "rse-opening-movie-setting-out-on-a-journey-in-the-hoenn-region" not in tracks
    assert tracks["rse-route-101"].sources[0] == LOCAL
    stream = tracks["rse-route-101"].sources[1]
    assert isinstance(stream, StreamSource) and stream.videoId == "WMj9b4vYTXU"
    accepted = tracks["rse-opening-movie-double-battles"].sources[0]
    assert isinstance(accepted, StreamSource)
    assert (accepted.videoId, accepted.confidence) == ("ID2Iv5HP-0I", 1.0)
    battle_mew = tracks["rse-battle-mew"]
    assert battle_mew.games == ["emerald"]
    assert [s.videoId for s in battle_mew.sources if isinstance(s, StreamSource)] == ["vGVcmq-JUs0"]


def test_rejected_and_red_flagged_videos_are_never_sources(run: tuple[Any, list[Match]]) -> None:
    _, matches = run
    used = {s.videoId for m in matches for s in m.streams}
    assert "cnzggrebNS0" not in used  # rejected by override
    assert "h5Dvlly6aKg" not in used  # a remix of the complete soundtrack


def test_the_report_lists_the_track_needing_review(run: tuple[Any, list[Match]]) -> None:
    _, matches = run
    report = match_report(GAMES.games, matches)
    assert "- Matched: 7, needs review: 1, unmatched: 0" in report
    assert "- Needs review: Opening Movie: Setting out on a Journey in the Hoenn Region" in report
