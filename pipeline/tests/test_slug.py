import pytest

from vgmixr_pipeline.catalog.slug import slugify, track_id


@pytest.mark.parametrize(
    ("title", "slug"),
    [
        ("Route 101", "route-101"),
        ("Battle! (Team Aqua / Team Magma)", "battle-team-aqua-team-magma"),
        ("Battle! (Wild Pokémon)", "battle-wild-pokemon"),
        ("Trainers' Eyes Meet (Tuber♀)", "trainers-eyes-meet-tuber-female"),
        ("Pokémon Trainer♂", "pokemon-trainer-male"),
        ("Black & White", "black-and-white"),
        ("Giovanni’s Theme", "giovannis-theme"),
        ("Battle! (Frontier Brain—Hoenn Version)", "battle-frontier-brain-hoenn-version"),
        ("Special Program: \"Let's Ask Prof. Rowan!\"", "special-program-lets-ask-prof-rowan"),
        ("Poké Mart", "poke-mart"),
        ("  Opening Movie: Double Battles  ", "opening-movie-double-battles"),
    ],
)
def test_slugify(title: str, slug: str) -> None:
    assert slugify(title) == slug


def test_track_id_prefixes_family() -> None:
    assert track_id("rse", "Route 101") == "rse-route-101"


def test_slugify_rejects_titles_without_letters_or_digits() -> None:
    with pytest.raises(ValueError):
        slugify("!!!")
