"""Family covers: hand-cropped square images converted into catalog/covers/<family>.webp.

The app bundles catalog/ as assets and finds a family's cover by its id, so a cover's file name
is the link to its family: no manifest field. Sources are cropped square by hand (the alignment is
a judgement call); this only checks, resizes and re-encodes them.
"""

from dataclasses import dataclass
from pathlib import Path
from typing import Any

from PIL import Image, UnidentifiedImageError

COVER_SIZE = 512
QUALITY = 85
# A hand crop a few pixels off square is fine; anything more is a mistake to fix at the source.
SQUARE_TOLERANCE = 0.01
# Transparent areas are flattened onto the app's background.
BACKGROUND = (0x11, 0x11, 0x11)
SOURCE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"}


class CoverError(Exception):
    pass


@dataclass(frozen=True)
class CoverResult:
    written: list[str]
    missing: list[str]
    orphans: list[str]


def family_ids(manifest: dict[str, Any]) -> set[str]:
    """A game with no `family` is its own single-game family."""
    return {game.get("family") or game["id"] for game in manifest["games"]}


def convert_covers(src: Path, out: Path, families: set[str]) -> CoverResult:
    """Converts every image in [src]. Checks all of them first, so a bad one writes nothing."""
    sources = sorted(p for p in src.iterdir() if p.suffix.lower() in SOURCE_SUFFIXES)
    images = _load(sources, families)

    out.mkdir(parents=True, exist_ok=True)
    for family, image in images.items():
        _cover(image).save(out / f"{family}.webp", "WEBP", quality=QUALITY, method=6)

    existing = {p.stem for p in out.glob("*.webp")}
    return CoverResult(
        written=sorted(images),
        missing=sorted(families - existing),
        orphans=sorted(existing - families),
    )


def _load(sources: list[Path], families: set[str]) -> dict[str, Image.Image]:
    errors: list[str] = []
    images: dict[str, Image.Image] = {}
    for path in sources:
        family = path.stem
        if family not in families:
            errors.append(f"{path.name}: {family!r} is not a family id")
            continue
        if family in images:
            errors.append(f"{path.name}: {family!r} has more than one image")
            continue
        try:
            with Image.open(path) as opened:
                image = opened.convert("RGBA")
        except (UnidentifiedImageError, OSError) as e:
            errors.append(f"{path.name}: not a readable image ({e})")
            continue
        width, height = image.size
        if abs(width - height) > SQUARE_TOLERANCE * max(width, height):
            errors.append(f"{path.name}: {width}x{height} is not square; crop it first")
            continue
        images[family] = image
    if errors:
        raise CoverError("\n".join(errors))
    return images


def _cover(image: Image.Image) -> Image.Image:
    side = min(image.size)
    left, top = (image.width - side) // 2, (image.height - side) // 2
    square = image.crop((left, top, left + side, top + side))
    flat = Image.new("RGB", square.size, BACKGROUND)
    flat.paste(square, mask=square.getchannel("A"))
    return flat.resize((COVER_SIZE, COVER_SIZE), Image.Resampling.LANCZOS)
