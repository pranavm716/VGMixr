"""Pulls the app's broken.json and reports.json from the phone over adb into pipeline/out/.

uv run python -m vgmixr_pipeline.broken [--adb PATH]

adb isn't on PATH on this machine, so the default is the Android SDK's copy under %LOCALAPPDATA%.
"""

import argparse
import json
import sys
from pathlib import Path

from vgmixr_pipeline.adb import default_adb
from vgmixr_pipeline.broken import FILE_NAME, PullError, pull_broken, pull_reports, summarize

PIPELINE_DIR = Path(__file__).resolve().parents[3]
OUT = PIPELINE_DIR / "out"
MANIFEST = PIPELINE_DIR.parent / "catalog" / "manifest.json"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--adb", type=Path, default=default_adb(), help="adb's full path")
    args = parser.parse_args()
    try:
        broken = pull_broken(args.adb, OUT)
        reports = pull_reports(args.adb, OUT)
    except PullError as e:
        print(e, file=sys.stderr)
        return 1
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    families = {track["id"]: track["family"] for track in manifest["tracks"]}
    print(f"Wrote {OUT / FILE_NAME}")
    print("\n".join(summarize(broken, families)))
    count = len(reports.reports)
    print(f"{count} wrong-source report{'' if count == 1 else 's'}:")
    for report in reports.reports:
        print(f"  {report.track_id}: {report.video_id} at {report.position_ms // 1000} s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
