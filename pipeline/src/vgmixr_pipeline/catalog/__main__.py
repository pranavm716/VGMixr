"""Builds the catalog from Bulbapedia, YouTube and pipeline/data/ into catalog/manifest.json.

Writes the draft, the family report, the match report and every track's best YouTube candidates to
pipeline/out/, then the manifest, only if it validates.

uv run python -m vgmixr_pipeline.catalog                    # uses cached responses where present
uv run python -m vgmixr_pipeline.catalog --refresh          # refetches every Bulbapedia page
uv run python -m vgmixr_pipeline.catalog --refresh-youtube  # refetches every YouTube response
uv run python -m vgmixr_pipeline.catalog --no-search        # playlists and albums only
uv run python -m vgmixr_pipeline.catalog --cached-only      # no YouTube requests at all
"""

import argparse
import json
import sys
from datetime import UTC, datetime
from pathlib import Path

from vgmixr_pipeline.catalog.build import BuildError, build_draft
from vgmixr_pipeline.catalog.bulbapedia import Bulbapedia
from vgmixr_pipeline.catalog.config import (
    DATA_DIR,
    load_durations,
    load_games,
    load_order,
    load_overrides,
)
from vgmixr_pipeline.catalog.manifest_out import write_manifest
from vgmixr_pipeline.catalog.match import candidates_json, match_tracks
from vgmixr_pipeline.catalog.report import family_report, match_report, match_summary, summary
from vgmixr_pipeline.catalog.score import Names
from vgmixr_pipeline.catalog.youtube import YouTube

OUT_DIR = DATA_DIR.parent / "out"
MANIFEST = DATA_DIR.parents[1] / "catalog" / "manifest.json"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0] if __doc__ else None)
    parser.add_argument("--refresh", action="store_true", help="refetch cached Bulbapedia pages")
    parser.add_argument(
        "--refresh-youtube", action="store_true", help="refetch cached YouTube responses"
    )
    parser.add_argument(
        "--no-search", action="store_true", help="match from playlists and albums only"
    )
    parser.add_argument(
        "--cached-only", action="store_true", help="use cached YouTube responses only"
    )
    parser.add_argument("--out", type=Path, default=OUT_DIR, help="output directory")
    parser.add_argument("--manifest", type=Path, default=MANIFEST, help="manifest to write")
    args = parser.parse_args(argv)

    games = load_games()
    bulbapedia = Bulbapedia(args.out / "bulbapedia", refresh=args.refresh)
    generated_at = datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
    try:
        draft, result = build_draft(
            games,
            load_overrides(),
            bulbapedia.wikitext,
            generated_at,
            load_durations(),
            order=load_order(),
        )
    except BuildError as e:
        print("Build failed:", *e.errors, sep="\n  ", file=sys.stderr)
        return 1

    draft_path = args.out / "draft.json"
    report_path = args.out / "family-report.md"
    draft_path.write_text(draft.model_dump_json(indent=2) + "\n", encoding="utf-8")
    report_path.write_text(family_report(games.games, result), encoding="utf-8")
    print(summary(games.games, result))
    print(f"\n{len(draft.tracks)} tracks. Wrote {draft_path} and {report_path}.\n")

    youtube = YouTube(
        args.out / "youtube", refresh=args.refresh_youtube, cached_only=args.cached_only
    )
    names = Names.build(games.games, games.youtube.aliases)
    matches = match_tracks(
        draft, games.youtube, load_overrides(), youtube, names, search=not args.no_search
    )
    match_path = args.out / "match-report.md"
    candidates_path = args.out / "candidates.json"
    match_path.write_text(match_report(games.games, matches), encoding="utf-8")
    candidates = json.dumps(candidates_json(matches), ensure_ascii=False, indent=1)
    candidates_path.write_text(candidates + "\n", encoding="utf-8")
    print(match_summary(games.games, matches))
    print(f"\nWrote {match_path} and {candidates_path}.")

    errors = write_manifest(args.manifest, draft, matches, generated_at)
    if errors:
        print("Manifest invalid, not written:", *errors, sep="\n  ", file=sys.stderr)
        return 1
    print(f"Wrote {args.manifest}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
