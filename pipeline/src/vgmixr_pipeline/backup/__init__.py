"""Backups of the app's Room database (playlists, favorites, play history) over adb.

`pull` copies vgmixr.db with its -wal and -shm files from the phone into a dated folder, so
changes still in the WAL file come along. It reads them with `run-as`, which needs a debug build
on the phone. Every copied file is checked against the phone's own sha256sum: `adb exec-out`
returns errors as output with exit code 0, so a bad copy would otherwise look fine.
"""

import hashlib
import re
import shutil
import sqlite3
import tempfile
from datetime import datetime, tzinfo
from pathlib import Path

from vgmixr_pipeline.adb import PACKAGE, BytesRunner, run_bytes

DB = "vgmixr.db"
DB_FILES = (DB, f"{DB}-wal", f"{DB}-shm")
REMOTE_DIR = "databases"

# PlaybackState numbers that mean the app is busy with a track: playing, fast-forwarding,
# rewinding, buffering, connecting.
_ACTIVE_STATES = {3, 4, 5, 6, 8}


class BackupError(Exception):
    """A backup step failed; nothing half-written is left behind."""


def backup_name(now: datetime, taken: set[str]) -> str:
    """The folder name for a backup taken at [now] (local time), unique among [taken]."""
    name = now.strftime("%Y-%m-%d_%H%M%S")
    if name not in taken:
        return name
    n = 2
    while f"{name}_{n}" in taken:
        n += 1
    return f"{name}_{n}"


def parse_ls(text: str) -> set[str]:
    """The database files in `run-as <package> ls databases` output."""
    if text.startswith("run-as:"):
        raise BackupError(
            f"{text.strip()}\nrun-as needs a debug build of VGMixr on the phone, not a release APK"
        )
    if "No such file" in text:
        raise BackupError(f"The app has no databases folder: {text.strip()}")
    return set(text.split()) & set(DB_FILES)


def parse_sha256(text: str, file: str) -> str | None:
    """The hash `sha256sum databases/<file>` printed, or None if it couldn't read the file."""
    match = re.search(rf"^([0-9a-f]{{64}})  {REMOTE_DIR}/{re.escape(file)}$", text, re.MULTILINE)
    return match.group(1) if match else None


def is_playing(dumpsys_media_session: str) -> bool:
    """Whether VGMixr's media session is playing (or about to), per `dumpsys media_session`."""
    package = None
    for line in dumpsys_media_session.splitlines():
        line = line.strip()
        if line.startswith("package="):
            package = line.removeprefix("package=")
        elif line.startswith("state=PlaybackState") and package == PACKAGE:
            match = re.search(r"\{state=\w+\((\d+)\)", line)
            if match and int(match.group(1)) in _ACTIVE_STATES:
                return True
    return False


def _adb(adb: Path, run: BytesRunner, *args: str, input: bytes | None = None) -> bytes:
    try:
        result = run([str(adb), *args], input=input)
    except FileNotFoundError as e:
        raise BackupError(f"adb not found at {adb}; pass its path with --adb") from e
    if result.returncode != 0:
        message = (result.stderr or result.stdout).decode(errors="replace").strip()
        raise BackupError(f"adb {' '.join(args)} failed: {message}")
    return result.stdout


def _run_as(adb: Path, run: BytesRunner, *args: str, input: bytes | None = None) -> bytes:
    return _adb(adb, run, "exec-out", "run-as", PACKAGE, *args, input=input)


def stop_app(adb: Path, run: BytesRunner) -> None:
    """Force-stops VGMixr so nothing writes the database, unless it is playing."""
    dumpsys = _adb(adb, run, "shell", "dumpsys", "media_session").decode(errors="replace")
    if is_playing(dumpsys):
        raise BackupError("VGMixr is playing: pause it first, so stopping the app loses nothing")
    _adb(adb, run, "shell", "am", "force-stop", PACKAGE)


def phone_files(adb: Path, run: BytesRunner) -> set[str]:
    """The database files on the phone right now."""
    return parse_ls(_run_as(adb, run, "ls", REMOTE_DIR).decode(errors="replace"))


def phone_hash(adb: Path, run: BytesRunner, file: str) -> str | None:
    text = _run_as(adb, run, "sha256sum", f"{REMOTE_DIR}/{file}").decode(errors="replace")
    return parse_sha256(text, file)


def pull(
    adb: Path, backups_dir: Path, run: BytesRunner = run_bytes, now: datetime | None = None
) -> Path:
    """Stops the app and copies its database files into a new folder in [backups_dir]."""
    stop_app(adb, run)
    files = phone_files(adb, run)
    if DB not in files:
        raise BackupError(f"No {DB} on the phone: open VGMixr once to create it")
    backups_dir.mkdir(parents=True, exist_ok=True)
    taken = {p.name.removesuffix(".part") for p in backups_dir.iterdir()}
    folder = backups_dir / backup_name(now or datetime.now(), taken)
    part = folder.with_name(f"{folder.name}.part")
    try:
        part.mkdir()
        for file in sorted(files):
            data = _run_as(adb, run, "cat", f"{REMOTE_DIR}/{file}")
            if hashlib.sha256(data).hexdigest() != phone_hash(adb, run, file):
                raise BackupError(f"The copy of {file} differs from the phone's; nothing was kept")
            (part / file).write_bytes(data)
        part.rename(folder)
    finally:
        shutil.rmtree(part, ignore_errors=True)
    return folder


def summarize(folder: Path, tz: tzinfo | None = None) -> list[str]:
    """Playlists with their track counts, and the play history, read from a backup.

    Opening a WAL database checkpoints it on close, so this reads a temporary copy."""
    with tempfile.TemporaryDirectory() as temp:
        for file in DB_FILES:
            if (folder / file).exists():
                shutil.copyfile(folder / file, Path(temp) / file)
        db = sqlite3.connect(Path(temp) / DB)
        try:
            playlists = db.execute(
                "SELECT p.name, COUNT(e.trackId) FROM playlists p"
                " LEFT JOIN playlist_entries e ON e.playlistId = p.id GROUP BY p.id ORDER BY p.id"
            ).fetchall()
            plays, last = db.execute("SELECT COUNT(*), MAX(playedAt) FROM play_history").fetchone()
        finally:
            db.close()
    lines = ["Playlists:"]
    lines += [f"  {name}: {count} track{'' if count == 1 else 's'}" for name, count in playlists]
    history = f"Play history: {plays} play{'' if plays == 1 else 's'}"
    if last is not None:
        played = datetime.fromtimestamp(last / 1000, tz).strftime("%Y-%m-%d %H:%M")
        history += f", last {played}"
    lines.append(history)
    return lines
