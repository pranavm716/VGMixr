"""The review tool's edits of the committed data files, on tmp copies of pipeline/data/."""

import difflib
import json
import shutil
from pathlib import Path

import pytest

from vgmixr_pipeline.catalog.config import DATA_DIR, load_games, load_overrides
from vgmixr_pipeline.catalog.jsonfmt import games_families, track_family
from vgmixr_pipeline.catalog.review.edits import (
    EditError,
    add_playlist,
    remove_playlist,
    set_exclude,
    set_streams,
)

FAMILIES = games_families(json.loads((DATA_DIR / "games.json").read_text(encoding="utf-8")))
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
    shutil.copy(DATA_DIR / "overrides.json", path)
    return path


def changes(path: Path) -> list[str]:
    """The diff against the committed file: added and removed lines only."""
    before = (DATA_DIR / "overrides.json").read_text(encoding="utf-8").splitlines()
    after = path.read_text(encoding="utf-8").splitlines()
    diff = difflib.unified_diff(before, after, lineterm="", n=0)
    return [line for line in diff if line[:1] in "+-" and line[:3] not in ("+++", "---")]


def family_rank(line: str) -> int:
    """The position in family order of the track key a line starts with."""
    family = track_family(line.split('"')[1], FAMILIES)
    assert family is not None
    return FAMILIES.index(family)


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


def test_a_family_without_overrides_gets_its_own_group_in_family_order(overrides: Path) -> None:
    edit(overrides, "dppt-twinleaf-town", {"accept": [{"videoId": "ID2Iv5HP-0I"}]})
    text = lines(overrides)
    at = text.index(
        '    "dppt-twinleaf-town": { "streams": { "accept": [ { "videoId": "ID2Iv5HP-0I" } ] } },'
    )
    assert text[at - 1] == "" and text[at + 1] == ""
    # The neighbouring groups belong to an earlier and a later family, whatever is committed.
    dppt = FAMILIES.index("dppt")
    assert family_rank(text[at - 2]) < dppt < family_rank(text[at + 2])


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


# Playlists in games.json

DP_LIST = "PLA4BF48BF9073F50B"
RSE_LIST = "PL2uxd6YWj7PIOlswxbt16G63Klr_3lsbR"


@pytest.fixture
def games(tmp_path: Path) -> Path:
    path = tmp_path / "games.json"
    shutil.copy(DATA_DIR / "games.json", path)
    return path


def games_changes(path: Path) -> list[str]:
    before = (DATA_DIR / "games.json").read_text(encoding="utf-8").splitlines()
    after = path.read_text(encoding="utf-8").splitlines()
    diff = difflib.unified_diff(before, after, lineterm="", n=0)
    return [line for line in diff if line[:1] in "+-" and line[:3] not in ("+++", "---")]


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
        ({"games": ["red", "blue", "yellow"], "playlist": "PL2uxd6YWj7PLbKlHSlTO7Lv4E6csDZhRc",
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
