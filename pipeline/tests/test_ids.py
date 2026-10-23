"""ids.json: the frozen catalog ids, source key -> id (docs/roadmap.md, step 12e)."""

from pathlib import Path

import pytest

from vgmixr_pipeline.catalog.ids import load_ids, updated, write_ids
from vgmixr_pipeline.catalog.jsonfmt import format_ids

FAMILIES = ["rse", "dppt"]


def test_a_missing_file_freezes_nothing(tmp_path: Path) -> None:
    assert load_ids(tmp_path / "ids.json") == {}


@pytest.mark.parametrize("text", ['["rse-a"]', '{"rse-a": 1}', '{"rse-a": null}'])
def test_anything_but_a_map_of_strings_is_an_error(tmp_path: Path, text: str) -> None:
    path = tmp_path / "ids.json"
    path.write_text(text, encoding="utf-8")
    with pytest.raises(ValueError, match="ids.json"):
        load_ids(path)


def test_updated_adds_new_entries_and_never_removes_or_changes_one() -> None:
    frozen = {"rse-a": "rse-a-old", "rse-gone": "rse-gone"}
    shipped = {"rse-a": "rse-a-old", "rse-b": "rse-b"}
    assert updated(frozen, shipped) == {"rse-a": "rse-a-old", "rse-gone": "rse-gone",
                                        "rse-b": "rse-b"}  # fmt: skip


def test_written_by_family_in_games_order_and_by_key_within_a_family(tmp_path: Path) -> None:
    path = tmp_path / "ids.json"
    write_ids(path, {"rse-b": "rse-b", "dppt-a": "dppt-a-day", "rse-a": "rse-a"}, FAMILIES)
    assert path.read_text(encoding="utf-8") == (
        '{\n  "rse-a": "rse-a",\n  "rse-b": "rse-b",\n\n  "dppt-a": "dppt-a-day"\n}\n'
    )
    assert load_ids(path) == {"rse-a": "rse-a", "rse-b": "rse-b", "dppt-a": "dppt-a-day"}
    assert list(tmp_path.iterdir()) == [path]


def test_format_ids_keeps_the_order_given() -> None:
    assert format_ids({"dppt-a": "dppt-a", "rse-a": "rse-a"}, FAMILIES) == (
        '{\n  "dppt-a": "dppt-a",\n\n  "rse-a": "rse-a"\n}\n'
    )


def test_an_empty_file_is_an_empty_map() -> None:
    assert format_ids({}, FAMILIES) == "{}\n"
