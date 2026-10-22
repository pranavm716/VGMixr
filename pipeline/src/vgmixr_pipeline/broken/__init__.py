"""Broken sources the app reported: its broken.json, pulled over adb (format in docs/broken.md).

The app marks a stream video broken when it resolves to no playable audio (YouTube refusing the
video to the extractor's client) or is unavailable, and mirrors its marks to broken.json in its
external files folder. The review tool's Broken tab (roadmap step 10b) reads the pulled copy.
"""

import subprocess
from collections import Counter
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, ValidationError
from pydantic.alias_generators import to_camel

PACKAGE = "io.github.pranavm716.vgmixr"
REMOTE_PATH = f"/sdcard/Android/data/{PACKAGE}/files/broken.json"
FILE_NAME = "broken.json"

Reason = Literal["unavailable", "no-audio"]


class _Model(BaseModel):
    # Strict, as the manifest validator: the app writes this file, so anything else is a bug.
    model_config = ConfigDict(strict=True, extra="forbid", frozen=True, alias_generator=to_camel)


class BrokenMark(_Model):
    track_id: str
    video_id: str
    reason: Reason
    detail: str | None = None
    marked_at: datetime
    catalog_generated_at: datetime | None = None


class BrokenFile(_Model):
    version: Literal[1]
    marks: list[BrokenMark]


class PullError(Exception):
    """broken.json could not be pulled; the last pulled copy is kept."""


Runner = Callable[[list[str]], "subprocess.CompletedProcess[str]"]


def parse_broken(text: str) -> BrokenFile:
    return BrokenFile.model_validate_json(text)


@dataclass(frozen=True)
class Pulled:
    """The last pulled broken.json: its marks, or why it can't be read; both None when nothing
    was pulled yet. `pulled_at` is the file's time."""

    broken: BrokenFile | None
    error: str | None
    pulled_at: datetime | None


def read_pulled(out_dir: Path) -> Pulled:
    path = out_dir / FILE_NAME
    try:
        text = path.read_text(encoding="utf-8")
        pulled_at = datetime.fromtimestamp(path.stat().st_mtime, UTC)
    except FileNotFoundError:
        return Pulled(None, None, None)
    try:
        return Pulled(parse_broken(text), None, pulled_at)
    except ValidationError as e:
        return Pulled(None, f"{path} is not a valid broken.json:\n{e}", pulled_at)


def run_adb(args: list[str]) -> "subprocess.CompletedProcess[str]":
    return subprocess.run(args, capture_output=True, text=True, check=False)


def pull_broken(adb: Path, out_dir: Path, run: Runner = run_adb) -> BrokenFile:
    """Pulls the phone's broken.json into [out_dir]; only a valid file replaces the last pull."""
    out_dir.mkdir(parents=True, exist_ok=True)
    part = out_dir / f"{FILE_NAME}.part"
    try:
        result = run([str(adb), "pull", REMOTE_PATH, str(part)])
    except FileNotFoundError as e:
        raise PullError(f"adb not found at {adb}; pass its path with --adb") from e
    if result.returncode != 0:
        message = (result.stderr or result.stdout).strip()
        if "No such file" in message:
            raise PullError(
                "No broken.json on the phone yet: open VGMixr once (it writes the file on start)"
            )
        raise PullError(f"adb pull failed: {message}")
    try:
        text = part.read_text(encoding="utf-8")
        broken = parse_broken(text)
    except ValidationError as e:
        raise PullError(f"The phone's file is not a valid broken.json:\n{e}") from e
    finally:
        part.unlink(missing_ok=True)
    (out_dir / FILE_NAME).write_text(text, encoding="utf-8")
    return broken


def summarize(broken: BrokenFile, track_families: dict[str, str]) -> list[str]:
    """Counts per family (by the catalog's tracks), largest first; tracks gone from it come last."""
    missing = "not in the catalog"
    by_family: dict[str, Counter[str]] = {}
    for mark in broken.marks:
        family = track_families.get(mark.track_id, missing)
        by_family.setdefault(family, Counter())[mark.reason] += 1
    order = sorted(by_family, key=lambda f: (f == missing, -sum(by_family[f].values()), f))
    lines = [f"{len(broken.marks)} broken video{'' if len(broken.marks) == 1 else 's'}:"]
    for family in order:
        reasons = by_family[family]
        detail = ", ".join(f"{reason} {count}" for reason, count in sorted(reasons.items()))
        lines.append(f"  {family}: {sum(reasons.values())} ({detail})")
    return lines
