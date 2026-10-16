"""Jingles and fanfares are left out of the catalog (docs/roadmap.md, step 16).

A track is a jingle if it's at most 15 s long, or if its title names a short event sound. Titles
matter for tracks without a known duration (e.g. Legends: Z-A). An override can bring one back.
"""

import re

MAX_JINGLE_MS = 15_000

_JINGLE_TITLES = re.compile(
    r"^(Pokémon Healed|Level Up|Obtained|Received|Picked Up|Caught a Pokémon|You Caught"
    r"|Pokédex Evaluation|Move Deleted)\b"
    r"|Your Pokémon Evolved",
    re.IGNORECASE,
)


def is_jingle(title: str, duration_ms: int | None) -> bool:
    if duration_ms is not None and duration_ms <= MAX_JINGLE_MS:
        return True
    return _JINGLE_TITLES.search(title) is not None
