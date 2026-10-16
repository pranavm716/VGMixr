"""Guards the README tables that drive the fixture tests against drifting from the files."""

from fixture_files import FIXTURES_DIR, readme_table

from vgmixr_pipeline.manifest.errors import ErrorCode


def test_fixture_table_lists_exactly_the_files_in_invalid() -> None:
    files = [row[0] for row in readme_table("Invalid fixtures")]
    assert len(files) == len(set(files))
    assert set(files) == {path.name for path in (FIXTURES_DIR / "invalid").iterdir()}


def test_fixture_table_uses_every_error_code_and_no_others() -> None:
    codes = {row[0] for row in readme_table("Error codes")}
    assert {row[1] for row in readme_table("Invalid fixtures")} == codes


def test_error_code_table_matches_the_26_error_codes() -> None:
    codes = {row[0] for row in readme_table("Error codes")}
    assert len(codes) == 26
    assert codes == {code.value for code in ErrorCode}
