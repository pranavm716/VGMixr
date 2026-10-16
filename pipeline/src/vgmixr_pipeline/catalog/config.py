"""The catalog builder's hand-written inputs in pipeline/data/.

games.json: the games (in manifest form) and, per family, which Bulbapedia album discs list its
tracks. overrides.json: hand corrections keyed by generated track id, plus extra tracks. Both are
reapplied on every build; the generated draft is never edited by hand.
"""

from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict

from vgmixr_pipeline.manifest.models import Game, Source

DATA_DIR = Path(__file__).resolve().parents[3] / "data"

type Category = Literal["battle", "route", "town", "dungeon", "event", "other"]


class _Model(BaseModel):
    model_config = ConfigDict(strict=True, extra="forbid", frozen=True)


class Tracklist(_Model):
    """One disc of a Bulbapedia album page, optionally limited to CD numbers first..last."""

    page: str
    disc: str
    first: int | None = None
    last: int | None = None
    family: str
    # None means the tracks exist in every game of the family.
    games: list[str] | None = None


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
    note: str | None = None


class Overrides(_Model):
    tracks: dict[str, TrackOverride] = {}
    extras: list[Extra] = []


def load_games(path: Path = DATA_DIR / "games.json") -> GamesFile:
    return GamesFile.model_validate_json(path.read_text(encoding="utf-8"))


def load_overrides(path: Path = DATA_DIR / "overrides.json") -> Overrides:
    return Overrides.model_validate_json(path.read_text(encoding="utf-8"))
