"""Family covers: hand-cropped 16:9 images converted into catalog/covers/<family>.webp.

The app bundles catalog/ as assets and finds a family's cover by its id, so a cover's file name
is the link to its family: no manifest field. Sources are cropped to 16:9 by hand (the alignment
is a judgement call); this only checks, resizes and re-encodes them, so the app bundles small
files of one shape. 16:9 is YouTube's thumbnail shape, which user tracks show as their art.
"""

from dataclasses import dataclass
from pathlib import Path
from typing import Any

from PIL import Image, UnidentifiedImageError

# YouTube's largest thumbnail size: sharp at the full width of a phone.
COVER_SIZE = (1280, 720)
QUALITY = 85
# A hand crop a few pixels off 16:9 is fine; anything more is a mistake to fix at the source.
ASPECT_TOLERANCE = 0.01
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
        if abs(width / height / _ASPECT - 1) > ASPECT_TOLERANCE:
            errors.append(f"{path.name}: {width}x{height} is not 16:9; crop it first")
            continue
        images[family] = image
    if errors:
        raise CoverError("\n".join(errors))
    return images


_ASPECT = COVER_SIZE[0] / COVER_SIZE[1]


def _cover(image: Image.Image) -> Image.Image:
    # Trims the few pixels a hand crop is off 16:9, evenly from both sides.
    width = min(image.width, round(image.height * _ASPECT))
    height = min(image.height, round(image.width / _ASPECT))
    left, top = (image.width - width) // 2, (image.height - height) // 2
    cropped = image.crop((left, top, left + width, top + height))
    flat = Image.new("RGB", cropped.size, BACKGROUND)
    flat.paste(cropped, mask=cropped.getchannel("A"))
    return flat.resize(COVER_SIZE, Image.Resampling.LANCZOS)
