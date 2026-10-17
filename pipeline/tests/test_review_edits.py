"""The review tool's edits of the committed data files, on tmp copies of pipeline/data/."""

import difflib
import json
import shutil
from pathlib import Path

import pytest

from vgmixr_pipeline.catalog.config import DATA_DIR, load_overrides
from vgmixr_pipeline.catalog.jsonfmt import games_families, track_family
from vgmixr_pipeline.catalog.review.edits import EditError, set_exclude, set_streams

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
