"""ids.json: the frozen catalog ids, source key -> the id the manifest ships (docs/roadmap.md,
step 12e). A track's id freezes the first time it ships, so a rename changes only its title.
Entries are never removed: an excluded track gets its id back when included again, and a vanished
key's id stays taken. Moving an id onto a new key is a hand edit.
"""

import json
import os
from pathlib import Path

from vgmixr_pipeline.catalog.config import DATA_DIR
from vgmixr_pipeline.catalog.jsonfmt import format_ids, track_family
from vgmixr_pipeline.catalog.slug import track_id

IDS_PATH = DATA_DIR / "ids.json"


def load_ids(path: Path = IDS_PATH) -> dict[str, str]:
    """The frozen ids; none when the file doesn't exist yet."""
    if not path.exists():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or not all(
        isinstance(k, str) and isinstance(v, str) for k, v in data.items()
    ):
        raise ValueError(f"{path.name}: must map source keys to catalog ids (strings)")
    return data


def catalog_id(frozen: dict[str, str], key: str, family: str, title: str) -> str:
    """A track's catalog id: frozen in ids.json, else the slug of its final title."""
    return frozen.get(key) or track_id(family, title)


def updated(frozen: dict[str, str], shipped: dict[str, str]) -> dict[str, str]:
    """The frozen ids plus the shipped tracks not listed yet; a listed entry never changes."""
    return {**frozen, **{k: v for k, v in shipped.items() if k not in frozen}}


def write_ids(path: Path, ids: dict[str, str], families: list[str]) -> None:
    """Writes families in games-file order and keys sorted within a family, so a new entry is a
    one-line diff wherever the track sits."""
    rank = {f: i for i, f in enumerate(families)}

    def place(key: str) -> tuple[int, str]:
        family = track_family(key, families)
        return (rank[family] if family is not None else len(families), key)

    text = format_ids({k: ids[k] for k in sorted(ids, key=place)}, families)
    temp = path.with_name(path.name + ".tmp")
    temp.write_text(text, encoding="utf-8", newline="\n")
    os.replace(temp, path)
