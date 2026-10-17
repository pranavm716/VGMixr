"""Edits of the committed data files. Each edit loads the raw JSON (keeping its order), changes it,
validates the result with the builder's own models, writes it in the canonical format (jsonfmt)
to a temporary file and swaps it in. An invalid edit raises EditError and writes nothing.
"""

import json
import os
from collections.abc import Callable, Collection
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ValidationError

from vgmixr_pipeline.catalog.config import Overrides, Streams
from vgmixr_pipeline.catalog.jsonfmt import format_overrides, track_family


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
    if track_id not in track_ids:
        raise EditError([f"{track_id} is not a track in the draft"])
    decision = _validated(Streams, streams)
    data = _load(path)
    tracks: dict[str, Any] = data.setdefault("tracks", {})
    entry = {k: v for k, v in tracks.get(track_id, {}).items() if k != "streams"}
    if decision.accept or decision.reject:
        entry = _before_note(entry, "streams", _streams_json(decision))
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
    path: Path, data: dict[str, Any], model: type[Overrides], fmt: Callable[[Any], str]
) -> None:
    text = fmt(data)
    _validated(model, json.loads(text))
    temp = path.with_name(path.name + ".tmp")
    temp.write_text(text, encoding="utf-8", newline="\n")
    os.replace(temp, path)
