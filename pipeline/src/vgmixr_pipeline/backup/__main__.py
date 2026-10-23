"""Backs up the app's database (playlists, favorites, play history) from the phone over adb.

uv run python -m vgmixr_pipeline.backup pull [--adb PATH]

pull stops VGMixr (refusing while it plays) and copies vgmixr.db with its -wal and -shm files into
backups/<local time>/ at the repo root (gitignored). It needs a debug build on the phone.
"""

import argparse
import sys
from pathlib import Path

from vgmixr_pipeline.adb import default_adb
from vgmixr_pipeline.backup import BackupError, pull, summarize

BACKUPS = Path(__file__).resolve().parents[4] / "backups"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--adb", type=Path, default=default_adb(), help="adb's full path")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("pull", help="copy the phone's database into a new backup folder")
    args = parser.parse_args()
    try:
        folder = pull(args.adb, BACKUPS)
    except BackupError as e:
        print(e, file=sys.stderr)
        return 1
    print(f"Pulled into {folder}")
    print("\n".join(summarize(folder)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
