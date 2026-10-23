"""Finding and running adb, shared by the commands that talk to the phone."""

import os
import subprocess
from pathlib import Path
from typing import Protocol

PACKAGE = "io.github.pranavm716.vgmixr"


def default_adb() -> Path:
    """adb isn't on PATH on this machine, so the default is the Android SDK's copy."""
    return (
        Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
    )


class BytesRunner(Protocol):
    """Runs a command with optional stdin, returning its raw output (files are binary)."""

    def __call__(
        self, args: list[str], input: bytes | None = None
    ) -> "subprocess.CompletedProcess[bytes]": ...


def run_bytes(args: list[str], input: bytes | None = None) -> "subprocess.CompletedProcess[bytes]":
    return subprocess.run(args, input=input, capture_output=True, check=False)
