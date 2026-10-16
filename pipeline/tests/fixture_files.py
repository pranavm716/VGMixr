from pathlib import Path

FIXTURES_DIR = Path(__file__).resolve().parents[2] / "docs" / "fixtures"


def readme_table_lines(heading: str) -> list[str]:
    """Lines of the first table under `## <heading>` in the fixtures README."""
    lines = (FIXTURES_DIR / "README.md").read_text(encoding="utf-8").splitlines()
    start = lines.index(f"## {heading}") + 1
    table: list[str] = []
    for line in lines[start:]:
        if line.startswith("## "):
            break
        if line.startswith("|"):
            table.append(line)
    return table


def readme_table(heading: str) -> list[list[str]]:
    """Body rows of that table, cells trimmed and unquoted from backticks.

    Parsed the same way as the app's ManifestFixtures.tableRows, so both sides read one table.
    """
    return [
        [cell.strip().removeprefix("`").removesuffix("`") for cell in line.strip("|").split("|")]
        for line in readme_table_lines(heading)[2:]  # header and separator rows
    ]
