import json
import shutil
from pathlib import Path

import pytest
from fixture_files import FIXTURES_DIR, readme_table_lines

from vgmixr_pipeline.fixtures import generate
from vgmixr_pipeline.fixtures.spec import FIXTURES, RawFixture


@pytest.fixture
def fixtures_copy(tmp_path: Path) -> Path:
    """A freshly generated fixtures folder, so tests can break it without touching docs/."""
    shutil.copy(FIXTURES_DIR / "README.md", tmp_path / "README.md")
    generate.write(tmp_path)
    return tmp_path


def test_spec_lists_every_committed_fixture() -> None:
    files = [fixture.file for fixture in FIXTURES]
    assert len(set(files)) == len(files)
    assert set(files) == {path.name for path in (FIXTURES_DIR / "invalid").iterdir()}


def test_rendered_fixtures_match_committed_values() -> None:
    for fixture in FIXTURES:
        committed = (FIXTURES_DIR / "invalid" / fixture.file).read_text(encoding="utf-8")
        rendered = generate.render_fixture(fixture)
        if isinstance(fixture, RawFixture):
            assert rendered == committed, fixture.file
        else:
            assert json.loads(rendered) == json.loads(committed), fixture.file


def test_rendered_table_matches_readme() -> None:
    assert generate.render_table().splitlines() == readme_table_lines("Invalid fixtures")


def test_committed_fixtures_are_up_to_date() -> None:
    assert generate.check(FIXTURES_DIR) == []


def test_check_passes_after_write(fixtures_copy: Path) -> None:
    assert generate.check(fixtures_copy) == []


def test_check_reports_a_changed_fixture(fixtures_copy: Path) -> None:
    (fixtures_copy / "invalid" / "tracks-missing.json").write_text("{}\n", encoding="utf-8")
    assert generate.check(fixtures_copy) == ["invalid/tracks-missing.json differs"]


def test_check_reports_a_missing_and_a_stray_fixture(fixtures_copy: Path) -> None:
    invalid = fixtures_copy / "invalid"
    (invalid / "tracks-missing.json").rename(invalid / "tracks-gone.json")
    assert generate.check(fixtures_copy) == [
        "invalid/tracks-gone.json is not in the spec",
        "invalid/tracks-missing.json is missing",
    ]


def test_check_reports_a_changed_table(fixtures_copy: Path) -> None:
    readme = fixtures_copy / "README.md"
    readme.write_text(
        readme.read_text(encoding="utf-8").replace("unclosed brace", "unclosed"),
        encoding="utf-8",
        newline="\n",
    )
    assert generate.check(fixtures_copy) == ["README.md differs"]


def test_write_removes_stray_fixtures(fixtures_copy: Path) -> None:
    stray = fixtures_copy / "invalid" / "stray.json"
    stray.write_text("{}\n", encoding="utf-8")
    generate.write(fixtures_copy)
    assert not stray.exists()


def test_fixtures_use_lf_line_endings(fixtures_copy: Path) -> None:
    for path in [fixtures_copy / "README.md", *(fixtures_copy / "invalid").iterdir()]:
        assert b"\r\n" not in path.read_bytes(), path.name


def test_main_check_exit_codes(fixtures_copy: Path) -> None:
    assert generate.main(["--check", "--dir", str(fixtures_copy)]) == 0
    (fixtures_copy / "invalid" / "tracks-missing.json").write_text("{}\n", encoding="utf-8")
    assert generate.main(["--check", "--dir", str(fixtures_copy)]) == 1
