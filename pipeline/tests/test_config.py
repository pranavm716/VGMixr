"""The committed games and overrides files (pipeline/data/)."""

from pathlib import Path
from typing import Any

import pytest
from pydantic import ValidationError

from vgmixr_pipeline.catalog.config import (
    DATA_DIR,
    Durations,
    GamesFile,
    Overrides,
    Tracklist,
    load_durations,
    load_games,
    load_overrides,
)
from vgmixr_pipeline.catalog.tracklist import parse_tsv_disc
from vgmixr_pipeline.manifest.models import LocalSource, Manifest
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


def test_one_track_has_a_local_file_for_playback_development(overrides: Overrides) -> None:
    with_local = {id_: o.local for id_, o in overrides.tracks.items() if o.local}
    assert with_local == {"rse-route-101": [LocalSource(type="local", path="rse/route-101.m4a")]}


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


def test_durations_are_for_listed_tracklists(games: GamesFile) -> None:
    listed = {(t.page, t.disc) for t in games.tracklists}
    for page, durations in load_durations().pages.items():
        assert {(page, disc) for disc in durations.discs} & listed, page


def test_durations_must_be_minutes_and_seconds() -> None:
    page = {"source": "https://vgmdb.net/album/1", "discs": {"Disc 1": ["1:2"]}}
    with pytest.raises(ValidationError):
        Durations.model_validate({"pages": {"P": page}})


@pytest.mark.parametrize("source", [{}, {"page": "P", "file": "tracklists/x.tsv"}])
def test_tracklist_has_a_page_or_a_file(source: dict[str, str]) -> None:
    with pytest.raises(ValidationError):
        Tracklist.model_validate({"family": "f", "disc": "Disc 1", **source})


def test_file_tracklists_exist_and_have_their_disc(games: GamesFile) -> None:
    for tracklist in games.tracklists:
        if tracklist.file is not None:
            text = (DATA_DIR / tracklist.file).read_text(encoding="utf-8")
            assert parse_tsv_disc(text, tracklist.disc), tracklist


def test_extra_duration_must_be_minutes_and_seconds() -> None:
    with pytest.raises(ValidationError):
        Overrides.model_validate({"extras": [{"family": "f", "title": "T", "duration": "1:2"}]})


GAME = {"name": "N", "shortName": "N", "color": "#000000", "generation": 3, "platform": "GBA"}


def games_file(youtube: dict[str, Any]) -> GamesFile:
    return GamesFile.model_validate(
        {
            "games": [
                {**GAME, "id": "ruby", "family": "rse"},
                {**GAME, "id": "sapphire", "family": "rse"},
            ],
            "tracklists": [],
            "youtube": youtube,
        }
    )


def test_youtube_block_is_optional() -> None:
    games = GamesFile.model_validate({"games": [], "tracklists": []})
    assert games.youtube.playlists == [] and games.youtube.albums == []


def test_youtube_playlists_albums_and_aliases_parse() -> None:
    games = games_file(
        {
            "playlists": [
                {"games": ["ruby"], "playlist": "OLAK5uy_abcdefghij", "first": 2, "last": 9},
                {"games": ["ruby", "sapphire"], "playlist": "PLabcdefghij", "note": "rip"},
            ],
            "albums": [{"games": ["sapphire"], "video": "abcdefghijk"}],
            "aliases": {"rse": ["Hoenn"], "ruby": ["Rubí"]},
        }
    )
    first, second = games.youtube.playlists
    assert (first.games, first.playlist, first.first, first.last) == (
        ["ruby"],
        "OLAK5uy_abcdefghij",
        2,
        9,
    )
    assert second.first is None and second.note == "rip"
    assert games.youtube.albums[0].video == "abcdefghijk"
    assert games.youtube.aliases == {"rse": ["Hoenn"], "ruby": ["Rubí"]}


@pytest.mark.parametrize(
    "youtube",
    [
        {"playlists": [{"games": ["emerald"], "playlist": "PLabcdefghij"}]},
        {"playlists": [{"games": [], "playlist": "PLabcdefghij"}]},
        {"playlists": [{"games": ["ruby"], "playlist": "PL abc"}]},
        {"playlists": [{"games": ["ruby"], "playlist": "PLabcdefghij", "first": 5, "last": 4}]},
        {"playlists": [{"games": ["ruby"], "playlist": "PLabcdefghij", "first": 0}]},
        {"albums": [{"games": ["ruby"], "video": "short"}]},
        {"albums": [{"games": ["emerald"], "video": "abcdefghijk"}]},
        {"aliases": {"hoenn": ["Hoenn"]}},
    ],
    ids=[
        "unknown-game",
        "no-games",
        "bad-playlist-id",
        "first-after-last",
        "first-below-1",
        "bad-video-id",
        "unknown-album-game",
        "unknown-alias-key",
    ],
)
def test_youtube_block_errors(youtube: dict[str, Any]) -> None:
    with pytest.raises(ValidationError):
        games_file(youtube)


def test_streams_override_parses() -> None:
    overrides = Overrides.model_validate(
        {
            "tracks": {
                "rse-route-101": {
                    "streams": {
                        "accept": [
                            {"videoId": "abcdefghijk"},
                            {"videoId": "bcdefghijkl", "startMs": 1000, "endMs": 5000},
                        ],
                        "reject": ["cdefghijklm"],
                    }
                }
            }
        }
    )
    streams = overrides.tracks["rse-route-101"].streams
    assert streams is not None
    assert [(s.videoId, s.startMs, s.endMs) for s in streams.accept] == [
        ("abcdefghijk", 0, None),
        ("bcdefghijkl", 1000, 5000),
    ]
    assert streams.reject == ["cdefghijklm"]


@pytest.mark.parametrize(
    "streams",
    [
        {"accept": [{"videoId": "short"}]},
        {"accept": [{"videoId": "abcdefghijk", "startMs": -1}]},
        {"accept": [{"videoId": "abcdefghijk", "startMs": 5, "endMs": 5}]},
        {"accept": [{"videoId": "abcdefghijk"}, {"videoId": "abcdefghijk"}]},
        {"reject": ["short"]},
        {"accept": [{"videoId": "abcdefghijk"}], "reject": ["abcdefghijk"]},
    ],
    ids=["bad-id", "negative-start", "end-not-after-start", "duplicate", "bad-reject", "both"],
)
def test_streams_override_errors(streams: dict[str, Any]) -> None:
    with pytest.raises(ValidationError):
        Overrides.model_validate({"tracks": {"rse-x": {"streams": streams}}})


def test_local_override_takes_only_local_sources() -> None:
    local = {"type": "local", "path": "rse/route-101.m4a"}
    parsed = Overrides.model_validate({"tracks": {"rse-x": {"local": [local]}}})
    assert parsed.tracks["rse-x"].local[0].path == "rse/route-101.m4a"
    stream = {"type": "stream", "videoId": "abcdefghijk"}
    with pytest.raises(ValidationError):
        Overrides.model_validate({"tracks": {"rse-x": {"local": [stream]}}})


def test_part_duration_must_be_minutes_and_seconds() -> None:
    parts = [{"title": "A", "duration": "0:4"}, {"title": "B"}]
    with pytest.raises(ValidationError):
        Overrides.model_validate({"tracks": {"x": {"parts": parts}}})
