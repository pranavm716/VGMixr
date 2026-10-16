"""Writes catalog/manifest.json from the draft and its matches (docs/roadmap.md, step 17).

A track's own sources (local files, hand-written) come first, then its matched streams; a track
left with no source is left out. The text is validated with the same parser the fixtures test, and
only a valid manifest replaces the file. `generatedAt` is kept when nothing else changed, so a
rerun neither shows a diff nor makes the app re-import.
"""

import json
import os
from pathlib import Path
from typing import Any

from vgmixr_pipeline.catalog.build import Draft
from vgmixr_pipeline.catalog.match import Match
from vgmixr_pipeline.manifest.models import Manifest, Track
from vgmixr_pipeline.manifest.parse import SUPPORTED_SCHEMA_VERSION, Invalid, parse


def build_manifest(draft: Draft, matches: list[Match], generated_at: str) -> Manifest:
    streams = {m.track_id: m.streams for m in matches}
    tracks = []
    for t in draft.tracks:
        sources = [*t.sources, *streams.get(t.id, [])]
        if sources:
            tracks.append(
                Track(
                    id=t.id,
                    family=t.family,
                    games=t.games,
                    title=t.title,
                    category=t.category,
                    order=t.order,
                    sources=sources,
                )
            )
    return Manifest(
        schemaVersion=SUPPORTED_SCHEMA_VERSION,
        generatedAt=generated_at,
        games=draft.games,
        tracks=tracks,
    )


def write_manifest(path: Path, draft: Draft, matches: list[Match], generated_at: str) -> list[str]:
    """Writes the manifest unless it's invalid; returns the validation errors."""
    data = _json(build_manifest(draft, matches, generated_at))
    old = _read(path)
    if old is not None and {**old, "generatedAt": None} == {**data, "generatedAt": None}:
        data["generatedAt"] = old["generatedAt"]
    text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    result = parse(text)
    if isinstance(result, Invalid):
        return [f"{e.code}: {e.message}" for e in result.errors]
    temp = path.with_name(path.name + ".tmp")
    temp.write_text(text, encoding="utf-8", newline="\n")
    os.replace(temp, path)
    return []


def _json(manifest: Manifest) -> dict[str, Any]:
    """The manifest as JSON data, leaving out unset optional fields rather than writing null."""
    data: dict[str, Any] = manifest.model_dump(exclude_none=True)
    for track in data["tracks"]:
        if not track["replaces"]:
            del track["replaces"]
    return data


def _read(path: Path) -> dict[str, Any] | None:
    try:
        data: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None
    return data if isinstance(data, dict) and "generatedAt" in data else None
