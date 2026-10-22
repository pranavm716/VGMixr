"""Broken sources reported by the app: broken.json pulled over adb (docs/broken.md)."""

import subprocess
from datetime import UTC, datetime
from pathlib import Path

import pytest
from fixture_files import FIXTURES_DIR
from pydantic import ValidationError

from vgmixr_pipeline.broken import (
    REMOTE_PATH,
    BrokenFile,
    Pulled,
    PullError,
    parse_broken,
    pull_broken,
    read_pulled,
    summarize,
)

FIXTURE = (FIXTURES_DIR / "broken.json").read_text(encoding="utf-8")
ADB = Path("C:/Sdk/platform-tools/adb.exe")


class FakeAdb:
    """Stands in for adb: `pull` copies [content] to the local path, or fails with [stderr]."""

    def __init__(self, content: str | None = FIXTURE, stderr: str = "") -> None:
        self.content = content
        self.stderr = stderr
        self.calls: list[list[str]] = []

    def __call__(self, args: list[str]) -> subprocess.CompletedProcess[str]:
        self.calls.append(args)
        if self.content is None:
            return subprocess.CompletedProcess(args, 1, "", self.stderr)
        Path(args[-1]).write_text(self.content, encoding="utf-8")
        return subprocess.CompletedProcess(args, 0, "1 file pulled", "")


def test_the_shared_fixture_parses() -> None:
    broken = parse_broken(FIXTURE)

    assert broken.version == 1
    first, second = broken.marks
    assert (first.track_id, first.video_id, first.reason) == (
        "oras-battle-elite-four",
        "7DbGoUJkNfU",
        "no-audio",
    )
    assert first.detail == "no playable audio stream"
    assert first.marked_at == datetime(2026, 10, 21, 7, 23, 44, 638000, tzinfo=UTC)
    assert first.catalog_generated_at == datetime(2026, 10, 21, 3, 12, 40, tzinfo=UTC)
    assert (second.reason, second.catalog_generated_at) == ("unavailable", None)


@pytest.mark.parametrize(
    "change",
    [
        ('"reason": "no-audio"', '"reason": "refused"'),
        ('"version": 1', '"version": 2'),
        ('"videoId": "7DbGoUJkNfU",', '"videoId": "7DbGoUJkNfU", "extra": 1,'),
        ('"markedAt": "2026-10-21T07:23:44.638Z"', '"markedAt": 5'),
    ],
)
def test_a_file_the_format_does_not_allow_is_rejected(change: tuple[str, str]) -> None:
    with pytest.raises(ValidationError):
        parse_broken(FIXTURE.replace(*change))


def test_an_empty_file_has_no_marks() -> None:
    assert parse_broken('{\n  "version": 1,\n  "marks": []\n}\n').marks == []


def test_pull_copies_the_phone_file_into_out(tmp_path: Path) -> None:
    adb = FakeAdb()

    broken = pull_broken(ADB, tmp_path, adb)

    assert len(broken.marks) == 2
    assert (tmp_path / "broken.json").read_text(encoding="utf-8") == FIXTURE
    assert adb.calls[0][:3] == [str(ADB), "pull", REMOTE_PATH]
    assert not list(tmp_path.glob("*.part"))


def test_a_file_missing_on_the_phone_says_to_open_the_app(tmp_path: Path) -> None:
    adb = FakeAdb(
        None,
        stderr=f"adb: error: failed to stat remote object '{REMOTE_PATH}': No such file",
    )

    with pytest.raises(PullError, match="open VGMixr once"):
        pull_broken(ADB, tmp_path, adb)
    assert not (tmp_path / "broken.json").exists()


def test_no_phone_is_an_error_that_keeps_the_last_pull(tmp_path: Path) -> None:
    (tmp_path / "broken.json").write_text("last pull", encoding="utf-8")

    with pytest.raises(PullError, match="no devices"):
        pull_broken(ADB, tmp_path, FakeAdb(None, stderr="adb: no devices/emulators found"))
    assert (tmp_path / "broken.json").read_text(encoding="utf-8") == "last pull"


def test_an_invalid_file_on_the_phone_keeps_the_last_pull(tmp_path: Path) -> None:
    (tmp_path / "broken.json").write_text("last pull", encoding="utf-8")

    with pytest.raises(PullError, match="not a valid broken.json"):
        pull_broken(ADB, tmp_path, FakeAdb("{}"))
    assert (tmp_path / "broken.json").read_text(encoding="utf-8") == "last pull"
    assert not list(tmp_path.glob("*.part"))


def test_a_missing_adb_is_named(tmp_path: Path) -> None:
    def no_adb(args: list[str]) -> subprocess.CompletedProcess[str]:
        raise FileNotFoundError(args[0])

    with pytest.raises(PullError, match="adb not found"):
        pull_broken(ADB, tmp_path, no_adb)


def test_the_summary_counts_marks_by_family_and_reason() -> None:
    broken: BrokenFile = parse_broken(FIXTURE)
    track_families = {"oras-battle-elite-four": "oras"}

    assert summarize(broken, track_families) == [
        "2 broken videos:",
        "  oras: 1 (no-audio 1)",
        "  not in the catalog: 1 (unavailable 1)",
    ]


def test_reading_the_pulled_file(tmp_path: Path) -> None:
    (tmp_path / "broken.json").write_text(FIXTURE, encoding="utf-8")

    pulled = read_pulled(tmp_path)

    assert pulled.error is None
    assert pulled.broken == parse_broken(FIXTURE)
    assert pulled.pulled_at is not None
    assert abs((datetime.now(UTC) - pulled.pulled_at).total_seconds()) < 60


def test_no_pulled_file_reads_as_nothing_pulled(tmp_path: Path) -> None:
    assert read_pulled(tmp_path) == Pulled(broken=None, error=None, pulled_at=None)


def test_an_invalid_pulled_file_says_why(tmp_path: Path) -> None:
    (tmp_path / "broken.json").write_text('{"version": 2, "marks": []}', encoding="utf-8")

    pulled = read_pulled(tmp_path)

    assert pulled.broken is None
    assert pulled.error is not None
    assert "version" in pulled.error
