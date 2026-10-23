"""Writes catalog/manifest.json from the draft and its matches (docs/roadmap.md, steps 17, 19).

A track's id is its catalog id: frozen in ids.json, else the family plus a slug of its final title
(step 12e); the draft's id is the source key, which only the pipeline sees (overrides, matches, the
review tool). Once the manifest is written, its tracks not in ids.json yet are added. A track's own
sources (local files, hand-written) come first, then its matched streams; a track left with no
source is left out. The text is validated with the same parser the fixtures test, and
only a valid manifest replaces the file. `generatedAt` is kept when nothing else changed, so a
rerun neither shows a diff nor makes the app re-import.
"""

import json
import os
from pathlib import Path
from typing import Any

from vgmixr_pipeline.catalog.build import Draft, DraftTrack
from vgmixr_pipeline.catalog.ids import catalog_id, updated, write_ids
from vgmixr_pipeline.catalog.match import Match
from vgmixr_pipeline.manifest.models import Manifest, Source, Track
from vgmixr_pipeline.manifest.parse import SUPPORTED_SCHEMA_VERSION, Invalid, parse


def build_manifest(
    draft: Draft, matches: list[Match], generated_at: str, frozen: dict[str, str] | None = None
) -> Manifest:
    """`frozen` is ids.json; a track it doesn't list ships under its final title's slug."""
    tracks = [
        Track(
            id=catalog_id(frozen or {}, t.id, t.family, t.title),
            family=t.family,
            games=t.games,
            title=t.title,
            order=t.order,
            sources=sources,
        )
        for t, sources in _shipped(draft, matches)
    ]
    return Manifest(
        schemaVersion=SUPPORTED_SCHEMA_VERSION,
        generatedAt=generated_at,
        games=draft.games,
        tracks=tracks,
    )


def shipped_ids(
    draft: Draft, matches: list[Match], frozen: dict[str, str] | None = None
) -> dict[str, str]:
    """The manifest's tracks: source key -> catalog id."""
    shipped = _shipped(draft, matches)
    return {t.id: catalog_id(frozen or {}, t.id, t.family, t.title) for t, _ in shipped}


def write_catalog(
    manifest_path: Path,
    ids_path: Path,
    draft: Draft,
    matches: list[Match],
    generated_at: str,
    frozen: dict[str, str],
    families: list[str],
) -> tuple[list[str], int]:
    """Writes the manifest, then adds its new tracks to ids.json; an invalid manifest writes
    neither. Returns the validation errors and the number of ids added."""
    errors = write_manifest(manifest_path, draft, matches, generated_at, frozen)
    if errors:
        return errors, 0
    ids = updated(frozen, shipped_ids(draft, matches, frozen))
    if ids != frozen or not ids_path.exists():
        write_ids(ids_path, ids, families)
    return [], len(ids) - len(frozen)


def _shipped(draft: Draft, matches: list[Match]) -> list[tuple[DraftTrack, list[Source]]]:
    """The tracks with a source (their own, then their matched streams); the rest don't ship."""
    streams = {m.track_id: m.streams for m in matches}
    tracks = [(t, [*t.sources, *streams.get(t.id, [])]) for t in draft.tracks]
    return [(t, sources) for t, sources in tracks if sources]


def write_manifest(
    path: Path,
    draft: Draft,
    matches: list[Match],
    generated_at: str,
    frozen: dict[str, str] | None = None,
) -> list[str]:
    """Writes the manifest unless it's invalid; returns the validation errors."""
    data = _json(build_manifest(draft, matches, generated_at, frozen))
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
