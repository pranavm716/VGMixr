"""The review tool: a local page for deciding the catalog's track list and fixing broken sources.

Steps 17-18 (track review, playlists, order) and 10b (the Broken tab: the sources the app reported
broken, docs/broken.md) in docs/roadmap.md.

Reads pipeline/out/draft.json (run the builder first) and pipeline/data/, and writes decisions to
pipeline/data/. Matching uses the YouTube cache only; searches and lookups from the page ask
YouTube unless --cached-only is set.

uv run python -m vgmixr_pipeline.catalog.review                 # http://127.0.0.1:8765
uv run python -m vgmixr_pipeline.catalog.review --cached-only   # never ask YouTube
"""

import argparse
import sys
import time
from pathlib import Path

from vgmixr_pipeline.broken.recheck import AppChecks
from vgmixr_pipeline.catalog.__main__ import MANIFEST, OUT_DIR
from vgmixr_pipeline.catalog.config import DATA_DIR
from vgmixr_pipeline.catalog.review.server import make_server
from vgmixr_pipeline.catalog.review.service import Review
from vgmixr_pipeline.catalog.youtube import YouTube


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0] if __doc__ else None)
    parser.add_argument("--port", type=int, default=8765, help="port on 127.0.0.1")
    parser.add_argument(
        "--cached-only", action="store_true", help="use cached YouTube responses only"
    )
    args = parser.parse_args(argv)

    out: Path = OUT_DIR
    if not (out / "draft.json").exists():
        print(f"No {out / 'draft.json'}: run `uv run python -m vgmixr_pipeline.catalog` first.",
              file=sys.stderr)  # fmt: skip
        return 1
    cache = out / "youtube"
    started = time.monotonic()
    print("Matching every track from the YouTube cache…", flush=True)
    review = Review(
        DATA_DIR,
        out,
        cached=YouTube(cache, cached_only=True),
        live=YouTube(cache, delay=0),
        cached_only=args.cached_only,
        manifest_path=MANIFEST,
        app_checks=AppChecks(cache / "app-check"),
    )
    server = make_server(review, args.port)
    print(
        f"Ready in {time.monotonic() - started:.0f} s: http://127.0.0.1:{args.port} (Ctrl+C stops)",
        flush=True,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
