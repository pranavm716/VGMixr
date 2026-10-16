"""Writes docs/fixtures/invalid/ and the README's invalid-fixture table from spec.py.

uv run python -m vgmixr_pipeline.fixtures.generate          # write
uv run python -m vgmixr_pipeline.fixtures.generate --check  # exit 1 if out of date
"""

import argparse
import copy
import json
import sys
from pathlib import Path

from vgmixr_pipeline.fixtures.spec import BASE, FIXTURES, Fixture, RawFixture

FIXTURES_DIR = Path(__file__).resolve().parents[4] / "docs" / "fixtures"
README = "README.md"
INVALID = "invalid"
TABLE_START = "<!-- Generated from pipeline/src/vgmixr_pipeline/fixtures/spec.py. Do not edit. -->"
TABLE_END = "<!-- End of generated table. -->"


def render_fixture(fixture: Fixture | RawFixture) -> str:
    if isinstance(fixture, RawFixture):
        return fixture.text
    manifest = copy.deepcopy(BASE)
    fixture.patch(manifest)
    return json.dumps(manifest, indent=2, ensure_ascii=False) + "\n"


def render_table() -> str:
    rows = [f"| `{f.file}` | `{f.code.value}` | {f.defect} |" for f in FIXTURES]
    return "\n".join(["| File | Code | Defect |", "|---|---|---|", *rows]) + "\n"


def render_readme(readme: str) -> str:
    """Replaces the generated table between its markers, keeping the rest of the README."""
    before, start, rest = readme.partition(TABLE_START + "\n")
    _, end, after = rest.partition(TABLE_END + "\n")
    if not start or not end:
        raise ValueError(f"{README} is missing the generated-table markers")
    return before + start + render_table() + end + after


def render(fixtures_dir: Path) -> dict[str, str]:
    """Every generated file's content, keyed by its POSIX path relative to `fixtures_dir`."""
    readme = (fixtures_dir / README).read_text(encoding="utf-8")
    files = {f"{INVALID}/{f.file}": render_fixture(f) for f in FIXTURES}
    return files | {README: render_readme(readme)}


def check(fixtures_dir: Path) -> list[str]:
    """Problems that `write` would fix, or an empty list if `fixtures_dir` is up to date."""
    expected = render(fixtures_dir)
    on_disk = {f"{INVALID}/{path.name}" for path in (fixtures_dir / INVALID).iterdir()}
    problems = []
    for name in sorted(on_disk | expected.keys()):
        path = fixtures_dir / name
        if name not in expected:
            problems.append(f"{name} is not in the spec")
        elif not path.exists():
            problems.append(f"{name} is missing")
        elif path.read_bytes() != expected[name].encode():
            problems.append(f"{name} differs")
    return problems


def write(fixtures_dir: Path) -> None:
    expected = render(fixtures_dir)
    invalid = fixtures_dir / INVALID
    invalid.mkdir(exist_ok=True)
    for path in invalid.iterdir():
        if f"{INVALID}/{path.name}" not in expected:
            path.unlink()
    for name, content in expected.items():
        # newline="\n": the repo is LF-only, and Windows would otherwise write CRLF.
        (fixtures_dir / name).write_text(content, encoding="utf-8", newline="\n")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="report stale files, write nothing")
    parser.add_argument("--dir", type=Path, default=FIXTURES_DIR, help="fixtures folder")
    args = parser.parse_args(argv)
    if not args.check:
        write(args.dir)
        return 0
    problems = check(args.dir)
    for problem in problems:
        print(f"docs/fixtures/{problem}", file=sys.stderr)
    if problems:
        print("Fixtures are stale: run the generator and commit the result.", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
