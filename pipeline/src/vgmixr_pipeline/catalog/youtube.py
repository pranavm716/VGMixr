"""YouTube playlists, searches and videos through yt-dlp (no API key), cached on disk.

Only the fields matching needs are cached (trimmed), under playlists/, search/ and videos/. A cached
response is never refetched unless `refresh` is set, so builds after the first run offline and see
the same results. Network calls are spaced out, like Bulbapedia's.
"""

import json
import time
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from vgmixr_pipeline.catalog.slug import slugify

DELAY_SECONDS = 1.0
# Placeholders yt-dlp lists for entries that can't be played.
_UNAVAILABLE = {"[Deleted video]", "[Private video]"}

# (url, flat) -> yt-dlp's info dict. Flat lists a playlist's entries without opening each video.
type Extract = Callable[[str, bool], dict[str, Any]]


def ytdlp_extract(url: str, flat: bool) -> dict[str, Any]:
    import yt_dlp  # only here: the rest of the pipeline never sees yt-dlp types

    options = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "extract_flat": "in_playlist" if flat else False,
        # Metadata (title, chapters) is all we need, even when no format is playable.
        "ignore_no_formats_error": True,
    }
    with yt_dlp.YoutubeDL(options) as ydl:
        info: dict[str, Any] = ydl.extract_info(url, download=False)
        return info


@dataclass(frozen=True)
class Entry:
    """A video in a playlist or search result. `position` counts from 1, unavailable entries
    included, so it matches what YouTube shows."""

    id: str
    title: str
    duration_ms: int | None
    channel: str | None
    position: int


@dataclass(frozen=True)
class Chapter:
    title: str
    start_ms: int
    # None for the last chapter: it plays to the end of the video.
    end_ms: int | None


@dataclass(frozen=True)
class Video:
    id: str
    title: str
    duration_ms: int | None
    chapters: list[Chapter]


class YouTube:
    def __init__(
        self,
        cache_dir: Path,
        extract: Extract = ytdlp_extract,
        refresh: bool = False,
        sleep: Callable[[float], Any] = time.sleep,
    ) -> None:
        self._cache_dir = cache_dir
        self._extract = extract
        self._refresh = refresh
        self._sleep = sleep
        self._fetched: set[Path] = set()  # cache files written in this run
        self._called = False

    def playlist(self, playlist_id: str) -> list[Entry]:
        url = f"https://www.youtube.com/playlist?list={playlist_id}"
        return _entries(self._cached(Path("playlists", playlist_id), url, True, _trim_listing))

    def search(self, query: str, n: int = 10) -> list[Entry]:
        key = Path("search", slugify(query))
        return _entries(self._cached(key, f"ytsearch{n}:{query}", True, _trim_listing))

    def video(self, video_id: str) -> Video:
        url = f"https://www.youtube.com/watch?v={video_id}"
        data = self._cached(Path("videos", video_id), url, False, _trim_video)
        chapters = data["chapters"]
        return Video(
            video_id,
            data["title"],
            _ms(data["duration"]),
            [
                Chapter(
                    c["title"],
                    _ms(c["start"]) or 0,
                    None if i == len(chapters) - 1 else _ms(c["end"]),
                )
                for i, c in enumerate(chapters)
            ],
        )

    def _cached(
        self,
        key: Path,
        url: str,
        flat: bool,
        trim: Callable[[dict[str, Any]], dict[str, Any]],
    ) -> dict[str, Any]:
        path = (self._cache_dir / key).with_suffix(".json")
        if path.exists() and (not self._refresh or path in self._fetched):
            cached: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
            return cached
        if self._called:
            self._sleep(DELAY_SECONDS)
        self._called = True
        data = trim(self._extract(url, flat))
        self._fetched.add(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
        return data


def _trim_listing(info: dict[str, Any]) -> dict[str, Any]:
    entries = [
        {k: e.get(k) for k in ("id", "title", "duration", "channel")}
        for e in info.get("entries") or []
    ]
    return {"entries": entries}


def _trim_video(info: dict[str, Any]) -> dict[str, Any]:
    chapters = [
        {"title": c["title"], "start": c["start_time"], "end": c["end_time"]}
        for c in info.get("chapters") or []
    ]
    return {"title": info["title"], "duration": info.get("duration"), "chapters": chapters}


def _entries(data: dict[str, Any]) -> list[Entry]:
    return [
        Entry(e["id"], e["title"], _ms(e["duration"]), e["channel"], position)
        for position, e in enumerate(data["entries"], start=1)
        if e["title"] not in _UNAVAILABLE
    ]


def _ms(seconds: float | None) -> int | None:
    return None if seconds is None else round(seconds * 1000)
