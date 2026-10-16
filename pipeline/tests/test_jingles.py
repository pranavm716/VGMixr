import pytest

from vgmixr_pipeline.catalog.jingles import is_jingle


def test_fifteen_seconds_or_less_is_a_jingle() -> None:
    assert is_jingle("Win", 15_000)
    assert not is_jingle("Route 101", 16_000)


def test_unknown_duration_is_judged_by_title_only() -> None:
    assert not is_jingle("Route 101", None)
    assert is_jingle("Obtained a Mega Stone!", None)


@pytest.mark.parametrize(
    "title",
    [
        "Pokémon Healed",
        "Level Up!",
        "Obtained a TM!",
        "Obtained a Z-Crystal!",
        "Received Battle Points! (Hoenn Version)",
        "Picked Up an Item!",
        "Caught a Pokémon!",
        "You Caught a Pokémon!",
        "Congratulations! Your Pokémon Evolved!",
        "Pokédex Evaluation... You're on Your Way!",
        "Move Deleted",
    ],
)
def test_jingle_titles_regardless_of_duration(title: str) -> None:
    assert is_jingle(title, 60_000)


@pytest.mark.parametrize(
    "title",
    [
        "Congratulations on Entering the Hall of Fame!",
        "Congratulations!",
        "Evolution",
        "Victory! (Wild Pokémon)",
    ],
)
def test_long_tracks_with_similar_titles_are_kept(title: str) -> None:
    assert not is_jingle(title, 60_000)
