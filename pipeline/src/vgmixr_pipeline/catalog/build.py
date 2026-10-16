"""Builds the draft catalog: Bulbapedia track rows plus hand overrides (docs/roadmap.md, step 16).

Each Bulbapedia row is an occurrence of a title in a family. Occurrences with the same id
(`<family>-<slug of title>`) are one track; overrides then merge, split, exclude, correct and add
tracks. Every rule problem is collected and reported at once as a BuildError.
"""

from collections.abc import Iterable
from dataclasses import dataclass, field
from typing import Literal

from pydantic import BaseModel, ConfigDict

from vgmixr_pipeline.catalog.category import guess_category
from vgmixr_pipeline.catalog.config import Overrides, TrackOverride
from vgmixr_pipeline.catalog.jingles import is_jingle
from vgmixr_pipeline.catalog.slug import track_id
from vgmixr_pipeline.manifest.models import Game, Source


@dataclass(frozen=True)
class Occurrence:
    """One Bulbapedia row: a title in a family, for `games` (None = every game of the family)."""

    family: str
    games: tuple[str, ...] | None
    title: str
    duration_ms: int | None
    origin: str


class DraftTrack(BaseModel):
    """A manifest track (stream sources come in Part B) plus what the build knows about it."""

    model_config = ConfigDict(strict=True, extra="forbid", frozen=True)

    id: str
    family: str
    games: list[str] | None
    title: str
    category: str
    order: int
    sources: list[Source]
    durationMs: int | None
    origins: list[str]


@dataclass(frozen=True)
class Excluded:
    id: str
    title: str
    duration_ms: int | None
    reason: Literal["jingle", "override"]


@dataclass(frozen=True)
class BuildResult:
    tracks: list[DraftTrack]
    excluded: list[Excluded]


class BuildError(Exception):
    def __init__(self, errors: list[str]) -> None:
        super().__init__("\n".join(errors))
        self.errors = errors


@dataclass
class _Track:
    """A track while it's being assembled."""

    id: str
    family: str
    title: str
    occurrences: list[Occurrence] = field(default_factory=list)
    games: set[str] = field(default_factory=set)
    explicit_order: int | None = None
    category: str | None = None
    sources: list[Source] = field(default_factory=list)
    is_extra: bool = False


def build_tracks(
    games: list[Game], occurrences: list[Occurrence], overrides: Overrides
) -> BuildResult:
    family_games: dict[str, list[str]] = {}
    for game in games:
        family_games.setdefault(game.family_id, []).append(game.id)
    errors: list[str] = []

    tracks = _group(occurrences, family_games, overrides.tracks, errors)
    known_ids = set(tracks) | {
        occurrence_id for occurrence_id in (track_id(o.family, o.title) for o in occurrences)
    }
    tracks = _split(tracks, family_games, overrides.tracks, errors)
    known_ids |= set(tracks)
    for extra_track in _extras(overrides, family_games, set(tracks), errors):
        tracks[extra_track.id] = extra_track
        known_ids.add(extra_track.id)
    for key in overrides.tracks:
        if key not in known_ids:
            errors.append(f"override {key}: no such track")

    kept: list[_Track] = []
    excluded: list[Excluded] = []
    for track in tracks.values():
        override = overrides.tracks.get(track.id, TrackOverride())
        duration = _duration(track.occurrences)
        if override.exclude:
            excluded.append(Excluded(track.id, track.title, duration, "override"))
        elif override.exclude is None and is_jingle(track.title, duration):
            excluded.append(Excluded(track.id, track.title, duration, "jingle"))
        else:
            _apply(track, override, family_games, errors)
            kept.append(track)
    if errors:
        raise BuildError(errors)
    return BuildResult(_draft(kept, family_games), excluded)


def _group(
    occurrences: Iterable[Occurrence],
    family_games: dict[str, list[str]],
    overrides: dict[str, TrackOverride],
    errors: list[str],
) -> dict[str, _Track]:
    raw_ids = {track_id(o.family, o.title) for o in occurrences}
    tracks: dict[str, _Track] = {}
    for occurrence in occurrences:
        if occurrence.family not in family_games:
            errors.append(f"{occurrence.origin}: unknown family {occurrence.family!r}")
            continue
        id_ = track_id(occurrence.family, occurrence.title)
        target = overrides.get(id_, TrackOverride()).mergeInto
        if target is not None:
            if target not in raw_ids or overrides.get(target, TrackOverride()).mergeInto:
                if id_ not in tracks:  # report once
                    errors.append(f"override {id_}: mergeInto {target!r} is not a track")
                tracks.setdefault(id_, _Track(id_, occurrence.family, occurrence.title))
                continue
            id_ = target
        track = tracks.setdefault(id_, _Track(id_, occurrence.family, occurrence.title))
        track.occurrences.append(occurrence)
        track.games |= _games(occurrence, family_games)
    return tracks


def _split(
    tracks: dict[str, _Track],
    family_games: dict[str, list[str]],
    overrides: dict[str, TrackOverride],
    errors: list[str],
) -> dict[str, _Track]:
    result: dict[str, _Track] = {}
    for track in tracks.values():
        result[track.id] = track
        for new_id, split in (overrides.get(track.id, TrackOverride()).split or {}).items():
            subset = set(split.games)
            moved = [o for o in track.occurrences if _games(o, family_games) <= subset]
            if not moved or len(moved) == len(track.occurrences):
                errors.append(f"override {track.id}: split {new_id} must move some occurrences")
                continue
            if new_id in tracks or new_id in result:
                errors.append(f"override {track.id}: split id {new_id} already exists")
                continue
            track.occurrences = [o for o in track.occurrences if o not in moved]
            track.games = set().union(*(_games(o, family_games) for o in track.occurrences))
            new = _Track(new_id, track.family, split.title or moved[0].title, moved)
            new.games = set().union(*(_games(o, family_games) for o in moved))
            result[new_id] = new
    return result


def _extras(
    overrides: Overrides,
    family_games: dict[str, list[str]],
    taken: set[str],
    errors: list[str],
) -> list[_Track]:
    tracks = []
    for extra in overrides.extras:
        id_ = extra.id or track_id(extra.family, extra.title)
        if extra.family not in family_games:
            errors.append(f"extra {id_}: unknown family {extra.family!r}")
            continue
        if id_ in taken:
            errors.append(f"extra {id_}: id already used; give the extra its own id")
            continue
        taken.add(id_)
        track = _Track(id_, extra.family, extra.title, is_extra=True)
        track.games = set(extra.games or family_games[extra.family])
        track.explicit_order = extra.order
        track.category = extra.category
        track.sources = list(extra.sources)
        if extra.games is not None:
            _check_games(id_, extra.games, family_games[extra.family], errors)
        tracks.append(track)
    return tracks


def _apply(
    track: _Track,
    override: TrackOverride,
    family_games: dict[str, list[str]],
    errors: list[str],
) -> None:
    if override.title is not None:
        track.title = override.title
    if override.category is not None:
        track.category = override.category
    if override.games is not None:
        _check_games(track.id, override.games, family_games[track.family], errors)
        track.games = set(override.games)


def _check_games(id_: str, games: list[str], family: list[str], errors: list[str]) -> None:
    if not games or len(set(games)) != len(games) or not set(games) <= set(family):
        errors.append(f"{id_}: games {games} must be distinct games of its family {family}")


def _draft(tracks: list[_Track], family_games: dict[str, list[str]]) -> list[DraftTrack]:
    """Tracks grouped by family (in order of first appearance), generated ones in occurrence
    order with consecutive `order`s, then extras."""
    by_family: dict[str, list[_Track]] = {}
    for track in tracks:
        by_family.setdefault(track.family, []).append(track)
    drafts = []
    for family, members in by_family.items():
        generated = [t for t in members if not t.is_extra]
        extras = [t for t in members if t.is_extra]
        for order, track in enumerate([*generated, *extras]):
            all_games = family_games[family]
            games = (
                None
                if track.games >= set(all_games)
                else [g for g in all_games if g in track.games]
            )
            drafts.append(
                DraftTrack(
                    id=track.id,
                    family=family,
                    games=games,
                    title=track.title,
                    category=track.category or guess_category(track.title),
                    order=track.explicit_order if track.explicit_order is not None else order,
                    sources=track.sources,
                    durationMs=_duration(track.occurrences),
                    origins=[o.origin for o in track.occurrences],
                )
            )
    return drafts


def _games(occurrence: Occurrence, family_games: dict[str, list[str]]) -> set[str]:
    return set(occurrence.games or family_games[occurrence.family])


def _duration(occurrences: list[Occurrence]) -> int | None:
    return max((o.duration_ms for o in occurrences if o.duration_ms is not None), default=None)
