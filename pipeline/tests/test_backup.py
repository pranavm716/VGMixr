"""Backups of the app's database over adb (roadmap step 12d)."""

import hashlib
import sqlite3
import subprocess
from datetime import UTC, datetime
from pathlib import Path

import pytest

from vgmixr_pipeline.adb import PACKAGE
from vgmixr_pipeline.backup import (
    DB,
    BackupError,
    backup_name,
    files_to_delete,
    is_playing,
    parse_ls,
    parse_sha256,
    pull,
    push,
    summarize,
    validate_backup,
)

ADB = Path("C:/Sdk/platform-tools/adb.exe")
NOW = datetime(2026, 10, 23, 14, 12, 5)

# Trimmed from the phone's `dumpsys media_session`: VGMixr paused, then another player's session.
DUMPSYS = """\
  Sessions Stack - have 2 sessions:
    androidx.media3.session.id. io.github.pranavm716.vgmixr/androidx.media3.session.id./75
      ownerPid=6206, ownerUid=10028, userId=0
      package=io.github.pranavm716.vgmixr
      active=true
      state=PlaybackState {state=PAUSED(2), position=10114, speed=0.0, active item id=0, error=null}
      metadata: size=10, description=Santalune City, X / Y, null
    ShuttleMediaSession com.simplecityapps.shuttle/ShuttleMediaSession/27 (userId=0)
      ownerPid=31325, ownerUid=10269, userId=0
      package=com.simplecityapps.shuttle
      active=false
      state=PlaybackState {state=PAUSED(2), position=2261, speed=1.0, active item id=7, error=null}
"""


def dumpsys(vgmixr: str = "PAUSED(2)", other: str = "PAUSED(2)") -> str:
    head, tail = DUMPSYS.split("    ShuttleMediaSession")
    return (
        head.replace("PAUSED(2)", vgmixr)
        + "    ShuttleMediaSession"
        + tail.replace("PAUSED(2)", other)
    )


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class FakePhone:
    """Stands in for adb talking to the phone. Like the real `adb exec-out`, run-as and shell
    errors come back on stdout with exit code 0."""

    def __init__(
        self,
        files: dict[str, bytes] | None = None,
        playing: bool = False,
        debuggable: bool = True,
    ) -> None:
        self.files = (
            files
            if files is not None
            else {DB: b"db" * 100, f"{DB}-wal": b"wal\r\n" * 50, f"{DB}-shm": b"\x00shm\n" * 9}
        )
        self.playing = playing
        self.debuggable = debuggable
        self.mangled: set[str] = set()
        self.mangled_writes: set[str] = set()
        # Like the real exec-in, which returns before the phone's `cat` is done: a written file
        # reads as empty for its first [lag] hash checks.
        self.lag = 0
        self.landing: dict[str, tuple[bytes, int]] = {}
        self.sleeps: list[float] = []
        # File modes: the app's own files are 660; a file written through run-as sh comes out 666.
        self.modes = {name: "660" for name in self.files}
        self.calls: list[list[str]] = []

    def sleep(self, seconds: float) -> None:
        self.sleeps.append(seconds)

    def ok(self, args: list[str], out: bytes) -> subprocess.CompletedProcess[bytes]:
        return subprocess.CompletedProcess(args, 0, out, b"")

    def __call__(
        self, args: list[str], input: bytes | None = None
    ) -> subprocess.CompletedProcess[bytes]:
        assert args[0] == str(ADB)
        cmd = args[1:]
        self.calls.append(cmd)
        if cmd == ["shell", "dumpsys", "media_session"]:
            return self.ok(args, dumpsys("PLAYING(3)" if self.playing else "PAUSED(2)").encode())
        if cmd == ["shell", "am", "force-stop", PACKAGE]:
            return self.ok(args, b"")
        if cmd[0] == "exec-in":
            # One string, as on Windows: adb drops the quotes of separate arguments.
            prefix, suffix = f"run-as {PACKAGE} sh -c 'cat > databases/", "'"
            assert len(cmd) == 2 and cmd[1].startswith(prefix) and cmd[1].endswith(suffix), cmd
            assert input is not None
            name = cmd[1].removeprefix(prefix).removesuffix(suffix)
            damaged = name.removesuffix(".part") in self.mangled_writes
            data = input[:-1] if damaged else input
            self.modes[name] = "666"
            if self.lag:
                self.files[name] = b""
                self.landing[name] = (data, self.lag)
            else:
                self.files[name] = data
            return self.ok(args, b"")
        assert cmd[:3] == ["exec-out", "run-as", PACKAGE], cmd
        if not self.debuggable:
            return self.ok(args, f"run-as: package not debuggable: {PACKAGE}\n".encode())
        match cmd[3:]:
            case ["ls", "databases"]:
                return self.ok(args, ("  ".join(sorted(self.files)) + "\n").encode())
            case ["cat", path]:
                name = path.removeprefix("databases/")
                if name not in self.files:
                    return self.ok(args, f"cat: {path}: No such file or directory\n".encode())
                data = self.files[name]
                return self.ok(args, data.replace(b"\n", b"\r\n") if name in self.mangled else data)
            case ["sha256sum", path]:
                name = path.removeprefix("databases/")
                if name not in self.files:
                    return self.ok(args, f"sha256sum: {path}: No such file or directory\n".encode())
                if name in self.landing:
                    data, checks = self.landing.pop(name)
                    if checks > 1:
                        self.landing[name] = (data, checks - 1)
                    else:
                        self.files[name] = data
                return self.ok(args, f"{sha(self.files[name])}  {path}\n".encode())
            case ["mv", src, dst]:
                source = src.removeprefix("databases/")
                if source not in self.files:
                    return self.ok(args, f"mv: bad '{src}': No such file or directory\n".encode())
                target = dst.removeprefix("databases/")
                self.files[target] = self.files.pop(source)
                self.modes[target] = self.modes.pop(source)
                return self.ok(args, b"")
            case ["chmod", mode, *paths]:
                for path in paths:
                    name = path.removeprefix("databases/")
                    if name not in self.files:
                        return self.ok(args, f"chmod: {path}: No such file\n".encode())
                    self.modes[name] = mode
                return self.ok(args, b"")
            case ["rm", "-f", *paths]:
                for path in paths:
                    self.files.pop(path.removeprefix("databases/"), None)
                return self.ok(args, b"")
        raise AssertionError(f"unexpected adb call {cmd}")


# backup_name


def test_a_backup_is_named_by_local_time_without_colons() -> None:
    assert backup_name(NOW, set()) == "2026-10-23_141205"


def test_a_second_backup_in_the_same_second_gets_a_suffix() -> None:
    taken = {"2026-10-23_141205", "2026-10-23_141205_2"}

    assert backup_name(NOW, taken) == "2026-10-23_141205_3"


# parse_ls


def test_ls_lists_only_the_database_files() -> None:
    text = "vgmixr.db  vgmixr.db-shm  vgmixr.db-wal  other.db\n"

    assert parse_ls(text) == {DB, f"{DB}-shm", f"{DB}-wal"}


def test_ls_of_a_database_without_wal() -> None:
    assert parse_ls("vgmixr.db\n") == {DB}


def test_ls_of_an_app_that_is_not_debuggable_fails() -> None:
    with pytest.raises(BackupError, match="debug build"):
        parse_ls(f"run-as: package not debuggable: {PACKAGE}\n")


def test_ls_of_an_app_without_databases_fails() -> None:
    with pytest.raises(BackupError, match="No such file"):
        parse_ls("ls: databases: No such file or directory\n")


# parse_sha256


def test_sha256sum_output_gives_the_hash() -> None:
    digest = "45d4d0f4b907151cb2a9877ab69d8e02762f2d10b97dd19b692ffdad1d599bef"

    assert parse_sha256(f"{digest}  databases/vgmixr.db\n", DB) == digest


def test_sha256sum_of_a_missing_file_gives_none() -> None:
    text = "sha256sum: databases/vgmixr.db-wal: No such file or directory\n"

    assert parse_sha256(text, f"{DB}-wal") is None


# is_playing


def test_a_paused_session_is_not_playing() -> None:
    assert not is_playing(dumpsys())


def test_a_playing_session_is_playing() -> None:
    assert is_playing(dumpsys(vgmixr="PLAYING(3)"))


def test_a_buffering_session_counts_as_playing() -> None:
    assert is_playing(dumpsys(vgmixr="BUFFERING(6)"))


def test_another_app_playing_does_not_count() -> None:
    assert not is_playing(dumpsys(other="PLAYING(3)"))


def test_no_vgmixr_session_is_not_playing() -> None:
    assert not is_playing("  Sessions Stack - have 0 sessions:\n")


# pull


def test_pull_copies_the_three_files_into_a_dated_folder(tmp_path: Path) -> None:
    phone = FakePhone()

    folder = pull(ADB, tmp_path, phone, NOW)

    assert folder == tmp_path / "2026-10-23_141205"
    assert {p.name: p.read_bytes() for p in folder.iterdir()} == phone.files
    assert [p.name for p in tmp_path.iterdir()] == ["2026-10-23_141205"]


def test_pull_stops_the_app_before_reading(tmp_path: Path) -> None:
    phone = FakePhone()

    pull(ADB, tmp_path, phone, NOW)

    assert phone.calls[0] == ["shell", "dumpsys", "media_session"]
    assert phone.calls[1] == ["shell", "am", "force-stop", PACKAGE]


def test_pull_reads_with_run_as_cat(tmp_path: Path) -> None:
    phone = FakePhone()

    pull(ADB, tmp_path, phone, NOW)

    assert ["exec-out", "run-as", PACKAGE, "cat", "databases/vgmixr.db"] in phone.calls


def test_pull_without_wal_copies_the_database_alone(tmp_path: Path) -> None:
    phone = FakePhone(files={DB: b"db"})

    folder = pull(ADB, tmp_path, phone, NOW)

    assert [p.name for p in folder.iterdir()] == [DB]


def test_pull_refuses_while_vgmixr_plays(tmp_path: Path) -> None:
    phone = FakePhone(playing=True)

    with pytest.raises(BackupError, match="playing"):
        pull(ADB, tmp_path, phone, NOW)

    assert ["shell", "am", "force-stop", PACKAGE] not in phone.calls
    assert list(tmp_path.iterdir()) == []


def test_pull_fails_without_a_database_on_the_phone(tmp_path: Path) -> None:
    with pytest.raises(BackupError, match="vgmixr.db"):
        pull(ADB, tmp_path, FakePhone(files={}), NOW)

    assert list(tmp_path.iterdir()) == []


def test_pull_fails_for_a_release_build(tmp_path: Path) -> None:
    with pytest.raises(BackupError, match="debug build"):
        pull(ADB, tmp_path, FakePhone(debuggable=False), NOW)

    assert list(tmp_path.iterdir()) == []


def test_a_copy_that_differs_from_the_phone_fails_and_leaves_nothing(tmp_path: Path) -> None:
    phone = FakePhone()
    phone.mangled = {f"{DB}-wal"}

    with pytest.raises(BackupError, match="vgmixr.db-wal"):
        pull(ADB, tmp_path, phone, NOW)

    assert list(tmp_path.iterdir()) == []


def test_pull_names_a_missing_adb(tmp_path: Path) -> None:
    def missing(args: list[str], input: bytes | None = None) -> subprocess.CompletedProcess[bytes]:
        raise FileNotFoundError(args[0])

    with pytest.raises(BackupError, match="adb not found"):
        pull(ADB, tmp_path, missing, NOW)


def test_a_failing_adb_command_reports_its_error(tmp_path: Path) -> None:
    def offline(args: list[str], input: bytes | None = None) -> subprocess.CompletedProcess[bytes]:
        return subprocess.CompletedProcess(args, 1, b"", b"adb: no devices/emulators found")

    with pytest.raises(BackupError, match="no devices"):
        pull(ADB, tmp_path, offline, NOW)


# summarize


SCHEMA = """
CREATE TABLE playlists (id INTEGER PRIMARY KEY, name TEXT NOT NULL, createdAt INTEGER NOT NULL);
CREATE TABLE playlist_entries (playlistId INTEGER, trackId TEXT, addedAt INTEGER,
    PRIMARY KEY (playlistId, trackId));
CREATE TABLE play_history (id INTEGER PRIMARY KEY, trackId TEXT, playedAt INTEGER);
"""


def make_backup(folder: Path) -> None:
    """A backup whose newest rows are still in its -wal file, as the app's often are."""
    folder.mkdir()
    db = sqlite3.connect(folder / DB)
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("PRAGMA wal_autocheckpoint=0")
    db.executescript(SCHEMA)
    db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    db.executemany(
        "INSERT INTO playlists VALUES (?, ?, 0)", [(1, "Favorites"), (2, "Hype"), (3, "Empty")]
    )
    db.executemany(
        "INSERT INTO playlist_entries VALUES (?, ?, 0)",
        [(1, "rse-route-101"), (2, "rse-route-101"), (2, "dppt-route-209")],
    )
    db.executemany(
        "INSERT INTO play_history (trackId, playedAt) VALUES (?, ?)",
        [("rse-route-101", 1_761_000_000_000), ("dppt-route-209", 1_761_177_600_000)],
    )
    db.commit()
    # Copy the files while the connection is open: closing it would checkpoint the WAL away.
    copies = {name: (folder / name).read_bytes() for name in (DB, f"{DB}-wal", f"{DB}-shm")}
    db.close()
    for name, data in copies.items():
        (folder / name).write_bytes(data)


def test_summarize_lists_playlists_and_history_including_the_wal(tmp_path: Path) -> None:
    folder = tmp_path / "backup"
    make_backup(folder)

    assert summarize(folder, UTC) == [
        "Playlists:",
        "  Favorites: 1 track",
        "  Hype: 2 tracks",
        "  Empty: 0 tracks",
        "Play history: 2 plays, last 2025-10-23 00:00",
    ]


def test_summarize_leaves_the_backup_untouched(tmp_path: Path) -> None:
    folder = tmp_path / "backup"
    make_backup(folder)
    before = {p.name: p.read_bytes() for p in folder.iterdir()}

    summarize(folder, UTC)

    assert {p.name: p.read_bytes() for p in folder.iterdir()} == before


# validate_backup


def write_backup(folder: Path, files: dict[str, bytes]) -> Path:
    folder.mkdir()
    for name, data in files.items():
        (folder / name).write_bytes(data)
    return folder


BACKUP = {DB: b"old db", f"{DB}-wal": b"old\nwal", f"{DB}-shm": b"old shm"}


def test_a_backup_folder_lists_its_database_files(tmp_path: Path) -> None:
    folder = write_backup(tmp_path / "b", {**BACKUP, "notes.txt": b"x"})

    assert validate_backup(folder) == set(BACKUP)


def test_a_folder_without_the_database_is_refused(tmp_path: Path) -> None:
    folder = write_backup(tmp_path / "b", {f"{DB}-wal": b"wal"})

    with pytest.raises(BackupError, match="no vgmixr.db"):
        validate_backup(folder)


def test_a_missing_folder_is_refused(tmp_path: Path) -> None:
    with pytest.raises(BackupError, match="no vgmixr.db"):
        validate_backup(tmp_path / "nope")


def test_a_shm_without_its_wal_is_refused(tmp_path: Path) -> None:
    folder = write_backup(tmp_path / "b", {DB: b"db", f"{DB}-shm": b"shm"})

    with pytest.raises(BackupError, match="-shm without"):
        validate_backup(folder)


# files_to_delete


def test_phone_files_the_backup_lacks_are_deleted() -> None:
    phone = {DB, f"{DB}-wal", f"{DB}-shm"}

    assert files_to_delete({DB}, phone) == [f"{DB}-shm", f"{DB}-wal"]


def test_nothing_is_deleted_when_the_backup_has_every_file() -> None:
    assert files_to_delete({DB, f"{DB}-wal", f"{DB}-shm"}, {DB, f"{DB}-wal"}) == []


# push


class Confirm:
    """Records what push asked, answers [answer], and marks the moment in the phone's calls."""

    def __init__(self, phone: FakePhone, answer: bool = True) -> None:
        self.phone = phone
        self.answer = answer
        self.asked: list[str] = []

    def __call__(self, lines: list[str]) -> bool:
        self.asked = lines
        self.phone.calls.append(["<confirm>"])
        return self.answer


def test_push_writes_the_backup_over_the_phone(tmp_path: Path) -> None:
    folder = write_backup(tmp_path / "b", BACKUP)
    phone = FakePhone()

    push(ADB, folder, tmp_path / "backups", phone, NOW, Confirm(phone), sleep=phone.sleep)

    assert phone.files == BACKUP


def test_push_writes_with_exec_in_as_one_command_string(tmp_path: Path) -> None:
    folder = write_backup(tmp_path / "b", BACKUP)
    phone = FakePhone()

    push(ADB, folder, tmp_path / "backups", phone, NOW, Confirm(phone), sleep=phone.sleep)

    assert ["exec-in", f"run-as {PACKAGE} sh -c 'cat > databases/vgmixr.db.part'"] in phone.calls


def test_push_pulls_the_phone_first_as_a_rollback_point(tmp_path: Path) -> None:
    folder = write_backup(tmp_path / "b", BACKUP)
    phone = FakePhone()
    before = dict(phone.files)

    rollback = push(
        ADB, folder, tmp_path / "backups", phone, NOW, Confirm(phone), sleep=phone.sleep
    )

    assert rollback == tmp_path / "backups" / "2026-10-23_141205"
    assert {p.name: p.read_bytes() for p in rollback.iterdir()} == before


def test_push_asks_after_the_rollback_and_stops_the_app_again_before_writing(
    tmp_path: Path,
) -> None:
    folder = write_backup(tmp_path / "b", BACKUP)
    phone = FakePhone()

    push(ADB, folder, tmp_path / "backups", phone, NOW, Confirm(phone), sleep=phone.sleep)

    asked = phone.calls.index(["<confirm>"])
    first_write = next(i for i, c in enumerate(phone.calls) if c[0] == "exec-in")
    last_read = max(i for i, c in enumerate(phone.calls) if c[3:4] == ["cat"])
    assert last_read < asked < first_write
    after = phone.calls[asked:first_write]
    assert ["shell", "dumpsys", "media_session"] in after
    assert ["shell", "am", "force-stop", PACKAGE] in after


def test_push_tells_what_it_will_write_and_delete(tmp_path: Path) -> None:
    folder = write_backup(tmp_path / "b", {DB: b"db"})
    phone = FakePhone()
    confirm = Confirm(phone)

    push(ADB, folder, tmp_path / "backups", phone, NOW, confirm, sleep=phone.sleep)

    text = "\n".join(confirm.asked)
    assert str(folder) in text
    assert "Write: vgmixr.db" in text
    assert "Delete: vgmixr.db-shm, vgmixr.db-wal" in text
    assert str(tmp_path / "backups" / "2026-10-23_141205") in text


def test_push_deletes_a_wal_and_shm_the_backup_lacks(tmp_path: Path) -> None:
    folder = write_backup(tmp_path / "b", {DB: b"db"})
    phone = FakePhone()

    push(ADB, folder, tmp_path / "backups", phone, NOW, Confirm(phone), sleep=phone.sleep)

    assert phone.files == {DB: b"db"}


def test_declining_leaves_the_phone_unchanged(tmp_path: Path) -> None:
    folder = write_backup(tmp_path / "b", BACKUP)
    phone = FakePhone()
    before = dict(phone.files)

    with pytest.raises(BackupError, match="unchanged"):
        push(
            ADB,
            folder,
            tmp_path / "backups",
            phone,
            NOW,
            Confirm(phone, answer=False),
            sleep=phone.sleep,
        )

    assert phone.files == before
    assert not any(c[0] == "exec-in" for c in phone.calls)


def test_push_refuses_a_folder_without_the_database_before_touching_the_phone(
    tmp_path: Path,
) -> None:
    folder = write_backup(tmp_path / "b", {f"{DB}-wal": b"wal"})
    phone = FakePhone()

    with pytest.raises(BackupError):
        push(ADB, folder, tmp_path / "backups", phone, NOW, Confirm(phone), sleep=phone.sleep)

    assert phone.calls == []


def test_push_refuses_while_vgmixr_plays(tmp_path: Path) -> None:
    folder = write_backup(tmp_path / "b", BACKUP)
    phone = FakePhone(playing=True)
    before = dict(phone.files)

    with pytest.raises(BackupError, match="playing"):
        push(ADB, folder, tmp_path / "backups", phone, NOW, Confirm(phone), sleep=phone.sleep)

    assert phone.files == before
    assert ["shell", "am", "force-stop", PACKAGE] not in phone.calls


def test_a_write_that_arrives_damaged_leaves_the_phone_files_alone(tmp_path: Path) -> None:
    folder = write_backup(tmp_path / "b", BACKUP)
    phone = FakePhone()
    phone.mangled_writes = {f"{DB}-wal"}
    before = dict(phone.files)

    with pytest.raises(BackupError, match="vgmixr.db-wal"):
        push(ADB, folder, tmp_path / "backups", phone, NOW, Confirm(phone), sleep=phone.sleep)

    assert phone.files == before


def test_push_waits_for_a_write_still_landing_on_the_phone(tmp_path: Path) -> None:
    folder = write_backup(tmp_path / "b", BACKUP)
    phone = FakePhone()
    phone.lag = 3

    push(ADB, folder, tmp_path / "backups", phone, NOW, Confirm(phone), sleep=phone.sleep)

    assert phone.files == BACKUP
    assert phone.sleeps


def test_a_write_that_never_matches_gives_up_within_seconds(tmp_path: Path) -> None:
    folder = write_backup(tmp_path / "b", BACKUP)
    phone = FakePhone()
    phone.mangled_writes = {DB}

    with pytest.raises(BackupError, match="damaged"):
        push(ADB, folder, tmp_path / "backups", phone, NOW, Confirm(phone), sleep=phone.sleep)

    assert 0 < sum(phone.sleeps) <= 10


def test_pushed_files_get_the_app_files_mode_back(tmp_path: Path) -> None:
    folder = write_backup(tmp_path / "b", BACKUP)
    phone = FakePhone()

    push(ADB, folder, tmp_path / "backups", phone, NOW, Confirm(phone), sleep=phone.sleep)

    assert {name: phone.modes[name] for name in phone.files} == dict.fromkeys(BACKUP, "660")
