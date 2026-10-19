"""Edits of the committed data files: overrides.json, games.json's playlists and order.json. Each
edit loads the raw JSON (keeping its order), changes it, validates the result with the builder's
own models, writes it in the canonical format (jsonfmt) to a temporary file and swaps it in. An
invalid edit raises EditError and writes nothing.
"""

import json
import os
from collections.abc import Callable, Collection
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ValidationError

from vgmixr_pipeline.catalog.config import GamesFile, Overrides, Playlist, Streams, TrackOrder
from vgmixr_pipeline.catalog.jsonfmt import (
    format_games,
    format_order,
    format_overrides,
    games_families,
    track_family,
)


class EditError(Exception):
    def __init__(self, errors: list[str]) -> None:
        super().__init__("\n".join(errors))
        self.errors = errors


def set_streams(
    path: Path,
    track_id: str,
    streams: dict[str, Any],
    track_ids: Collection[str],
    families: list[str],
) -> None:
    """Replaces a track's `streams` decision; an empty one removes it and an empty override."""
    _check_track(track_id, track_ids)
    decision = _validated(Streams, streams)
    value = _streams_json(decision) if decision.accept or decision.reject else None
    _set_key(path, track_id, "streams", value, families)


def set_exclude(
    path: Path,
    track_id: str,
    exclude: bool | None,
    track_ids: Collection[str],
    families: list[str],
) -> None:
    """Sets a track's `exclude` (True removes it from the catalog); None removes the key."""
    _check_track(track_id, track_ids)
    _set_key(path, track_id, "exclude", exclude, families)


def set_games(
    path: Path,
    track_id: str,
    games: list[str] | None,
    track_ids: Collection[str],
    families: list[str],
) -> None:
    """Sets a track's `games` (distinct ids, in the order given); None removes the key. That the
    games belong to the track's family is the caller's check, as the builder's."""
    _check_track(track_id, track_ids)
    if games is not None and (
        not isinstance(games, list)
        or not games
        or not all(isinstance(g, str) for g in games)
        or len(set(games)) != len(games)
    ):
        raise EditError([f"games must be a list of distinct game ids, not {games!r}"])
    _set_key(path, track_id, "games", games, families)


def rename(
    path: Path,
    track_id: str,
    title: str,
    streams: dict[str, Any] | None,
    track_ids: Collection[str],
    families: list[str],
) -> None:
    """Sets a track's `title` and, in the same write, its `streams` decision (the videos a rename
    pins); None leaves the decision as it is."""
    _check_track(track_id, track_ids)
    if not isinstance(title, str) or not title.strip() or "\n" in title or title != title.strip():
        raise EditError([f"a title is one line of text, not {title!r}"])
    keys: dict[str, Any] = {"title": title}
    if streams is not None:
        decision = _validated(Streams, streams)
        keys["streams"] = _streams_json(decision) if decision.accept or decision.reject else None
    _set_keys(path, track_id, keys, families)


def set_note(
    path: Path,
    track_id: str,
    note: str | None,
    track_ids: Collection[str],
    families: list[str],
) -> None:
    """Sets a track's `note` (always the override's last key); None removes it."""
    _check_track(track_id, track_ids)
    if note is not None and (not isinstance(note, str) or not note.strip() or "\n" in note):
        raise EditError([f"a note is one line of text, not {note!r}"])
    _set_keys(path, track_id, {"note": note}, families)


def set_order(path: Path, family: str, ids: list[str], families: list[str]) -> None:
    """Replaces a family's hand-set track order in order.json (created when missing); families
    stay in games-file order. That `ids` are the family's tracks is the caller's check."""
    if family not in families:
        raise EditError([f"{family} is not a family"])
    if not isinstance(ids, list) or not ids:
        raise EditError([f"{family}: an order lists the family's tracks, not {ids!r}"])
    data = _load(path) if path.exists() else {}
    data[family] = ids
    rank = {f: i for i, f in enumerate(families)}
    data = dict(sorted(data.items(), key=lambda item: rank.get(item[0], len(families))))
    _write(path, data, TrackOrder, format_order)


def add_playlist(path: Path, entry: dict[str, Any]) -> None:
    """Adds a playlist to games.json's youtube block, after the playlists of its first game's
    family (families in games-file order). The same playlist may be listed again with other games
    or another range (an album holding tracks of several families), never twice the same way."""
    playlist = _validated(Playlist, entry)
    data = _load(path)
    youtube: dict[str, Any] = data.setdefault("youtube", {})
    playlists: list[dict[str, Any]] = youtube.setdefault("playlists", [])
    if any(_validated(Playlist, p).model_dump(exclude={"note"}) ==
           playlist.model_dump(exclude={"note"}) for p in playlists):  # fmt: skip
        raise EditError([f"{playlist.playlist} is already listed with these games and range"])
    game_family = {g["id"]: g.get("family", g["id"]) for g in data["games"]}
    families = games_families(data)

    def rank(p: dict[str, Any]) -> int:
        family = game_family.get(p["games"][0]) if p["games"] else None
        return families.index(family) if family in families else len(families)

    new = playlist.model_dump(exclude_none=True)
    at = next((i for i, p in enumerate(playlists) if rank(p) > rank(new)), len(playlists))
    youtube["playlists"] = [*playlists[:at], new, *playlists[at:]]
    _write(path, data, GamesFile, format_games)


def remove_playlist(path: Path, index: int, playlist_id: str) -> None:
    """Removes the playlist at `index`, which must be `playlist_id` (else the page is stale)."""
    data = _load(path)
    playlists: list[dict[str, Any]] = data.get("youtube", {}).get("playlists", [])
    if not 0 <= index < len(playlists) or playlists[index].get("playlist") != playlist_id:
        raise EditError([f"playlist {index + 1} is not {playlist_id}; reload the page"])
    del playlists[index]
    _write(path, data, GamesFile, format_games)


def _check_track(track_id: str, track_ids: Collection[str]) -> None:
    if track_id not in track_ids:
        raise EditError([f"{track_id} is not a track in the draft"])


def _set_key(path: Path, track_id: str, key: str, value: Any, families: list[str]) -> None:
    _set_keys(path, track_id, {key: value}, families)


def _set_keys(path: Path, track_id: str, keys: dict[str, Any], families: list[str]) -> None:
    """Sets keys of a track's override in one write (each before its note, which stays last), or
    removes a key whose value is None. An override left empty is removed."""
    data = _load(path)
    tracks: dict[str, Any] = data.setdefault("tracks", {})
    entry = dict(tracks.get(track_id, {}))
    for key, value in keys.items():
        entry = {k: v for k, v in entry.items() if k != key}
        if value is not None:
            entry = {**entry, key: value} if key == "note" else _before_note(entry, key, value)
    if entry:
        data["tracks"] = _put(tracks, track_id, entry, families)
    else:
        tracks.pop(track_id, None)
    _write(path, data, Overrides, lambda d: format_overrides(d, families))


def _streams_json(streams: Streams) -> dict[str, Any]:
    data: dict[str, Any] = {}
    if streams.accept:
        data["accept"] = [s.model_dump(exclude_defaults=True) for s in streams.accept]
    if streams.reject:
        data["reject"] = list(streams.reject)
    return data


def _before_note(entry: dict[str, Any], key: str, value: Any) -> dict[str, Any]:
    """`entry` with `key` set, placed before the note, which stays last."""
    rest = {k: v for k, v in entry.items() if k != "note"}
    note = {"note": entry["note"]} if "note" in entry else {}
    return {**rest, key: value, **note}


def _put(tracks: dict[str, Any], key: str, value: Any, families: list[str]) -> dict[str, Any]:
    """Replaces `key` in place, or inserts it after the last key of its family (in family order)."""
    if key in tracks:
        return {k: value if k == key else v for k, v in tracks.items()}
    rank = _rank(key, families)
    items = list(tracks.items())
    at = next((i for i, (k, _) in enumerate(items) if _rank(k, families) > rank), len(items))
    return dict([*items[:at], (key, value), *items[at:]])


def _rank(key: str, families: list[str]) -> int:
    family = track_family(key, families)
    return families.index(family) if family is not None else len(families)


def _load(path: Path) -> dict[str, Any]:
    data: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
    return data


def _validated[M: BaseModel](model: type[M], data: Any) -> M:
    try:
        return model.model_validate(data)
    except ValidationError as e:
        raise EditError(
            [
                f"{'.'.join(str(p) for p in err['loc']) or 'value'}: {err['msg']}"
                for err in e.errors()
            ]
        ) from e


def _write(
    path: Path, data: dict[str, Any], model: type[BaseModel], fmt: Callable[[Any], str]
) -> None:
    text = fmt(data)
    _validated(model, json.loads(text))
    temp = path.with_name(path.name + ".tmp")
    temp.write_text(text, encoding="utf-8", newline="\n")
    os.replace(temp, path)
