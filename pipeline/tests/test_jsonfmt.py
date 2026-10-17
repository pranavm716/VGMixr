"""The canonical format of the committed data files, which the review tool writes."""

import json
from typing import Any

import pytest

from vgmixr_pipeline.catalog.config import DATA_DIR
from vgmixr_pipeline.catalog.jsonfmt import (
    format_games,
    format_overrides,
    games_families,
    track_family,
)

FAMILIES = ["rby", "gsc", "rse", "sm"]


def test_committed_games_file_is_canonical() -> None:
    text = (DATA_DIR / "games.json").read_text(encoding="utf-8")
    assert format_games(json.loads(text)) == text


def test_committed_overrides_file_is_canonical() -> None:
    games = json.loads((DATA_DIR / "games.json").read_text(encoding="utf-8"))
    text = (DATA_DIR / "overrides.json").read_text(encoding="utf-8")
    assert format_overrides(json.loads(text), games_families(games)) == text


def test_families_come_in_game_order_and_a_game_without_family_is_its_own() -> None:
    games = {
        "games": [
            {"id": "red", "family": "rby"},
            {"id": "blue", "family": "rby"},
            {"id": "pla"},
            {"id": "x", "family": "xy"},
        ]
    }
    assert games_families(games) == ["rby", "pla", "xy"]


@pytest.mark.parametrize(
    ("key", "family"),
    [("rse-route-101", "rse"), ("rs-intro", "rs"), ("sm-escape-usum", "sm"), ("pla-intro", None)],
)
def test_a_track_key_belongs_to_the_longest_family_prefix(key: str, family: str | None) -> None:
    assert track_family(key, ["rs", "rse", "sm"]) == family


def test_games_file_entries_are_one_per_line_and_families_are_separated() -> None:
    data: dict[str, Any] = {
        "games": [{"id": "red", "name": "Red", "generation": 1}, {"id": "gold", "name": "Gold"}],
        "tracklists": [
            {"family": "rby", "page": "Pokémon Red", "disc": "Disc 1", "last": 45},
            {"family": "rby", "games": ["yellow"], "page": "P", "disc": "Disc 3"},
            {"family": "gsc", "file": "tracklists/gsc.tsv", "disc": "Disc 1"},
        ],
        "youtube": {
            "playlists": [
                {"games": ["red", "blue"], "playlist": "PL1234567890", "note": "x" * 120}
            ],
            "albums": [],
            "aliases": {"oras": ["ORAS", "Omega Ruby Alpha Sapphire"]},
        },
    }
    assert format_games(data) == (
        "{\n"
        '  "games": [\n'
        '    { "id": "red", "name": "Red", "generation": 1 },\n'
        '    { "id": "gold", "name": "Gold" }\n'
        "  ],\n"
        '  "tracklists": [\n'
        '    { "family": "rby", "page": "Pokémon Red", "disc": "Disc 1", "last": 45 },\n'
        '    { "family": "rby", "games": ["yellow"], "page": "P", "disc": "Disc 3" },\n'
        "\n"
        '    { "family": "gsc", "file": "tracklists/gsc.tsv", "disc": "Disc 1" }\n'
        "  ],\n"
        '  "youtube": {\n'
        '    "playlists": [\n'
        f'      {{ "games": ["red", "blue"], "playlist": "PL1234567890", "note": "{"x" * 120}" }}\n'
        "    ],\n"
        '    "albums": [],\n'
        '    "aliases": {\n'
        '      "oras": ["ORAS", "Omega Ruby Alpha Sapphire"]\n'
        "    }\n"
        "  }\n"
        "}\n"
    )


def test_overrides_keep_nested_values_on_the_entry_line() -> None:
    data = {
        "tracks": {
            "rby-title-screen": {
                "split": {"rby-title-screen-yellow": {"games": ["yellow"], "title": "Title"}}
            },
            "rse-route-101": {"local": [{"type": "local", "path": "rse/route-101.m4a"}]},
            "rse-drought": {
                "streams": {"accept": [{"videoId": "ID2Iv5HP-0I", "startMs": 5}], "reject": []}
            },
            "sm-escape": {"exclude": False, "note": 'Matches the "You" rule'},
        },
        "extras": [{"family": "sm", "title": "Beach Spot", "duration": "2:01"}],
    }
    assert format_overrides(data, FAMILIES) == (
        "{\n"
        '  "tracks": {\n'
        '    "rby-title-screen": { "split": { "rby-title-screen-yellow": '
        '{ "games": ["yellow"], "title": "Title" } } },\n'
        "\n"
        '    "rse-route-101": { "local": [ { "type": "local", "path": "rse/route-101.m4a" } ] },\n'
        '    "rse-drought": { "streams": { "accept": '
        '[ { "videoId": "ID2Iv5HP-0I", "startMs": 5 } ], "reject": [] } },\n'
        "\n"
        '    "sm-escape": { "exclude": false, "note": "Matches the \\"You\\" rule" }\n'
        "  },\n"
        '  "extras": [\n'
        '    { "family": "sm", "title": "Beach Spot", "duration": "2:01" }\n'
        "  ]\n"
        "}\n"
    )


def test_a_long_override_moves_its_note_under_the_first_key() -> None:
    note = "Both halves of the opening, played back to back"
    data = {
        "tracks": {"gsc-opening": {"games": ["gold", "silver"], "duration": "0:45", "note": note}}
    }
    first = '    "gsc-opening": { '
    assert format_overrides(data, FAMILIES, width=60) == (
        "{\n"
        '  "tracks": {\n'
        f'{first}"games": ["gold", "silver"], "duration": "0:45",\n'
        f'{" " * len(first)}"note": "{note}" }}\n'
        "  }\n"
        "}\n"
    )


def test_a_long_override_without_a_note_stays_on_one_line() -> None:
    data = {"tracks": {"gsc-opening": {"games": ["gold", "silver"], "duration": "0:45"}}}
    lines = format_overrides(data, FAMILIES, width=20).splitlines()
    assert '    "gsc-opening": { "games": ["gold", "silver"], "duration": "0:45" }' in lines


def test_empty_collections_are_compact() -> None:
    assert format_overrides({"tracks": {}, "extras": []}, FAMILIES) == (
        '{\n  "tracks": {},\n  "extras": []\n}\n'
    )
