"""Re-checks a reported video the way the app sees it: yt-dlp posing as NewPipe's client.

The app looks videos up with NewPipe's visionOS client (VISIONOS, id 101, version 1.02), over IPv4.
yt-dlp's `visionos` player client sends the same name, id and version, so one metadata request
(the watch page plus that client's player call) tells three cases apart:

- plays: the client gets audio formats, so the app's mark is stale (Try again on the phone).
- refused: no audio, but the watch page knows the video (a public or unlisted title). This is how
  YouTube's PO-token enforcement shows: a browser plays the video, the app's client is refused.
- gone: YouTube doesn't know the video any more (removed, private) and says why.

The bot check and network trouble are no verdict: they raise and are never cached. Results are
cached per video under the given folder; a fresh check always asks YouTube and overwrites it.
"""

import json
from collections.abc import Callable
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, ValidationError
from pydantic.alias_generators import to_camel

from vgmixr_pipeline.catalog.youtube import raise_if_blocked
from vgmixr_pipeline.manifest.validate import VIDEO_ID

CLIENT = "visionos"
Verdict = Literal["plays", "refused", "gone"]
# Availabilities a browser can play (age-restricted ones when signed in).
_PLAYABLE_IN_BROWSER = {"public", "unlisted", "needs_auth"}
# A lookup that failed on the way rather than one YouTube answered.
_NETWORK = "Unable to download"

# video id -> (yt-dlp's info dict, or None when YouTube answered with an error; YouTube's messages)
type Probe = Callable[[str], tuple[dict[str, Any] | None, list[str]]]


class CheckFailed(Exception):
    """The video couldn't be checked (network trouble); nothing is known about it."""


class AppCheck(BaseModel):
    model_config = ConfigDict(frozen=True, alias_generator=to_camel, populate_by_name=True)

    video_id: str
    verdict: Verdict
    title: str | None
    detail: str | None
    checked_at: datetime
    client: str
    yt_dlp: str


def youtube_message(text: str) -> str | None:
    """YouTube's own message in a yt-dlp warning or error ("[youtube] <id>: Video unavailable"),
    without the prefixes; None for yt-dlp's own remarks about formats."""
    text = text.removeprefix("ERROR: ").removeprefix("WARNING: ")
    if not text.startswith("[youtube] "):
        return None
    text = text.removeprefix("[youtube] ")
    head, sep, rest = text.partition(": ")
    return rest if sep and VIDEO_ID.fullmatch(head) else text


def classify(
    info: dict[str, Any] | None, messages: list[str]
) -> tuple[Verdict, str | None, str | None]:
    """The verdict, the video's title (when YouTube knows it) and YouTube's message."""
    detail = messages[0] if messages else None
    if info is not None:
        formats = info.get("formats") or []
        if any(f.get("acodec") not in (None, "none") and f.get("url") for f in formats):
            return "plays", info.get("title"), None
        if info.get("availability") in _PLAYABLE_IN_BROWSER:
            return "refused", info.get("title"), detail
    return "gone", None, detail


def ytdlp_probe(video_id: str) -> tuple[dict[str, Any] | None, list[str]]:
    import yt_dlp  # only here: the rest of the pipeline never sees yt-dlp types

    warnings: list[str] = []

    class Logger:
        def debug(self, message: str) -> None:
            pass

        def info(self, message: str) -> None:
            pass

        def warning(self, message: str) -> None:
            warnings.append(message)

        def error(self, message: str) -> None:
            warnings.append(message)

    options: dict[str, Any] = {
        "quiet": True,
        "skip_download": True,
        # A refused video has no formats; its metadata still tells it from a gone one.
        "ignore_no_formats_error": True,
        # The app's lookups go over IPv4 (Ipv4OnlyDns): YouTube bot-checks this network's IPv6.
        "source_address": "0.0.0.0",
        "extractor_args": {"youtube": {"player_client": [CLIENT]}},
        "logger": Logger(),
    }
    url = f"https://www.youtube.com/watch?v={video_id}"
    with yt_dlp.YoutubeDL(options) as ydl:
        try:
            info: dict[str, Any] = ydl.extract_info(url, download=False)
        except yt_dlp.utils.DownloadError as e:
            raise_if_blocked(e)
            cause = e.exc_info[1] if e.exc_info else None
            if isinstance(cause, yt_dlp.utils.ExtractorError) and cause.expected:
                return None, [m for m in [youtube_message(str(e))] if m]
            raise CheckFailed(str(e)) from e
    for warning in warnings:
        raise_if_blocked(Exception(warning))
        if _NETWORK in warning:
            raise CheckFailed(warning)
    return info, [m for m in map(youtube_message, warnings) if m]


def ytdlp_version() -> str:
    import yt_dlp

    version: str = yt_dlp.version.__version__
    return version


class AppChecks:
    def __init__(
        self,
        cache_dir: Path,
        probe: Probe = ytdlp_probe,
        clock: Callable[[], datetime] = lambda: datetime.now(UTC),
        version: Callable[[], str] = ytdlp_version,
    ) -> None:
        self._cache_dir = cache_dir
        self._probe = probe
        self._clock = clock
        self._version = version

    def cached(self, video_id: str) -> AppCheck | None:
        try:
            return AppCheck.model_validate_json(self._path(video_id).read_text(encoding="utf-8"))
        except (OSError, ValidationError):
            return None

    def fresh(self, video_id: str) -> AppCheck:
        """Asks YouTube now; raises YouTubeBlocked or CheckFailed, which are never cached."""
        path = self._path(video_id)
        info, messages = self._probe(video_id)
        verdict, title, detail = classify(info, messages)
        check = AppCheck(
            video_id=video_id,
            verdict=verdict,
            title=title,
            detail=detail,
            checked_at=self._clock().replace(microsecond=0),
            client=CLIENT,
            yt_dlp=self._version(),
        )
        path.parent.mkdir(parents=True, exist_ok=True)
        data = check.model_dump(mode="json", by_alias=True)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        return check

    def _path(self, video_id: str) -> Path:
        if not VIDEO_ID.fullmatch(video_id):
            raise ValueError(f"{video_id!r} is not a YouTube video id")
        return self._cache_dir / f"{video_id}.json"
