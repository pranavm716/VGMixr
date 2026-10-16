"""Builds the draft catalog from Bulbapedia and pipeline/data/, writing to pipeline/out/.

uv run python -m vgmixr_pipeline.catalog            # uses cached pages where present
uv run python -m vgmixr_pipeline.catalog --refresh  # refetches every page
"""

import argparse
import sys
from datetime import UTC, datetime
from pathlib import Path

from vgmixr_pipeline.catalog.build import BuildError, build_draft
from vgmixr_pipeline.catalog.bulbapedia import Bulbapedia
from vgmixr_pipeline.catalog.config import DATA_DIR, load_durations, load_games, load_overrides
from vgmixr_pipeline.catalog.report import family_report, summary

OUT_DIR = DATA_DIR.parent / "out"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0] if __doc__ else None)
    parser.add_argument("--refresh", action="store_true", help="refetch cached Bulbapedia pages")
    parser.add_argument("--out", type=Path, default=OUT_DIR, help="output directory")
    args = parser.parse_args(argv)

    games = load_games()
    bulbapedia = Bulbapedia(args.out / "bulbapedia", refresh=args.refresh)
    generated_at = datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
    try:
        draft, result = build_draft(
            games, load_overrides(), bulbapedia.wikitext, generated_at, load_durations()
        )
    except BuildError as e:
        print("Build failed:", *e.errors, sep="\n  ", file=sys.stderr)
        return 1

    draft_path = args.out / "draft.json"
    report_path = args.out / "family-report.md"
    draft_path.write_text(draft.model_dump_json(indent=2) + "\n", encoding="utf-8")
    report_path.write_text(family_report(games.games, result), encoding="utf-8")
    print(summary(games.games, result))
    print(f"\n{len(draft.tracks)} tracks. Wrote {draft_path} and {report_path}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
