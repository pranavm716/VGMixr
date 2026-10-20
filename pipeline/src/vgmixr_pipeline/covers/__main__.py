"""Converts the hand-cropped covers in pipeline/covers-in/ into catalog/covers/<family>.webp.

Name each source after its family id (rse.jpg, dppt.png); any common image format works.

uv run python -m vgmixr_pipeline.covers
"""

import json
import sys
from pathlib import Path

from vgmixr_pipeline.covers import COVER_SIZE, CoverError, convert_covers, family_ids

PIPELINE_DIR = Path(__file__).resolve().parents[3]
SRC = PIPELINE_DIR / "covers-in"
CATALOG = PIPELINE_DIR.parent / "catalog"


def main() -> int:
    SRC.mkdir(exist_ok=True)
    manifest = json.loads((CATALOG / "manifest.json").read_text(encoding="utf-8"))
    try:
        result = convert_covers(SRC, CATALOG / "covers", family_ids(manifest))
    except CoverError as e:
        print(f"Nothing written:\n{e}", file=sys.stderr)
        return 1
    print(f"Wrote {len(result.written)} covers ({COVER_SIZE}px WebP): {', '.join(result.written)}")
    if result.missing:
        print(f"Families without a cover: {', '.join(result.missing)}")
    if result.orphans:
        print(f"Covers whose family is gone (delete them): {', '.join(result.orphans)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
