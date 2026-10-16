"""The catalog builder's hand-written inputs in pipeline/data/.

games.json: the games (in manifest form) and, per family, which Bulbapedia album discs list its
tracks. overrides.json: hand corrections keyed by generated track id, plus extra tracks.
durations.json: track lengths for album pages that list none. All are reapplied on every build;
the generated draft is never edited by hand.
"""

from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, field_validator, model_validator

from vgmixr_pipeline.catalog.tracklist import parse_duration
from vgmixr_pipeline.manifest.models import Game, Source

DATA_DIR = Path(__file__).resolve().parents[3] / "data"

type Category = Literal["battle", "route", "town", "dungeon", "event", "other"]


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


class GamesFile(_Model):
    games: list[Game]
    tracklists: list[Tracklist]


class Split(_Model):
    games: list[str]
    title: str | None = None


class TrackOverride(_Model):
    title: str | None = None
    category: Category | None = None
    games: list[str] | None = None
    # True drops the track; False keeps one that was auto-excluded as a jingle.
    exclude: bool | None = None
    mergeInto: str | None = None
    # New track id -> the games whose occurrences move to it.
    split: dict[str, Split] | None = None
    note: str | None = None


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
        if duration is not None and parse_duration(duration) is None:
            raise ValueError(f"{duration!r} is not m:ss")
        return duration


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


def load_games(path: Path = DATA_DIR / "games.json") -> GamesFile:
    return GamesFile.model_validate_json(path.read_text(encoding="utf-8"))


def load_overrides(path: Path = DATA_DIR / "overrides.json") -> Overrides:
    return Overrides.model_validate_json(path.read_text(encoding="utf-8"))


def load_durations(path: Path = DATA_DIR / "durations.json") -> Durations:
    return Durations.model_validate_json(path.read_text(encoding="utf-8"))
