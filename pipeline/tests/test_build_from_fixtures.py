"""The whole build, from the trimmed Bulbapedia pages to a draft that becomes a valid manifest."""

import json
from pathlib import Path

import pytest

from vgmixr_pipeline.catalog.build import BuildError, Draft, build_draft
from vgmixr_pipeline.catalog.config import Durations, GamesFile, Overrides, Tracklist
from vgmixr_pipeline.manifest.parse import Valid, parse

PAGES = Path(__file__).parent / "data" / "bulbapedia"
RS = "Pokémon Ruby & Pokémon Sapphire: Super Music Collection"
B2W2 = "Pokémon Black 2 & Pokémon White 2: Super Music Collection"
ZA = "Pokémon Legends: Z-A + Mega Dimension Super Music Collection"
FILES = {RS: "rs", B2W2: "b2w2", ZA: "za"}

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
        del track["durationMs"], track["origins"], track["listedGames"]
    result = parse(json.dumps({"schemaVersion": 1, **data}))
    assert isinstance(result, Valid), result


def test_title_suffix_keeps_another_version_apart() -> None:
    """The same disc listed twice, once as a variant (like HGSS's GB Sounds versions)."""
    variant = GAMES.tracklists[1].model_copy(update={"titleSuffix": " (GB Sounds)"})
    games = GAMES.model_copy(update={"tracklists": [GAMES.tracklists[1], variant]})
    tracks = {
        t.id: t for t in build_draft(games, Overrides(), wikitext, "2026-10-15T12:00:00Z")[0].tracks
    }
    assert list(tracks) == [
        "rse-route-119",
        "rse-fortree-city",
        "rse-route-119-gb-sounds",
        "rse-fortree-city-gb-sounds",
    ]
    assert tracks["rse-route-119-gb-sounds"].title == "Route 119 (GB Sounds)"


def test_missing_disc_names_the_tracklist() -> None:
    games = GAMES.model_copy(
        update={"tracklists": [GAMES.tracklists[0].model_copy(update={"disc": "Disc 7"})]}
    )
    with pytest.raises(BuildError, match="Disc 7"):
        build_draft(games, Overrides(), wikitext, "2026-10-15T12:00:00Z")


ZA_GAMES = GamesFile.model_validate(
    {
        "games": [
            {"id": "plza", "name": "Legends: Z-A", "shortName": "Z-A", "color": "#31CA56",
             "generation": 9, "platform": "Switch"},
        ],
        "tracklists": [{"family": "plza", "page": ZA, "disc": "Disc 2"}],
    }
)  # fmt: skip


def durations(page: str, disc: str, times: list[str | None]) -> Durations:
    return Durations.model_validate(
        {"pages": {page: {"source": "https://vgmdb.net/album/1", "discs": {disc: times}}}}
    )


def test_fills_missing_durations_by_track_number() -> None:
    """The Z-A fixture has rows 3-6 and no durations; the list is in track order from #1."""
    filled = durations(ZA, "Disc 2", ["6:56", "1:52", "1:23", "4:32", "0:10", None])
    draft, result = build_draft(ZA_GAMES, Overrides(), wikitext, "2026-10-15T12:00:00Z", filled)
    tracks = {t.id: t for t in draft.tracks}
    assert tracks["plza-warning"].durationMs == 83_000
    assert tracks["plza-battle-rogue-mega-evolved-pokemon"].durationMs == 272_000
    # Mega Stone is now known to be a 10 s jingle; #6 has no duration and a jingle title.
    assert [(e.title, e.duration_ms) for e in result.excluded] == [
        ("Mega Stone", 10_000),
        ("Obtained a Mega Stone!", None),
    ]


def test_bulbapedia_durations_win() -> None:
    times: list[str | None] = ["9:59"] * 11
    filled = durations(RS, "Disc 1", times)
    draft = build_draft(GAMES, Overrides(), wikitext, "2026-10-15T12:00:00Z", filled)[0]
    route = next(t for t in draft.tracks if t.id == "rse-route-101")
    assert route.durationMs == 60_000


def test_reads_tracklists_from_files() -> None:
    games = GAMES.model_copy(
        update={
            "tracklists": [
                Tracklist(family="rse", file="tracklists/rse.tsv", disc="Disc 1", last=2)
            ]
        }
    )
    files = {"tracklists/rse.tsv": "Disc 1\t1\tRoute 101\t1:00\nDisc 1\t2\tDrought\t0:50\n"
             "Disc 1\t3\tLater\t1:00\n"}  # fmt: skip
    draft = build_draft(
        games, Overrides(), wikitext, "2026-10-15T12:00:00Z", read_file=files.__getitem__
    )[0]
    assert [(t.id, t.durationMs, t.origins) for t in draft.tracks] == [
        ("rse-route-101", 60_000, ["tracklists/rse.tsv / Disc 1 #1"]),
        ("rse-drought", 50_000, ["tracklists/rse.tsv / Disc 1 #2"]),
    ]
