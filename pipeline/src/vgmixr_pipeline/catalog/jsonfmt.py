"""The canonical format of the committed data files in pipeline/data/, written by the review tool.

It is the hand style the files started with, so a tool edit is a one-line diff: objects down to a
file's collections are expanded, each collection entry is one line, entries of different families
are separated by a blank line, and in overrides.json a long entry's note moves to a continuation
line aligned under its first key. Lists are never reordered (tracklist order sets track order).

uv run python -m vgmixr_pipeline.catalog.jsonfmt   # rewrites the data files in this format
"""

import json
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from vgmixr_pipeline.catalog.config import DATA_DIR

# Matches the hand-written file: its unwrapped lines reach 134 chars, wrapped ones start at 151.
WIDTH = 140
WRAP_KEY = "note"

# (entry key or None for a list entry, entry) -> its family, for blank lines between families.
type FamilyOf = Callable[[str | None, Any], str | None]
type Path_ = tuple[str, ...]


@dataclass(frozen=True)
class Collection:
    family: FamilyOf | None = None
    # Lines longer than this move WRAP_KEY to a continuation line; None never wraps.
    width: int | None = None


def games_families(games: dict[str, Any]) -> list[str]:
    """Family ids in the games file's order; a game without `family` is its own family."""
    return list(dict.fromkeys(g.get("family", g["id"]) for g in games["games"]))


def track_family(key: str, families: list[str]) -> str | None:
    """A track id's family: the longest family id it starts with, followed by a hyphen."""
    matches = [f for f in families if key.startswith(f + "-")]
    return max(matches, key=len, default=None)


def _field_family(_: str | None, entry: Any) -> str | None:
    family = entry.get("family") if isinstance(entry, dict) else None
    return family if isinstance(family, str) else None


def format_games(data: dict[str, Any]) -> str:
    return format_data(
        data,
        {
            ("games",): Collection(),
            ("tracklists",): Collection(_field_family),
            ("youtube", "playlists"): Collection(),
            ("youtube", "albums"): Collection(),
            ("youtube", "aliases"): Collection(),
        },
    )


def format_overrides(data: dict[str, Any], families: list[str], width: int = WIDTH) -> str:
    return format_data(
        data,
        {
            ("tracks",): Collection(lambda key, _: track_family(key or "", families), width),
            ("extras",): Collection(_field_family, width),
        },
    )


def format_data(data: Any, collections: dict[Path_, Collection]) -> str:
    return _value(data, (), 0, collections) + "\n"


def _value(value: Any, path: Path_, indent: int, collections: dict[Path_, Collection]) -> str:
    if path in collections and value:
        return _collection(value, indent, collections[path])
    expand = isinstance(value, dict) and value and any(p[: len(path)] == path for p in collections)
    if not expand:
        return _compact(value)
    pad = " " * (indent + 2)
    children = [
        f"{pad}{_compact(k)}: {_value(v, (*path, k), indent + 2, collections)}"
        for k, v in value.items()
    ]
    return "{\n" + ",\n".join(children) + "\n" + " " * indent + "}"


def _collection(value: dict[str, Any] | list[Any], indent: int, collection: Collection) -> str:
    pad = " " * (indent + 2)
    items: list[tuple[str | None, Any]] = (
        list(value.items()) if isinstance(value, dict) else [(None, v) for v in value]
    )
    text = ""
    previous: str | None = None
    for i, (key, entry) in enumerate(items):
        family = collection.family(key, entry) if collection.family else None
        if i > 0:
            text += ",\n\n" if family != previous else ",\n"
        previous = family
        prefix = pad + (f"{_compact(key)}: " if key is not None else "")
        text += _entry(prefix, entry, collection.width)
    opening, closing = ("{", "}") if isinstance(value, dict) else ("[", "]")
    return f"{opening}\n{text}\n{' ' * indent}{closing}"


def _entry(prefix: str, entry: Any, width: int | None) -> str:
    line = prefix + _compact(entry)
    if width is None or len(line) <= width or not isinstance(entry, dict):
        return line
    keys = list(entry)
    at = keys.index(WRAP_KEY) if WRAP_KEY in keys else 0
    if at == 0:
        return line
    # The note and any keys after it go to the continuation line, in order.
    head = ", ".join(_pair(k, entry[k]) for k in keys[:at])
    tail = ", ".join(_pair(k, entry[k]) for k in keys[at:])
    first = prefix + "{ "
    return f"{first}{head},\n{' ' * len(first)}{tail} }}"


def _pair(key: str, value: Any) -> str:
    return f"{_compact(key)}: {_compact(value)}"


def _compact(value: Any) -> str:
    if isinstance(value, dict):
        return "{ " + ", ".join(_pair(k, v) for k, v in value.items()) + " }" if value else "{}"
    if isinstance(value, list):
        if not value:
            return "[]"
        inner = ", ".join(_compact(v) for v in value)
        nested = any(isinstance(v, dict | list) for v in value)
        return f"[ {inner} ]" if nested else f"[{inner}]"
    return json.dumps(value, ensure_ascii=False)


def _rewrite(path: Path, text: str) -> None:
    if path.read_text(encoding="utf-8") != text:
        path.write_text(text, encoding="utf-8", newline="\n")
        print(f"Rewrote {path}.")


def main() -> None:
    games_path, overrides_path = DATA_DIR / "games.json", DATA_DIR / "overrides.json"
    games = json.loads(games_path.read_text(encoding="utf-8"))
    overrides = json.loads(overrides_path.read_text(encoding="utf-8"))
    _rewrite(games_path, format_games(games))
    _rewrite(overrides_path, format_overrides(overrides, games_families(games)))


if __name__ == "__main__":
    main()
