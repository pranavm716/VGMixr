"""Backs up the app's database (playlists, favorites, play history) between the phone and the PC.

uv run python -m vgmixr_pipeline.backup pull [--adb PATH]
uv run python -m vgmixr_pipeline.backup push FOLDER [--yes] [--adb PATH]

pull stops VGMixr (refusing while it plays) and copies vgmixr.db with its -wal and -shm files into
backups/<local time>/ at the repo root (gitignored). push pulls the phone first (the rollback
point), asks, then writes FOLDER's files back and deletes a -wal or -shm the backup lacks. Both
need a debug build on the phone, and a backup restores into the same or a newer app version only.
"""

import argparse
import sys
from pathlib import Path

from vgmixr_pipeline.adb import default_adb
from vgmixr_pipeline.backup import BackupError, pull, push, summarize, validate_backup

BACKUPS = Path(__file__).resolve().parents[4] / "backups"


def ask(lines: list[str]) -> bool:
    print("\n".join(lines))
    return input("Push? [y/N] ").strip().lower() in ("y", "yes")


def tell(lines: list[str]) -> bool:
    print("\n".join(lines))
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--adb", type=Path, default=default_adb(), help="adb's full path")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("pull", help="copy the phone's database into a new backup folder")
    push_parser = commands.add_parser("push", help="write a backup folder back to the phone")
    push_parser.add_argument("folder", type=Path, help="a folder made by pull")
    push_parser.add_argument("--yes", action="store_true", help="don't ask before writing")
    args = parser.parse_args()
    try:
        if args.command == "pull":
            folder = pull(args.adb, BACKUPS)
            print(f"Pulled into {folder}")
            print("\n".join(summarize(folder)))
        else:
            validate_backup(args.folder)
            print(f"{args.folder} holds:")
            print("\n".join(summarize(args.folder)))
            rollback = push(args.adb, args.folder, BACKUPS, confirm=tell if args.yes else ask)
            print(f"Pushed {args.folder}. To undo: push {rollback}")
    except BackupError as e:
        print(e, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
