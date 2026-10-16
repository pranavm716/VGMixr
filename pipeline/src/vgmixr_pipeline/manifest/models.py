"""The manifest exactly as written in JSON (docs/manifest.md).

Field names match the JSON keys, so they're camelCase. Strict mode rejects coercions such as "1" or
3.0 into an int field, as the app does. Unknown keys are rejected (`extra="forbid"`): the app
ignores them, but the pipeline writes the manifest, so here they can only be typos.
"""

from datetime import datetime
from typing import Annotated, Any, Literal

from pydantic import BaseModel, ConfigDict, Discriminator, Tag


class _Model(BaseModel):
    model_config = ConfigDict(strict=True, extra="forbid", frozen=True)


class Game(_Model):
    id: str
    name: str
    shortName: str
    color: str
    generation: int | None = None
    platform: str
    family: str | None = None

    @property
    def family_id(self) -> str:
        """A standalone game (no `family`) is its own family."""
        return self.family if self.family is not None else self.id


class Loop(_Model):
    sampleRate: int
    introEnd: int
    loopEnd: int


class LocalSource(_Model):
    type: Literal["local"]
    path: str
    loop: Loop | None = None


class StreamSource(_Model):
    type: Literal["stream"]
    videoId: str
    startMs: int = 0
    endMs: int | None = None
    confidence: float | None = None
    # Not allowed on streams. Declared so the validator reports it instead of parsing failing.
    loop: Loop | None = None


class UnknownSource(_Model):
    """A source whose `type` isn't known: a validation error, not a parsing one."""

    model_config = ConfigDict(extra="ignore")

    type: str


def _source_tag(value: Any) -> str | None:
    """Picks a source's model by its `type`; None (not an object with a string type) fails."""
    source_type = value.get("type") if isinstance(value, dict) else None
    if not isinstance(source_type, str):
        return None
    return source_type if source_type in ("local", "stream") else "unknown"


type Source = Annotated[
    Annotated[LocalSource, Tag("local")]
    | Annotated[StreamSource, Tag("stream")]
    | Annotated[UnknownSource, Tag("unknown")],
    Discriminator(_source_tag),
]


class Track(_Model):
    id: str
    family: str
    # None (omitted) means all of the family's games; empty is a validation error.
    games: list[str] | None = None
    title: str
    # Kept as written; the app imports unknown values as `other`.
    category: str
    order: int
    sources: list[Source]
    replaces: list[str] = []


class Manifest(_Model):
    schemaVersion: int
    generatedAt: str
    games: list[Game]
    tracks: list[Track]

    def generated_at(self) -> datetime:
        """`generatedAt` as a UTC datetime. Only call this on a validated manifest."""
        return datetime.fromisoformat(self.generatedAt)
