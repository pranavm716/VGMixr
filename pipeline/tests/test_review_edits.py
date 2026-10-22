"""The review tool's edits of the data files, on small fake files written to tmp_path.

The fakes mimic the committed files' layout (families in games-file order, override groups per
family, a note last), so the real data can change without touching these tests. That the
committed files stay in the canonical format is test_jsonfmt's job.
"""

import difflib
import json
from pathlib import Path
from typing import Any

import pytest

from vgmixr_pipeline.catalog.config import load_games, load_order, load_overrides
from vgmixr_pipeline.catalog.jsonfmt import (
    format_games,
    format_order,
    format_overrides,
    games_families,
)
from vgmixr_pipeline.catalog.review.edits import (
    EditError,
    add_playlist,
    remove_playlist,
    rename,
    set_exclude,
    set_games,
    set_note,
    set_order,
    set_streams,
)


def game(id_: str, name: str, family: str, generation: int, platform: str) -> dict[str, Any]:
    return {"id": id_, "name": name, "shortName": name[:2], "color": "#808080",
            "generation": generation, "platform": platform, "family": family}  # fmt: skip


RBY_LIST = "PL2uxd6YWj7PLbKlHSlTO7Lv4E6csDZhRc"
GSC_LIST = "PL2uxd6YWj7PKk4LnkWZEyqpcvnXmv8Iuf"
RSE_LIST = "PL2uxd6YWj7PIOlswxbt16G63Klr_3lsbR"
FRLG_LIST = "PL2uxd6YWj7PIOaewo5Z39hSJMdUmo3taO"
DROUGHT_NOTE = "Groudon's event; Emerald uses Abnormal Weather"
GAMES: dict[str, Any] = {
    "games": [
        game("red", "Red", "rby", 1, "GB"),
        game("blue", "Blue", "rby", 1, "GB"),
        game("yellow", "Yellow", "rby", 1, "GB"),
        game("gold", "Gold", "gsc", 2, "GBC"),
        game("silver", "Silver", "gsc", 2, "GBC"),
        game("crystal", "Crystal", "gsc", 2, "GBC"),
        game("ruby", "Ruby", "rse", 3, "GBA"),
        game("sapphire", "Sapphire", "rse", 3, "GBA"),
        game("emerald", "Emerald", "rse", 3, "GBA"),
        game("firered", "FireRed", "frlg", 3, "GBA"),
        game("leafgreen", "LeafGreen", "frlg", 3, "GBA"),
        game("diamond", "Diamond", "dppt", 4, "DS"),
        game("pearl", "Pearl", "dppt", 4, "DS"),
        game("platinum", "Platinum", "dppt", 4, "DS"),
        game("heartgold", "HeartGold", "hgss", 4, "DS"),
        game("soulsilver", "SoulSilver", "hgss", 4, "DS"),
        game("scarlet", "Scarlet", "sv", 9, "Switch"),
        game("violet", "Violet", "sv", 9, "Switch"),
    ],
    "tracklists": [],
    "youtube": {
        "playlists": [
            {"games": ["red", "blue", "yellow"], "playlist": RBY_LIST,
             "note": "Daintii Music, game rip"},
            {"games": ["gold", "silver", "crystal"], "playlist": GSC_LIST},
            {"games": ["ruby", "sapphire", "emerald"], "playlist": RSE_LIST},
            {"games": ["firered", "leafgreen"], "playlist": FRLG_LIST},
            {"games": ["heartgold", "soulsilver"], "playlist": "PL7657467B11931136"},
        ],
        "albums": [],
        "aliases": {},
    },
}  # fmt: skip
FAMILIES = games_families(GAMES)
OVERRIDES: dict[str, Any] = {
    "tracks": {
        "rby-pokemon-tower": {"note": "Lavender Town's tower"},
        "gsc-clair": {"games": ["crystal"]},
        "rse-drought": {"games": ["ruby"], "note": DROUGHT_NOTE},
        "rse-cable-car": {"exclude": False, "note": "Short, but music, not a jingle"},
        "frlg-sevii-islands": {"games": ["firered", "leafgreen"]},
        "hgss-kanto-route": {"note": "Plays on most Kanto routes"},
    },
    "extras": [],
}  # fmt: skip
GAMES_TEXT = format_games(GAMES)
OVERRIDES_TEXT = format_overrides(OVERRIDES, FAMILIES)
TRACK_IDS = {
    "rse-littleroot-town",
    "rse-drought",
    "dppt-twinleaf-town",
    "rse-route-101",
    "gsc-clair",
    "rse-cable-car",
}


@pytest.fixture
def overrides(tmp_path: Path) -> Path:
    path = tmp_path / "overrides.json"
    path.write_text(OVERRIDES_TEXT, encoding="utf-8", newline="\n")
    return path


def diff(before: str, path: Path) -> list[str]:
    """Added and removed lines only."""
    after = path.read_text(encoding="utf-8").splitlines()
    lines_ = difflib.unified_diff(before.splitlines(), after, lineterm="", n=0)
    return [line for line in lines_ if line[:1] in "+-" and line[:3] not in ("+++", "---")]


def changes(path: Path) -> list[str]:
    return diff(OVERRIDES_TEXT, path)


def lines(path: Path) -> list[str]:
    return path.read_text(encoding="utf-8").splitlines()


def edit(path: Path, track_id: str, streams: dict[str, object]) -> None:
    set_streams(path, track_id, streams, TRACK_IDS, FAMILIES)


def test_accepting_a_video_adds_one_line_in_the_tracks_family(overrides: Path) -> None:
    edit(overrides, "rse-littleroot-town", {"accept": [{"videoId": "ID2Iv5HP-0I"}]})
    line = (
        '    "rse-littleroot-town": { "streams": { "accept": [ { "videoId": "ID2Iv5HP-0I" } ] } },'
    )
    assert changes(overrides) == ["+" + line]
    text = lines(overrides)
    assert text[text.index(line) + 1] == ""  # the last of the rse group, before frlg
    assert load_overrides(overrides).tracks["rse-littleroot-town"].streams is not None


def test_slices_keep_only_what_differs_from_the_defaults(overrides: Path) -> None:
    accept = [{"videoId": "ID2Iv5HP-0I", "startMs": 0, "endMs": None},
              {"videoId": "cnzggrebNS0", "startMs": 5000, "endMs": 9000}]  # fmt: skip
    edit(overrides, "rse-littleroot-town", {"accept": accept, "reject": ["abcdefghijk"]})
    assert changes(overrides) == [
        '+    "rse-littleroot-town": { "streams": { "accept": [ { "videoId": "ID2Iv5HP-0I" }, '
        '{ "videoId": "cnzggrebNS0", "startMs": 5000, "endMs": 9000 } ], '
        '"reject": ["abcdefghijk"] } },'
    ]


def test_a_decision_joins_an_existing_override_in_place(overrides: Path) -> None:
    edit(overrides, "rse-drought", {"reject": ["abcdefghijk"]})
    assert changes(overrides) == [
        '-    "rse-drought": { "games": ["ruby"], "note": "Groudon\'s event; Emerald uses Abnormal '
        'Weather" },',
        '+    "rse-drought": { "games": ["ruby"], "streams": { "reject": ["abcdefghijk"] }, '
        '"note": "Groudon\'s event; Emerald uses Abnormal Weather" },',
    ]


def test_a_decision_that_makes_an_entry_too_long_moves_its_note_to_a_second_line(
    overrides: Path,
) -> None:
    edit(overrides, "rse-drought", {"accept": [{"videoId": "ID2Iv5HP-0I"}]})
    assert changes(overrides) == [
        '-    "rse-drought": { "games": ["ruby"], "note": "Groudon\'s event; Emerald uses Abnormal '
        'Weather" },',
        '+    "rse-drought": { "games": ["ruby"], '
        '"streams": { "accept": [ { "videoId": "ID2Iv5HP-0I" } ] },',
        # Aligned under the first key, after '    "rse-drought": { '.
        '+                     "note": "Groudon\'s event; Emerald uses Abnormal Weather" },',
    ]
    assert load_overrides(overrides).tracks["rse-drought"].note is not None


def test_a_family_without_overrides_gets_its_own_group_in_family_order(overrides: Path) -> None:
    edit(overrides, "dppt-twinleaf-town", {"accept": [{"videoId": "ID2Iv5HP-0I"}]})
    text = lines(overrides)
    at = text.index(
        '    "dppt-twinleaf-town": { "streams": { "accept": [ { "videoId": "ID2Iv5HP-0I" } ] } },'
    )
    assert text[at - 1] == "" and text[at + 1] == ""
    assert text[at - 2].startswith('    "frlg-') and text[at + 2].startswith('    "hgss-')


def test_clearing_a_decision_removes_an_override_left_empty(overrides: Path) -> None:
    edit(overrides, "rse-littleroot-town", {"accept": [{"videoId": "ID2Iv5HP-0I"}]})
    edit(overrides, "rse-littleroot-town", {"accept": [], "reject": []})
    assert changes(overrides) == []


def test_clearing_keeps_the_rest_of_an_override(overrides: Path) -> None:
    edit(overrides, "rse-drought", {"reject": ["abcdefghijk"]})
    edit(overrides, "rse-drought", {})
    assert changes(overrides) == []


@pytest.mark.parametrize(
    ("track_id", "streams", "error"),
    [
        ("rse-no-such-track", {"accept": [{"videoId": "ID2Iv5HP-0I"}]}, "rse-no-such-track"),
        ("rse-littleroot-town", {"accept": [{"videoId": "ID2Iv5HP-0I"}], "reject": ["ID2Iv5HP-0I"]},
         "both accepted and rejected"),
        ("rse-littleroot-town", {"accept": [{"videoId": "https://youtu.be/ID2Iv5HP-0I"}]},
         "not a YouTube video id"),
        ("rse-littleroot-town", {"accept": [{"videoId": "ID2Iv5HP-0I", "endMs": 0}]}, "endMs"),
        ("rse-littleroot-town", {"keep": True}, "keep"),
    ],
)  # fmt: skip
def test_an_invalid_edit_is_refused_and_nothing_is_written(
    overrides: Path, track_id: str, streams: dict[str, object], error: str
) -> None:
    before = overrides.read_bytes()
    with pytest.raises(EditError) as raised:
        edit(overrides, track_id, streams)
    assert any(error in e for e in raised.value.errors)
    assert overrides.read_bytes() == before
    assert not list(overrides.parent.glob("*.tmp"))


def test_removing_a_track_writes_exclude_before_its_note(overrides: Path) -> None:
    set_exclude(overrides, "rse-drought", True, TRACK_IDS, FAMILIES)
    assert changes(overrides) == [
        '-    "rse-drought": { "games": ["ruby"], "note": "Groudon\'s event; Emerald uses Abnormal '
        'Weather" },',
        '+    "rse-drought": { "games": ["ruby"], "exclude": true, '
        '"note": "Groudon\'s event; Emerald uses Abnormal Weather" },',
    ]


def test_restoring_a_track_removes_exclude_and_an_override_left_empty(overrides: Path) -> None:
    set_exclude(overrides, "rse-littleroot-town", True, TRACK_IDS, FAMILIES)
    assert changes(overrides) == ['+    "rse-littleroot-town": { "exclude": true },']
    set_exclude(overrides, "rse-littleroot-town", None, TRACK_IDS, FAMILIES)
    assert changes(overrides) == []


def test_restoring_a_kept_jingle_writes_exclude_false_again(overrides: Path) -> None:
    set_exclude(overrides, "rse-cable-car", True, TRACK_IDS, FAMILIES)
    set_exclude(overrides, "rse-cable-car", False, TRACK_IDS, FAMILIES)
    assert changes(overrides) == []


def test_removing_an_unknown_track_is_refused(overrides: Path) -> None:
    with pytest.raises(EditError):
        set_exclude(overrides, "rse-no-such-track", True, TRACK_IDS, FAMILIES)
    assert changes(overrides) == []


def test_setting_games_writes_them_and_none_removes_them(overrides: Path) -> None:
    set_games(overrides, "rse-littleroot-town", ["ruby", "sapphire"], TRACK_IDS, FAMILIES)
    assert changes(overrides) == ['+    "rse-littleroot-town": { "games": ["ruby", "sapphire"] },']
    set_games(overrides, "rse-littleroot-town", None, TRACK_IDS, FAMILIES)
    assert changes(overrides) == []


def test_changing_games_keeps_the_rest_of_an_override(overrides: Path) -> None:
    set_games(overrides, "rse-drought", ["ruby", "sapphire"], TRACK_IDS, FAMILIES)
    assert changes(overrides) == [
        f'-    "rse-drought": {{ "games": ["ruby"], "note": "{DROUGHT_NOTE}" }},',
        f'+    "rse-drought": {{ "games": ["ruby", "sapphire"], "note": "{DROUGHT_NOTE}" }},',
    ]
    set_games(overrides, "rse-drought", None, TRACK_IDS, FAMILIES)
    assert changes(overrides) == [
        f'-    "rse-drought": {{ "games": ["ruby"], "note": "{DROUGHT_NOTE}" }},',
        f'+    "rse-drought": {{ "note": "{DROUGHT_NOTE}" }},',
    ]


@pytest.mark.parametrize("games", [[], ["ruby", "ruby"], "ruby"])
def test_invalid_games_are_refused(overrides: Path, games: object) -> None:
    with pytest.raises(EditError):
        set_games(overrides, "rse-littleroot-town", games, TRACK_IDS, FAMILIES)  # type: ignore[arg-type]
    assert changes(overrides) == []


# Playlists in games.json

DP_LIST = "PLA4BF48BF9073F50B"


@pytest.fixture
def games(tmp_path: Path) -> Path:
    path = tmp_path / "games.json"
    path.write_text(GAMES_TEXT, encoding="utf-8", newline="\n")
    return path


def games_changes(path: Path) -> list[str]:
    return diff(GAMES_TEXT, path)


def playlist_lines(path: Path) -> list[str]:
    text = lines(path)
    start = text.index('    "playlists": [')
    end = text.index("    ],", start)
    return text[start + 1 : end]


def test_adding_a_playlist_adds_one_line_in_family_order(games: Path) -> None:
    add_playlist(games, {"games": ["diamond", "pearl"], "playlist": DP_LIST, "note": "discomonk"})
    line = (
        f'      {{ "games": ["diamond", "pearl"], "playlist": "{DP_LIST}", "note": "discomonk" }},'
    )
    assert games_changes(games) == ["+" + line]
    # dppt comes after frlg and before hgss in the games file.
    playlists = playlist_lines(games)
    assert '"firered"' in playlists[playlists.index(line) - 1]
    assert '"heartgold"' in playlists[playlists.index(line) + 1]


def test_a_familys_new_playlist_goes_after_its_other_playlists(games: Path) -> None:
    add_playlist(games, {"games": ["emerald"], "playlist": DP_LIST, "first": 1, "last": 17})
    playlists = playlist_lines(games)
    at = next(i for i, p in enumerate(playlists) if DP_LIST in p)
    assert RSE_LIST in playlists[at - 1]
    assert '"first": 1, "last": 17' in playlists[at]
    assert '"firered"' in playlists[at + 1]


def test_a_playlist_can_span_families_and_ranks_by_its_first_game(games: Path) -> None:
    add_playlist(games, {"games": ["platinum", "emerald"], "playlist": DP_LIST})
    playlists = playlist_lines(games)
    at = next(i for i, p in enumerate(playlists) if DP_LIST in p)
    assert '"firered"' in playlists[at - 1] and '"heartgold"' in playlists[at + 1]
    assert load_games(games).youtube.playlists[at].games == ["platinum", "emerald"]


def test_a_family_after_every_playlist_goes_last(games: Path) -> None:
    add_playlist(games, {"games": ["scarlet", "violet"], "playlist": DP_LIST})
    assert DP_LIST in playlist_lines(games)[-1]
    assert load_games(games).youtube.playlists[-1].playlist == DP_LIST


def test_the_same_playlist_with_another_range_is_another_entry(games: Path) -> None:
    add_playlist(games, {"games": ["emerald"], "playlist": DP_LIST, "first": 1, "last": 17})
    add_playlist(games, {"games": ["platinum"], "playlist": DP_LIST, "first": 18})
    assert [p.playlist for p in load_games(games).youtube.playlists].count(DP_LIST) == 2


@pytest.mark.parametrize(
    ("entry", "error"),
    [
        ({"games": ["diamond", "unknown"], "playlist": DP_LIST}, "known game ids"),
        ({"games": [], "playlist": DP_LIST}, "games"),
        ({"games": ["diamond"], "playlist": "bad!"}, "playlist id"),
        ({"games": ["diamond"], "playlist": DP_LIST, "first": 5, "last": 2}, "first"),
        ({"games": ["diamond"], "playlist": DP_LIST, "first": 0}, "first"),
        ({"games": ["red", "blue", "yellow"], "playlist": RBY_LIST,
          "note": "Daintii Music, game rip"}, "already"),
    ],
)  # fmt: skip
def test_an_invalid_playlist_is_refused_and_nothing_is_written(
    games: Path, entry: dict[str, object], error: str
) -> None:
    before = games.read_bytes()
    with pytest.raises(EditError) as raised:
        add_playlist(games, entry)
    assert any(error in e for e in raised.value.errors), raised.value.errors
    assert games.read_bytes() == before
    assert not list(games.parent.glob("*.tmp"))


def test_removing_a_playlist_removes_its_line(games: Path) -> None:
    remove_playlist(games, 2, RSE_LIST)
    assert len(games_changes(games)) == 1
    assert games_changes(games)[0].startswith("-") and RSE_LIST in games_changes(games)[0]
    assert RSE_LIST not in [p.playlist for p in load_games(games).youtube.playlists]


def test_removing_with_a_stale_index_is_refused(games: Path) -> None:
    before = games.read_bytes()
    with pytest.raises(EditError):
        remove_playlist(games, 3, RSE_LIST)
    with pytest.raises(EditError):
        remove_playlist(games, 99, RSE_LIST)
    assert games.read_bytes() == before


# Renaming and notes


def test_a_rename_writes_the_title_and_pinned_streams_in_one_edit_before_the_note(
    overrides: Path,
) -> None:
    pinned = {"accept": [{"videoId": "ID2Iv5HP-0I"}, {"videoId": "cnzggrebNS0"}]}
    rename(overrides, "rse-drought", "Drought (Ruby)", pinned, TRACK_IDS, FAMILIES)
    assert changes(overrides) == [
        f'-    "rse-drought": {{ "games": ["ruby"], "note": "{DROUGHT_NOTE}" }},',
        '+    "rse-drought": { "games": ["ruby"], "title": "Drought (Ruby)", '
        '"streams": { "accept": [ { "videoId": "ID2Iv5HP-0I" }, { "videoId": "cnzggrebNS0" } ] },',
        f'+                     "note": "{DROUGHT_NOTE}" }},',
    ]


def test_a_rename_without_videos_to_pin_writes_only_the_title(overrides: Path) -> None:
    rename(overrides, "rse-littleroot-town", "Littleroot", None, TRACK_IDS, FAMILIES)
    assert changes(overrides) == ['+    "rse-littleroot-town": { "title": "Littleroot" },']


@pytest.mark.parametrize(
    ("title", "streams"), [("", None), ("Two\nlines", None), ("Ok", {"accept": [{"videoId": "x"}]})]
)
def test_an_invalid_rename_writes_nothing(
    overrides: Path, title: str, streams: dict[str, Any] | None
) -> None:
    with pytest.raises(EditError):
        rename(overrides, "rse-littleroot-town", title, streams, TRACK_IDS, FAMILIES)
    assert changes(overrides) == []


def test_a_note_replaces_the_old_one_and_stays_last(overrides: Path) -> None:
    set_note(overrides, "rse-drought", "Also plays in Emerald's Sky Pillar", TRACK_IDS, FAMILIES)
    assert changes(overrides) == [
        f'-    "rse-drought": {{ "games": ["ruby"], "note": "{DROUGHT_NOTE}" }},',
        '+    "rse-drought": { "games": ["ruby"], "note": "Also plays in Emerald\'s Sky Pillar" },',
    ]


def test_a_note_on_a_track_without_an_override_adds_one(overrides: Path) -> None:
    set_note(overrides, "rse-route-101", "A remix of Route 1", TRACK_IDS, FAMILIES)
    assert changes(overrides) == ['+    "rse-route-101": { "note": "A remix of Route 1" },']
    set_note(overrides, "rse-route-101", None, TRACK_IDS, FAMILIES)
    assert changes(overrides) == []


def test_clearing_a_note_keeps_the_rest_of_the_override(overrides: Path) -> None:
    set_note(overrides, "rse-drought", None, TRACK_IDS, FAMILIES)
    assert changes(overrides) == [
        f'-    "rse-drought": {{ "games": ["ruby"], "note": "{DROUGHT_NOTE}" }},',
        '+    "rse-drought": { "games": ["ruby"] },',
    ]


def test_a_note_on_an_unknown_track_is_refused(overrides: Path) -> None:
    with pytest.raises(EditError):
        set_note(overrides, "rse-no-such-track", "x", TRACK_IDS, FAMILIES)
    assert changes(overrides) == []


# Track order

ORDER_TEXT = format_order({"rby": ["rby-route-1", "rby-pallet-town"], "hgss": ["hgss-route-29"]})


@pytest.fixture
def order(tmp_path: Path) -> Path:
    path = tmp_path / "order.json"
    path.write_text(ORDER_TEXT, encoding="utf-8", newline="\n")
    return path


def test_setting_a_familys_order_rewrites_only_its_list(order: Path) -> None:
    set_order(order, "rby", ["rby-pallet-town", "rby-route-1"], FAMILIES)
    assert diff(ORDER_TEXT, order) == [
        '-    "rby-route-1",',
        '-    "rby-pallet-town"',
        '+    "rby-pallet-town",',
        '+    "rby-route-1"',
    ]


def test_a_new_familys_order_goes_in_family_order(order: Path) -> None:
    set_order(order, "rse", ["rse-route-101", "rse-littleroot-town"], FAMILIES)
    assert list(json.loads(order.read_text(encoding="utf-8"))) == ["rby", "rse", "hgss"]
    assert order.read_text(encoding="utf-8") == format_order(
        {"rby": ["rby-route-1", "rby-pallet-town"],
         "rse": ["rse-route-101", "rse-littleroot-town"],
         "hgss": ["hgss-route-29"]}
    )  # fmt: skip


def test_a_missing_order_file_is_created(tmp_path: Path) -> None:
    path = tmp_path / "order.json"
    set_order(path, "rse", ["rse-route-101"], FAMILIES)
    assert load_order(path).root == {"rse": ["rse-route-101"]}


@pytest.mark.parametrize(
    ("family", "ids"), [("rby", ["rby-route-1", "rby-route-1"]), ("nope", ["nope-x"]), ("rby", [])]
)
def test_an_invalid_order_writes_nothing(order: Path, family: str, ids: list[str]) -> None:
    with pytest.raises(EditError):
        set_order(order, family, ids, FAMILIES)
    assert order.read_text(encoding="utf-8") == ORDER_TEXT
    assert not list(order.parent.glob("*.tmp"))
