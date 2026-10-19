"""The catalog builder's hand-written inputs in pipeline/data/.

games.json: the games (in manifest form) and, per family, which Bulbapedia album discs list its
tracks. overrides.json: hand corrections keyed by generated track id, plus extra tracks.
durations.json: track lengths for album pages that list none. order.json: each family's track order,
set by hand in the review tool. All are reapplied on every build;
the generated draft is never edited by hand.
"""

import re
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, RootModel, field_validator, model_validator

from vgmixr_pipeline.catalog.tracklist import parse_duration
from vgmixr_pipeline.manifest.models import Game, LocalSource, Source
from vgmixr_pipeline.manifest.validate import VIDEO_ID

DATA_DIR = Path(__file__).resolve().parents[3] / "data"

type Category = Literal["battle", "route", "town", "dungeon", "event", "other"]


def _check_time(time: str | None) -> str | None:
    if time is not None and parse_duration(time) is None:
        raise ValueError(f"{time!r} is not m:ss")
    return time


class _Model(BaseModel):
    model_config = ConfigDict(strict=True, extra="forbid", frozen=True)


class Tracklist(_Model):
    """One disc of a track list, optionally limited to CD numbers first..last.

    The list is a Bulbapedia album `page`, or a hand-written TSV `file` under pipeline/data/ for
    games without a usable album page.
    """

    page: str | None = None
    file: str | None = None
    disc: str
    first: int | None = None
    last: int | None = None
    family: str
    # None means the tracks exist in every game of the family.
    games: list[str] | None = None
    # Appended to every title, so another version of a family's tracks (e.g. HGSS's GB Sounds)
    # gets its own ids instead of merging with the originals.
    titleSuffix: str = ""

    @model_validator(mode="after")
    def _one_source(self) -> "Tracklist":
        if (self.page is None) == (self.file is None):
            raise ValueError("a tracklist needs exactly one of page and file")
        return self

    @property
    def source(self) -> str:
        """The page or file, for messages and track origins."""
        return self.page if self.page is not None else str(self.file)


# Playlist ids are longer than video ids and vary in length by kind (PL…, OLAK5uy_…).
PLAYLIST_ID = re.compile(r"[A-Za-z0-9_-]{10,}")


def _check_video_id(video_id: str) -> str:
    if not VIDEO_ID.fullmatch(video_id):
        raise ValueError(f"{video_id!r} is not a YouTube video id")
    return video_id


class Playlist(_Model):
    """A YouTube playlist holding tracks of `games`, optionally limited to positions first..last."""

    games: list[str]
    playlist: str
    first: int | None = None
    last: int | None = None
    note: str | None = None

    @field_validator("playlist")
    @classmethod
    def _check_id(cls, playlist: str) -> str:
        if not PLAYLIST_ID.fullmatch(playlist):
            raise ValueError(f"{playlist!r} is not a YouTube playlist id")
        return playlist

    @model_validator(mode="after")
    def _check_range(self) -> "Playlist":
        if self.first is not None and self.first < 1:
            raise ValueError("first must be 1 or more")
        if self.first is not None and self.last is not None and self.first > self.last:
            raise ValueError("first must not be after last")
        return self


class Album(_Model):
    """One long video of `games`' tracks whose chapters (description timestamps) are tracks."""

    games: list[str]
    video: str
    note: str | None = None

    @field_validator("video")
    @classmethod
    def _check_id(cls, video: str) -> str:
        return _check_video_id(video)


class YouTube(_Model):
    playlists: list[Playlist] = []
    albums: list[Album] = []
    # Game or family id -> extra names that identify it in video titles. Each game's name and
    # each family's label are names already.
    aliases: dict[str, list[str]] = {}


class GamesFile(_Model):
    games: list[Game]
    tracklists: list[Tracklist]
    youtube: YouTube = YouTube()

    @model_validator(mode="after")
    def _check_youtube_games(self) -> "GamesFile":
        game_ids = {g.id for g in self.games}
        entries: list[Playlist | Album] = [*self.youtube.playlists, *self.youtube.albums]
        for entry in entries:
            if not entry.games or not set(entry.games) <= game_ids:
                raise ValueError(f"{entry}: games must be known game ids")
        families = {g.family_id for g in self.games}
        for key in self.youtube.aliases:
            if key not in game_ids | families:
                raise ValueError(f"aliases: {key!r} is not a game or family id")
        return self


class Split(_Model):
    games: list[str]
    title: str | None = None


class Part(_Model):
    """One of the tracks a `parts` override makes of an album track that plays several pieces in
    a row (e.g. a route's spring and summer versions, one cycle each)."""

    title: str
    # "m:ss"; else an equal share of the whole track's length.
    duration: str | None = None

    @field_validator("duration")
    @classmethod
    def _check_duration(cls, duration: str | None) -> str | None:
        return _check_time(duration)


class Slice(_Model):
    """A stream source's identity: a video, or a part of one."""

    videoId: str
    startMs: int = 0
    endMs: int | None = None

    @field_validator("videoId")
    @classmethod
    def _check_id(cls, video_id: str) -> str:
        return _check_video_id(video_id)

    @model_validator(mode="after")
    def _check_bounds(self) -> "Slice":
        if self.startMs < 0 or (self.endMs is not None and self.endMs <= self.startMs):
            raise ValueError("startMs must be 0 or more and endMs after it")
        return self


class Streams(_Model):
    """Review decisions: accepted slices go first, in order; rejected videos are never used."""

    accept: list[Slice] = []
    reject: list[str] = []

    @field_validator("reject")
    @classmethod
    def _check_ids(cls, reject: list[str]) -> list[str]:
        return [_check_video_id(v) for v in reject]

    @model_validator(mode="after")
    def _check_disjoint(self) -> "Streams":
        if len(set(self.accept)) != len(self.accept):
            raise ValueError("accept lists a slice twice")
        if {s.videoId for s in self.accept} & set(self.reject):
            raise ValueError("a video is both accepted and rejected")
        return self


class TrackOverride(_Model):
    title: str | None = None
    category: Category | None = None
    games: list[str] | None = None
    # True drops the track; False keeps one that was auto-excluded as a jingle.
    exclude: bool | None = None
    mergeInto: str | None = None
    # New track id -> the games whose occurrences move to it.
    split: dict[str, Split] | None = None
    # "m:ss"; replaces the computed length (e.g. for a track merged from parts played back to back).
    duration: str | None = None
    streams: Streams | None = None
    # Local files (copied by hand into the app's music folder), played before any stream.
    local: list[LocalSource] = []
    # Replaces the track with consecutive tracks, one per part, each with its own id and overrides.
    parts: list[Part] | None = None
    note: str | None = None

    @field_validator("duration")
    @classmethod
    def _check_duration(cls, duration: str | None) -> str | None:
        return _check_time(duration)


class Extra(_Model):
    """A hand-written track. Unset fields are filled in like a generated track's."""

    id: str | None = None
    family: str
    title: str
    games: list[str] | None = None
    category: Category | None = None
    order: int | None = None
    sources: list[Source] = []
    # "m:ss"; lets the jingle length rule apply to the extra.
    duration: str | None = None
    note: str | None = None

    @field_validator("duration")
    @classmethod
    def _check_duration(cls, duration: str | None) -> str | None:
        return _check_time(duration)


class Overrides(_Model):
    tracks: dict[str, TrackOverride] = {}
    extras: list[Extra] = []


class PageDurations(_Model):
    """Track lengths for an album page that lacks them, from another listing of the same album."""

    source: str
    # Disc heading -> "m:ss" per track in CD number order (index 0 is #1); null where unknown.
    discs: dict[str, list[str | None]]

    @field_validator("discs")
    @classmethod
    def _check_times(cls, discs: dict[str, list[str | None]]) -> dict[str, list[str | None]]:
        for disc, times in discs.items():
            for number, time in enumerate(times, start=1):
                if time is not None and parse_duration(time) is None:
                    raise ValueError(f"{disc} #{number}: {time!r} is not m:ss")
        return discs


class Durations(_Model):
    """Fills in durations Bulbapedia leaves blank; Bulbapedia's own values always win."""

    pages: dict[str, PageDurations] = {}

    def lookup(self, page: str, disc: str, number: int) -> int | None:
        entry = self.pages.get(page)
        times = entry.discs.get(disc, []) if entry else []
        time = times[number - 1] if 0 < number <= len(times) else None
        return parse_duration(time) if time is not None else None


class TrackOrder(RootModel[dict[str, list[str]]]):
    """Family id -> its track ids in the order set by hand (see catalog/order.py). The ids are
    source keys, like the overrides'."""

    model_config = ConfigDict(strict=True, frozen=True)

    @field_validator("root")
    @classmethod
    def _check_distinct(cls, root: dict[str, list[str]]) -> dict[str, list[str]]:
        for family, ids in root.items():
            twice = sorted({i for i in ids if ids.count(i) > 1})
            if twice:
                raise ValueError(f"{family}: lists {', '.join(twice)} twice")
        return root


def load_games(path: Path = DATA_DIR / "games.json") -> GamesFile:
    return GamesFile.model_validate_json(path.read_text(encoding="utf-8"))


def load_overrides(path: Path = DATA_DIR / "overrides.json") -> Overrides:
    return Overrides.model_validate_json(path.read_text(encoding="utf-8"))


def load_durations(path: Path = DATA_DIR / "durations.json") -> Durations:
    return Durations.model_validate_json(path.read_text(encoding="utf-8"))


def load_order(path: Path = DATA_DIR / "order.json") -> TrackOrder:
    """The hand-set order; none when the file doesn't exist."""
    if not path.exists():
        return TrackOrder({})
    return TrackOrder.model_validate_json(path.read_text(encoding="utf-8"))
