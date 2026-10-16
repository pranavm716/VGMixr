import pytest
from fixture_files import FIXTURES_DIR, readme_table

from vgmixr_pipeline.manifest.parse import Invalid, parse

ROWS = readme_table("Invalid fixtures")


@pytest.mark.parametrize(("file", "code"), [row[:2] for row in ROWS], ids=[row[0] for row in ROWS])
def test_invalid_fixture_fails_with_exactly_its_error(file: str, code: str) -> None:
    result = parse((FIXTURES_DIR / "invalid" / file).read_text(encoding="utf-8"))
    assert isinstance(result, Invalid), "manifest passed validation"
    assert [error.code for error in result.errors] == [code], result.errors
