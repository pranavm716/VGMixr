"""YouTube playlists, searches and videos through yt-dlp (no API key), cached on disk.

Only the fields matching needs are cached (trimmed), under playlists/, search/, playlist-search/
and videos/. A cached
response is never refetched unless `refresh` is set, so builds after the first run offline and see
the same results. Network calls are spaced out, like Bulbapedia's. With `cached_only`, nothing is
fetched: an uncached playlist or search is empty and an uncached video has no chapters (YouTube
answers bursts of requests with a sign-in check, so a big run can be finished from the cache). That
check raises YouTubeBlocked; failures are never cached.
"""

import json
import time
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.parse import quote_plus

from vgmixr_pipeline.catalog.slug import slugify

DELAY_SECONDS = 1.0
# Playlists kept from a playlist search. The results page scrolls on and on, so yt-dlp is told
# to stop there too (a flat playlist search lists no entry counts, only id, title and channel).
PLAYLIST_RESULTS = 15
# YouTube's results page filter for playlists only.
_PLAYLISTS_FILTER = "EgIQAw%3D%3D"
# YouTube's answer to a burst of requests; it lifts after a while.
_BOT_CHECK = "not a bot"
# Placeholders yt-dlp lists for entries that can't be played.
_UNAVAILABLE = {"[Deleted video]", "[Private video]"}

# (url, flat) -> yt-dlp's info dict. Flat lists a playlist's entries without opening each video.
type Extract = Callable[[str, bool], dict[str, Any]]


class YouTubeBlocked(Exception):
    """YouTube asks to sign in to confirm we're not a bot."""


def raise_if_blocked(error: Exception) -> None:
    if _BOT_CHECK in str(error):
        raise YouTubeBlocked(str(error)) from error


def ytdlp_extract(url: str, flat: bool) -> dict[str, Any]:
    import yt_dlp  # only here: the rest of the pipeline never sees yt-dlp types

    options: dict[str, Any] = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "extract_flat": "in_playlist" if flat else False,
        # Metadata (title, chapters) is all we need, even when no format is playable.
        "ignore_no_formats_error": True,
    }
    if "/results?" in url:  # a playlist search: the results page paginates without end
        options["playlistend"] = PLAYLIST_RESULTS
    with yt_dlp.YoutubeDL(options) as ydl:
        try:
            info: dict[str, Any] = ydl.extract_info(url, download=False)
        except yt_dlp.utils.DownloadError as e:
            raise_if_blocked(e)
            raise
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
class PlaylistHit:
    """A playlist found by a playlist search."""

    id: str
    title: str
    channel: str | None


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
        cached_only: bool = False,
        delay: float = DELAY_SECONDS,
    ) -> None:
        self._cache_dir = cache_dir
        self._extract = extract
        self._refresh = refresh
        self._cached_only = cached_only
        self._sleep = sleep
        self._delay = delay
        self._fetched: set[Path] = set()  # cache files written in this run
        self._called = False

    def playlist(self, playlist_id: str) -> list[Entry]:
        url = f"https://www.youtube.com/playlist?list={playlist_id}"
        return _entries(self._cached(Path("playlists", playlist_id), url, True, _trim_listing))

    def search(self, query: str, n: int = 10) -> list[Entry]:
        key = Path("search", slugify(query))
        return _entries(self._cached(key, f"ytsearch{n}:{query}", True, _trim_listing))

    def search_playlists(self, query: str, n: int = PLAYLIST_RESULTS) -> list[PlaylistHit]:
        url = f"https://www.youtube.com/results?search_query={quote_plus(query)}&sp={_PLAYLISTS_FILTER}"
        data = self._cached(Path("playlist-search", slugify(query)), url, True, _trim_hits)
        return [PlaylistHit(e["id"], e["title"], e["channel"]) for e in data["entries"][:n]]

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
        if self._cached_only:
            return trim({"title": "", "entries": []})
        if self._called and self._delay > 0:
            self._sleep(self._delay)
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


def _trim_hits(info: dict[str, Any]) -> dict[str, Any]:
    entries = [{k: e.get(k) for k in ("id", "title", "channel")} for e in info.get("entries") or []]
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
