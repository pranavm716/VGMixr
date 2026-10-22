"""Re-checking a reported video the way the app sees it (docs/broken.md, Broken tab)."""

import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import pytest

from vgmixr_pipeline.broken.recheck import (
    CLIENT,
    AppCheck,
    AppChecks,
    CheckFailed,
    classify,
    youtube_message,
)
from vgmixr_pipeline.catalog.youtube import YouTubeBlocked

REFUSED = "saDO1GcGnRs"
FINE = "OKaeA81-Ap0"
GONE = "vTaiktjnkwY"

# Trimmed from yt-dlp's visionos answers for real videos (probed on 2026-10-22).
REFUSED_INFO: dict[str, Any] = {
    "title": "Battle! Primal Reversion  - Pokémon Omega Ruby & Alpha Sapphire",
    "availability": "public",
    "formats": [{"format_id": "sb0", "acodec": "none", "url": "https://i.ytimg.com/sb/x"}],
}
FINE_INFO: dict[str, Any] = {
    "title": "Pokémon Omega Ruby & Alpha Sapphire - Title Screen",
    "availability": "public",
    "formats": [
        {"format_id": "sb0", "acodec": "none", "url": "https://i.ytimg.com/sb/x"},
        {"format_id": "251", "acodec": "opus", "url": "https://rr1.googlevideo.com/x"},
    ],
}
GONE_INFO: dict[str, Any] = {
    "title": f"youtube video #{GONE}",
    "availability": None,
    "formats": [],
}


def test_a_video_with_audio_plays_in_the_app() -> None:
    assert classify(FINE_INFO, []) == ("plays", FINE_INFO["title"], None)


def test_a_known_video_without_audio_is_refused_to_the_app() -> None:
    assert classify(REFUSED_INFO, ["This video is not available"]) == (
        "refused",
        REFUSED_INFO["title"],
        "This video is not available",
    )


def test_an_age_restricted_video_is_refused_since_a_signed_in_browser_plays_it() -> None:
    info = {**REFUSED_INFO, "availability": "needs_auth"}

    assert classify(info, ["Sign in to confirm your age"])[0] == "refused"


def test_audio_formats_without_a_url_dont_count() -> None:
    info = {**FINE_INFO, "formats": [{"format_id": "251", "acodec": "opus"}]}

    assert classify(info, [])[0] == "refused"


def test_an_unknown_video_is_gone_for_everyone() -> None:
    assert classify(GONE_INFO, ["Video unavailable"]) == ("gone", None, "Video unavailable")


def test_an_error_answer_is_gone_for_everyone() -> None:
    message = "Private video. Sign in if you've been granted access to this video"

    assert classify(None, [message]) == ("gone", None, message)


def test_a_private_video_is_gone_even_with_a_title() -> None:
    info = {**REFUSED_INFO, "availability": "private"}

    assert classify(info, [])[0] == "gone"


@pytest.mark.parametrize(
    ("text", "message"),
    [
        ("[youtube] This video is not available", "This video is not available"),
        (f"[youtube] {GONE}: Video unavailable", "Video unavailable"),
        (f"ERROR: [youtube] {GONE}: Private video", "Private video"),
        ("Only images are available for download. use --list-formats to see them", None),
        ("Requested format is not available", None),
    ],
)
def test_youtube_messages_are_told_from_yt_dlps_own(text: str, message: str | None) -> None:
    assert youtube_message(text) == message


class FakeProbe:
    def __init__(self, answers: dict[str, Any]) -> None:
        self.answers = answers
        self.calls: list[str] = []

    def __call__(self, video_id: str) -> tuple[dict[str, Any] | None, list[str]]:
        self.calls.append(video_id)
        answer = self.answers[video_id]
        if isinstance(answer, Exception):
            raise answer
        result: tuple[dict[str, Any] | None, list[str]] = answer
        return result


NOW = datetime(2026, 10, 22, 9, 30, tzinfo=UTC)


def checks(tmp_path: Path, probe: FakeProbe) -> AppChecks:
    return AppChecks(tmp_path / "app-check", probe, clock=lambda: NOW, version=lambda: "2026.08.19")


def test_a_fresh_check_asks_youtube_and_is_cached(tmp_path: Path) -> None:
    probe = FakeProbe({REFUSED: (REFUSED_INFO, ["This video is not available"])})
    app_checks = checks(tmp_path, probe)

    assert app_checks.cached(REFUSED) is None
    check = app_checks.fresh(REFUSED)

    assert check == AppCheck(
        video_id=REFUSED,
        verdict="refused",
        title=REFUSED_INFO["title"],
        detail="This video is not available",
        checked_at=NOW,
        client=CLIENT,
        yt_dlp="2026.08.19",
    )
    assert app_checks.cached(REFUSED) == check
    saved = json.loads((tmp_path / "app-check" / f"{REFUSED}.json").read_text(encoding="utf-8"))
    assert saved["checkedAt"] == "2026-10-22T09:30:00Z"
    assert saved["client"] == "visionos"
    # Read back by a new instance, as after a restart.
    assert checks(tmp_path, FakeProbe({})).cached(REFUSED) == check


def test_a_fresh_check_always_asks_again_and_overwrites(tmp_path: Path) -> None:
    probe = FakeProbe({FINE: (REFUSED_INFO, [])})
    app_checks = checks(tmp_path, probe)
    app_checks.fresh(FINE)
    probe.answers[FINE] = (FINE_INFO, [])

    assert app_checks.fresh(FINE).verdict == "plays"
    assert probe.calls == [FINE, FINE]
    assert app_checks.cached(FINE) is not None
    assert app_checks.cached(FINE).verdict == "plays"  # type: ignore[union-attr]


@pytest.mark.parametrize(
    "failure", [YouTubeBlocked("Sign in to confirm you're not a bot"), CheckFailed("no network")]
)
def test_failures_are_never_cached(tmp_path: Path, failure: Exception) -> None:
    app_checks = checks(tmp_path, FakeProbe({GONE: failure}))

    with pytest.raises(type(failure)):
        app_checks.fresh(GONE)
    assert app_checks.cached(GONE) is None


def test_a_damaged_cache_file_reads_as_unchecked(tmp_path: Path) -> None:
    (tmp_path / "app-check").mkdir()
    (tmp_path / "app-check" / f"{GONE}.json").write_text("{", encoding="utf-8")

    assert checks(tmp_path, FakeProbe({})).cached(GONE) is None


def test_only_video_ids_name_cache_files(tmp_path: Path) -> None:
    app_checks = checks(tmp_path, FakeProbe({}))

    with pytest.raises(ValueError, match="not a YouTube video id"):
        app_checks.cached("../draft")
