"""Checks a parsed manifest against the rules in docs/manifest.md.

Each rule reports once per offending object, so one defect never yields two errors. Mirrors the
app's ManifestValidator; the shared fixtures keep the two in agreement.
"""

import re
from collections import Counter
from collections.abc import Hashable, Iterable
from datetime import datetime

from vgmixr_pipeline.manifest.errors import ErrorCode, ManifestError
from vgmixr_pipeline.manifest.models import (
    Game,
    LocalSource,
    Loop,
    Manifest,
    Source,
    StreamSource,
    Track,
    UnknownSource,
)

# Used with re.fullmatch: `$` would also match before a trailing newline.
SLUG = re.compile(r"[a-z0-9-]+")
COLOR = re.compile(r"#[0-9A-Fa-f]{6}")
VIDEO_ID = re.compile(r"[A-Za-z0-9_-]{11}")
# The shape the app's Instant.parse accepts: seconds required, up to 9 fractional digits.
# fromisoformat alone also takes date-only and space-separated forms, and offsets other than Z.
# (Instant.parse also takes 24:00:00 and leap second 23:59:60, which this rejects.)
UTC_INSTANT = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?Z", re.ASCII)
SHORT_NAME_MAX = 12

E = ErrorCode


def validate(manifest: Manifest) -> list[ManifestError]:
    errors = []
    if not _is_utc_instant(manifest.generatedAt):
        errors.append(ManifestError(E.GENERATED_AT_INVALID, manifest.generatedAt))
    errors += _validate_games(manifest.games)
    errors += _validate_families(manifest.games)
    errors += _validate_tracks(manifest.tracks, manifest.games)
    return errors


def _is_utc_instant(value: str) -> bool:
    if not UTC_INSTANT.fullmatch(value):
        return False
    try:
        datetime.fromisoformat(value)
    except ValueError:  # e.g. February 30
        return False
    return True


def _validate_games(games: list[Game]) -> list[ManifestError]:
    errors = [ManifestError(E.GAME_ID_DUPLICATE, i) for i in _duplicates(g.id for g in games)]
    for game in games:
        if not SLUG.fullmatch(game.id):
            errors.append(ManifestError(E.GAME_SLUG_INVALID, f"game id {game.id!r}"))
        if game.family is not None and not SLUG.fullmatch(game.family):
            errors.append(ManifestError(E.GAME_SLUG_INVALID, f"{game.id} family {game.family!r}"))
        for field, value in (("name", game.name), ("platform", game.platform)):
            if not value:
                errors.append(ManifestError(E.GAME_FIELD_EMPTY, f"{game.id} {field}"))
        if game.generation is not None and game.generation < 1:
            errors.append(ManifestError(E.GAME_GENERATION_INVALID, f"{game.id} {game.generation}"))
        # len() counts code points, as the app does.
        if not 1 <= len(game.shortName) <= SHORT_NAME_MAX:
            msg = f"{game.id} {game.shortName!r}"
            errors.append(ManifestError(E.GAME_SHORT_NAME_INVALID, msg))
        if not COLOR.fullmatch(game.color):
            errors.append(ManifestError(E.GAME_COLOR_INVALID, f"{game.id} {game.color!r}"))
    return errors


def _validate_families(games: list[Game]) -> list[ManifestError]:
    errors = []
    generations: dict[str, set[int | None]] = {}
    for game in games:
        generations.setdefault(game.family_id, set()).add(game.generation)
    # None counts as a value: some games with a generation and some without is a mismatch.
    for family_id, values in generations.items():
        if len(values) > 1:
            errors.append(ManifestError(E.FAMILY_GENERATION_MISMATCH, family_id))
    for family in dict.fromkeys(g.family for g in games if g.family is not None):
        if any(g.id == family and g.family_id != family for g in games):
            errors.append(ManifestError(E.FAMILY_ID_COLLISION, family))
    return errors


def _validate_tracks(tracks: list[Track], games: list[Game]) -> list[ManifestError]:
    game_ids_by_family: dict[str, list[str]] = {}
    for game in games:
        game_ids_by_family.setdefault(game.family_id, []).append(game.id)
    track_ids = {t.id for t in tracks}
    errors = [ManifestError(E.TRACK_ID_DUPLICATE, i) for i in _duplicates(t.id for t in tracks)]
    for track in tracks:
        if not SLUG.fullmatch(track.id):
            errors.append(ManifestError(E.TRACK_ID_INVALID, repr(track.id)))
        if not track.title:
            errors.append(ManifestError(E.TRACK_TITLE_EMPTY, track.id))
        if track.order < 0:
            errors.append(ManifestError(E.TRACK_ORDER_INVALID, f"{track.id} {track.order}"))
        family_game_ids = game_ids_by_family.get(track.family)
        if family_game_ids is None:
            msg = f"{track.id} family {track.family!r}"
            errors.append(ManifestError(E.TRACK_FAMILY_UNKNOWN, msg))
        if track.games is not None:
            # Membership is only checked against a known family, so an unknown family isn't
            # reported twice.
            outside = family_game_ids is not None and any(
                g not in family_game_ids for g in track.games
            )
            if not track.games or len(set(track.games)) != len(track.games) or outside:
                errors.append(ManifestError(E.TRACK_GAMES_INVALID, f"{track.id} {track.games}"))
        if not track.sources:
            errors.append(ManifestError(E.TRACK_SOURCES_EMPTY, track.id))
        errors += _validate_sources(track)
        for replaced in track.replaces:
            if not SLUG.fullmatch(replaced) or replaced in track_ids:
                msg = f"{track.id} replaces {replaced!r}"
                errors.append(ManifestError(E.TRACK_REPLACES_INVALID, msg))
    return errors + _validate_replaced_once(tracks)


def _validate_replaced_once(tracks: list[Track]) -> list[ManifestError]:
    """One error per id replaced by more than one track, reported on the second track to list it."""
    first_replacer: dict[str, str] = {}
    reported: set[str] = set()
    errors = []
    for track in tracks:
        for replaced in dict.fromkeys(track.replaces):
            first = first_replacer.setdefault(replaced, track.id)
            if first != track.id and replaced not in reported:
                reported.add(replaced)
                msg = f"{track.id} replaces {replaced!r}, already replaced by {first}"
                errors.append(ManifestError(E.TRACK_REPLACES_INVALID, msg))
    return errors


def _validate_sources(track: Track) -> list[ManifestError]:
    errors = []
    seen: set[Hashable] = set()
    for index, source in enumerate(track.sources):
        at = f"{track.id} source {index}"
        identity = _identity(source)
        if identity is not None:
            if identity in seen:
                errors.append(ManifestError(E.SOURCE_IDENTITY_DUPLICATE, at))
            seen.add(identity)
        match source:
            case LocalSource():
                if not _is_valid_path(source.path):
                    errors.append(ManifestError(E.LOCAL_PATH_INVALID, f"{at} {source.path!r}"))
                if source.loop is not None and not _is_valid_loop(source.loop):
                    errors.append(ManifestError(E.LOOP_INVALID, at))
            case StreamSource():
                end_before_start = source.endMs is not None and source.endMs <= source.startMs
                if not VIDEO_ID.fullmatch(source.videoId) or source.startMs < 0 or end_before_start:
                    errors.append(ManifestError(E.STREAM_INVALID, at))
                if source.confidence is not None and not 0 <= source.confidence <= 1:
                    msg = f"{at} confidence {source.confidence}"
                    errors.append(ManifestError(E.STREAM_CONFIDENCE_INVALID, msg))
                if source.loop is not None:
                    errors.append(ManifestError(E.STREAM_LOOP_PRESENT, at))
            case UnknownSource():
                errors.append(ManifestError(E.SOURCE_TYPE_UNKNOWN, f"{at} type {source.type!r}"))
    return errors


def _is_valid_path(path: str) -> bool:
    return (
        bool(path) and not path.startswith("/") and "\\" not in path and ".." not in path.split("/")
    )


def _is_valid_loop(loop: Loop) -> bool:
    return loop.sampleRate > 0 and loop.introEnd >= 0 and loop.loopEnd > loop.introEnd


def _identity(source: Source) -> Hashable | None:
    """Runtime source state is keyed by this, so two sources in a track must not share it."""
    match source:
        case LocalSource():
            return ("local", source.path)
        case StreamSource():
            return ("stream", source.videoId, source.startMs, source.endMs)
        case UnknownSource():
            return None


def _duplicates(ids: Iterable[str]) -> list[str]:
    return [id_ for id_, count in Counter(ids).items() if count > 1]
