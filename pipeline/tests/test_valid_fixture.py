from datetime import UTC, datetime

import pytest
from fixture_files import FIXTURES_DIR

from vgmixr_pipeline.manifest.models import LocalSource, Loop, Manifest, StreamSource, Track
from vgmixr_pipeline.manifest.parse import Valid, parse


@pytest.fixture(scope="module")
def manifest() -> Manifest:
    result = parse((FIXTURES_DIR / "valid.json").read_text(encoding="utf-8"))
    assert isinstance(result, Valid), result
    return result.manifest


def track(manifest: Manifest, track_id: str) -> Track:
    return next(t for t in manifest.tracks if t.id == track_id)


def test_parses_top_level_fields(manifest: Manifest) -> None:
    assert manifest.schemaVersion == 2
    assert manifest.generated_at() == datetime(2026, 10, 14, 8, 0, tzinfo=UTC)
    game_ids = [g.id for g in manifest.games]
    assert game_ids == ["ruby", "sapphire", "emerald", "pla", "botw", "red", "blue"]
    assert len(manifest.tracks) == 6


def test_parses_games_with_and_without_optional_fields(manifest: Manifest) -> None:
    pla, botw = manifest.games[3], manifest.games[4]
    assert (pla.generation, pla.family) == (8, None)
    assert (botw.generation, botw.shortName) == (None, "BreathOfWild")
    assert manifest.games[1].color == "#2d5bc8"


def test_parses_local_sources_with_and_without_loop(manifest: Manifest) -> None:
    assert track(manifest, "rse-route-101").sources[0] == LocalSource(
        type="local",
        path="rse/012.flac",
        loop=Loop(sampleRate=32000, introEnd=96000, loopEnd=1824000),
    )
    plain, looped = track(manifest, "botw-hyrule-field").sources
    assert plain == LocalSource(type="local", path="botw/field/hyrule-field.ogg")
    assert isinstance(looped, LocalSource)
    assert looped.loop == Loop(sampleRate=48000, introEnd=0, loopEnd=1)


def test_stream_defaults_apply_when_omitted(manifest: Manifest) -> None:
    (source,) = track(manifest, "pla-example-theme").sources
    assert isinstance(source, StreamSource)
    assert (source.startMs, source.endMs, source.confidence, source.loop) == (0, None, None, None)


def test_parses_stream_slices_and_confidence(manifest: Manifest) -> None:
    first, second = track(manifest, "rse-weather-institute").sources
    assert first == StreamSource(
        type="stream", videoId="Ab-cd_EF012", startMs=120000, endMs=245500, confidence=0.5
    )
    assert second == StreamSource(type="stream", videoId="Ab-cd_EF012", startMs=245500)
    route_stream = track(manifest, "rse-route-101").sources[1]
    assert isinstance(route_stream, StreamSource)
    assert route_stream.confidence == 1.0
    end_set = track(manifest, "rb-pallet-town").sources[1]
    assert isinstance(end_set, StreamSource)
    assert (end_set.startMs, end_set.endMs) == (0, 1)


def test_parses_track_fields(manifest: Manifest) -> None:
    institute = track(manifest, "rse-weather-institute")
    assert institute.games == ["ruby", "emerald"]
    assert institute.replaces == ["rse-weather-lab"]
    route = track(manifest, "rse-route-101")
    assert (route.games, route.replaces, route.order) == (None, [], 12)
