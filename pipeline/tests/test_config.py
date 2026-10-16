"""The committed games and overrides files (pipeline/data/)."""

from pathlib import Path

import pytest
from pydantic import ValidationError

from vgmixr_pipeline.catalog.config import GamesFile, Overrides, load_games, load_overrides
from vgmixr_pipeline.manifest.models import Manifest
from vgmixr_pipeline.manifest.parse import Valid, parse
from vgmixr_pipeline.manifest.validate import validate

SEED_MANIFEST = Path(__file__).resolve().parents[2] / "catalog" / "manifest.json"


@pytest.fixture(scope="module")
def games() -> GamesFile:
    return load_games()


@pytest.fixture(scope="module")
def overrides() -> Overrides:
    return load_overrides()


@pytest.fixture(scope="module")
def seed() -> Manifest:
    result = parse(SEED_MANIFEST.read_text(encoding="utf-8"))
    assert isinstance(result, Valid), result
    return result.manifest


def family_games(games: GamesFile) -> dict[str, list[str]]:
    families: dict[str, list[str]] = {}
    for game in games.games:
        families.setdefault(game.family_id, []).append(game.id)
    return families


def test_games_pass_the_manifest_rules(games: GamesFile) -> None:
    manifest = Manifest(
        schemaVersion=1, generatedAt="2026-01-01T00:00:00Z", games=games.games, tracks=[]
    )
    assert validate(manifest) == []


def test_every_family_has_a_tracklist(games: GamesFile) -> None:
    assert set(family_games(games)) == {t.family for t in games.tracklists}


def test_tracklist_games_belong_to_their_family(games: GamesFile) -> None:
    families = family_games(games)
    for tracklist in games.tracklists:
        assert set(tracklist.games or []) <= set(families[tracklist.family]), tracklist


def test_seed_games_are_unchanged(games: GamesFile, seed: Manifest) -> None:
    by_id = {g.id: g for g in games.games}
    for game in seed.games:
        built = by_id[game.id]
        assert built.color.upper() == game.color.upper()
        assert built.model_dump(exclude={"color"}) == game.model_dump(exclude={"color"})


def test_test_tone_extra_matches_the_seed(overrides: Overrides, seed: Manifest) -> None:
    seed_track = next(t for t in seed.tracks if t.id == "pla-test-tone")
    extra = next(e for e in overrides.extras if e.id == "pla-test-tone")
    assert (extra.family, extra.title, extra.category, extra.order) == (
        seed_track.family,
        seed_track.title,
        seed_track.category,
        seed_track.order,
    )
    assert extra.sources == seed_track.sources


def test_usum_extras_are_ultra_only(overrides: Overrides) -> None:
    usum = [e for e in overrides.extras if e.family == "sm"]
    assert len(usum) == 52
    for extra in usum:
        assert extra.games and set(extra.games) <= {"ultra-sun", "ultra-moon"}, extra


def test_override_fields_are_strict() -> None:
    with pytest.raises(ValidationError):
        Overrides.model_validate_json('{"tracks": {"rse-x": {"categry": "town"}}}')
    with pytest.raises(ValidationError):
        Overrides.model_validate_json('{"tracks": {"rse-x": {"category": "boss"}}}')
