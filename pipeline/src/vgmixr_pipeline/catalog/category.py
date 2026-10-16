"""Guesses a track's category from its title. Overrides fix the misses."""

import re

# First match wins, so the order matters: "Battle Tower" is a facility, not a battle or a
# dungeon tower; "Victory Road" is a dungeon, "Victory!" a battle.
_RULES: list[tuple[str, str]] = [
    (
        "battle",
        r"^(Final |Decisive )?Battle! |^Battle! ?\(|^Battle!$|^Victory!|Trainers' Eyes Meet"
        r"|^A Trainer Appears|\bVS\.? |^Champion ",
    ),
    (
        "route",
        r"^Route \d|^Road to|\bCycling\b|\bBicycle\b|^Surf$|\bSea\b|\bTrail\b|\bProvince\b"
        r"|\bWild Area\b|\bTundra\b|(Field|High|Ice|Coast|Mire)lands\b",
    ),
    (
        "town",
        r"^Battle (Tower|Frontier|Factory|Arena|Dome|Pike|Palace|Pyramid|Hall|Castle|Arcade"
        r"|Subway|Tree|Agency|Maison)\b",
    ),
    (
        "dungeon",
        r"\bCaves?\b|\bForest\b|\bWoods\b|\bTower\b|\bMt\.|\bMountain\b|\bHideout\b|\bRuins\b"
        r"|\bMansion\b|\bTunnel\b|^Victory Road\b|\bDen\b|\bLighthouse\b|\bSilph Co\b|\bHQ\b"
        r"|\bIce Path\b|\bWorld\b|\bSpace\b",
    ),
    (
        "town",
        r"\bTown\b|\bCity\b|\bVillage\b|\bIsland\b|\bPokémon Center\b|\bPoké Mart\b|\bLab\b"
        r"|\bPokémon Gym\b|\bPokémon League\b|\bGame Corner\b",
    ),
    (
        "event",
        r"\bOpening\b|\bTitle Screen\b|\bEnding\b|\bHall of Fame\b|^Evolution\b|\bAppears!?"
        r"|'s Theme\b|^Theme of\b|\bContest\b|\bPokéathlon\b|\bTrial\b",
    ),
]
_COMPILED = [(category, re.compile(pattern, re.IGNORECASE)) for category, pattern in _RULES]


def guess_category(title: str) -> str:
    for category, pattern in _COMPILED:
        if pattern.search(title):
            return category
    return "other"
