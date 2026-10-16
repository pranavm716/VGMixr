"""The whole build, from the trimmed Bulbapedia pages to a draft that becomes a valid manifest."""

import json
from pathlib import Path

import pytest

from vgmixr_pipeline.catalog.build import BuildError, Draft, build_draft
from vgmixr_pipeline.catalog.config import GamesFile, Overrides
from vgmixr_pipeline.manifest.parse import Valid, parse

PAGES = Path(__file__).parent / "data" / "bulbapedia"
RS = "Pokémon Ruby & Pokémon Sapphire: Super Music Collection"
B2W2 = "Pokémon Black 2 & Pokémon White 2: Super Music Collection"
FILES = {RS: "rs", B2W2: "b2w2"}

GAMES = GamesFile.model_validate(
    {
        "games": [
            {"id": "ruby", "name": "Ruby", "shortName": "R", "color": "#CD2236",
             "generation": 3, "platform": "GBA", "family": "rse"},
            {"id": "sapphire", "name": "Sapphire", "shortName": "S", "color": "#3D51A7",
             "generation": 3, "platform": "GBA", "family": "rse"},
            {"id": "emerald", "name": "Emerald", "shortName": "E", "color": "#009652",
             "generation": 3, "platform": "GBA", "family": "rse"},
        ],
        "tracklists": [
            {"family": "rse", "page": RS, "disc": "Disc 1"},
            {"family": "rse", "page": RS, "disc": "Disc 2"},
            {"family": "rse", "games": ["emerald"], "page": B2W2, "disc": "Disc 4", "last": 17},
        ],
    }
)  # fmt: skip


def wikitext(page: str) -> str:
    return (PAGES / f"{FILES[page]}.wikitext").read_text(encoding="utf-8")


def draft(overrides: Overrides | None = None) -> Draft:
    return build_draft(GAMES, overrides or Overrides(), wikitext, "2026-10-15T12:00:00Z")[0]


def test_builds_shared_and_emerald_only_tracks() -> None:
    tracks = {t.id: t for t in draft().tracks}
    assert list(tracks) == [
        "rse-opening-movie-setting-out-on-a-journey-in-the-hoenn-region",
        "rse-opening-movie-double-battles",
        "rse-littleroot-town",
        "rse-route-101",
        "rse-surf",
        "rse-route-119",
        "rse-fortree-city",
        "rse-battle-mew",
    ]
    assert tracks["rse-route-101"].games is None
    assert tracks["rse-battle-mew"].games == ["emerald"]
    assert tracks["rse-battle-mew"].origins == [f"{B2W2} / Disc 4 #17"]
    assert tracks["rse-surf"].durationMs == 133_000


def test_reports_jingles_left_out() -> None:
    result = build_draft(GAMES, Overrides(), wikitext, "2026-10-15T12:00:00Z")[1]
    assert [e.title for e in result.excluded] == [
        "Pokémon Healed",
        "Obtained a TM!",
        "Battle Symbol Received!",
    ]


def test_draft_with_sources_is_a_valid_manifest() -> None:
    data = json.loads(draft().model_dump_json())
    for track in data["tracks"]:
        track["sources"] = [{"type": "stream", "videoId": "XXXXXXXXXXX"}]
        del track["durationMs"], track["origins"]
    result = parse(json.dumps({"schemaVersion": 1, **data}))
    assert isinstance(result, Valid), result


def test_missing_disc_names_the_tracklist() -> None:
    games = GAMES.model_copy(
        update={"tracklists": [GAMES.tracklists[0].model_copy(update={"disc": "Disc 7"})]}
    )
    with pytest.raises(BuildError, match="Disc 7"):
        build_draft(games, Overrides(), wikitext, "2026-10-15T12:00:00Z")
