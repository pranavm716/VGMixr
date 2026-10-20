"""Track ids: `<family>-<slug of title>`. Of the source's title, a source key (overrides, the review
tool); of the final title, the catalog id the manifest ships (docs/roadmap.md, steps 16, 19)."""

import re
import unicodedata

# Applied before accent stripping, since these have no letter form.
_REPLACEMENTS = {"&": " and ", "♀": " female ", "♂": " male "}
_APOSTROPHES = re.compile(r"['’‘]")
_NON_SLUG = re.compile(r"[^a-z0-9]+")


def slugify(title: str) -> str:
    text = title
    for old, new in _REPLACEMENTS.items():
        text = text.replace(old, new)
    # Drop accents only; any other symbol (e.g. an em dash) separates words.
    decomposed = unicodedata.normalize("NFKD", text)
    plain = "".join(c for c in decomposed if not unicodedata.combining(c))
    slug = _NON_SLUG.sub("-", _APOSTROPHES.sub("", plain.lower())).strip("-")
    if not slug:
        raise ValueError(f"title {title!r} has no letters or digits to slug")
    return slug


def track_id(family: str, title: str) -> str:
    return f"{family}-{slugify(title)}"
