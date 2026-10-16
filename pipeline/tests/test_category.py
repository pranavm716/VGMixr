import pytest

from vgmixr_pipeline.catalog.category import guess_category


@pytest.mark.parametrize(
    ("title", "category"),
    [
        # battle
        ("Battle! (Wild Pokémon)", "battle"),
        ("Battle! (Team Aqua / Team Magma)", "battle"),
        ("Battle! (Frontier Brain—Hoenn Version)", "battle"),
        ("Final Battle! (Rival)", "battle"),
        ("Victory! (Trainer Battle)", "battle"),
        ("Trainers' Eyes Meet (Youngster)", "battle"),
        ("Trainers' eyes Meet (Twins)", "battle"),
        ("A Trainer Appears (Girl Version)", "battle"),
        ("Battle! (Ultra Necrozma)", "battle"),
        ("Decisive Battle! (Eternatus)", "battle"),
        ("Champion Alder", "battle"),
        # route
        ("Route 101", "route"),
        ("Route 10 on Ula'ula Island", "route"),
        ("Road to Viridian City: Leaving Pallet Town", "route"),
        ("Cycling", "route"),
        ("Bicycle", "route"),
        ("Surf", "route"),
        ("Crossing the Sea", "route"),
        ("Obsidian Fieldlands (Theme 1)", "route"),
        ("Alabaster Icelands 1–2", "route"),
        ("North Province (Area Two)", "route"),
        ("Wild Area (Day)", "route"),
        ("Crown Tundra", "route"),
        ("Mahalo Trail", "route"),
        # dungeon
        ("Petalburg Woods", "dungeon"),
        ("Viridian Forest", "dungeon"),
        ("Caves of Mt. Moon", "dungeon"),
        ("Pokémon Tower", "dungeon"),
        ("Rocket Hideout", "dungeon"),
        ("Victory Road", "dungeon"),
        ("Distortion World", "dungeon"),
        ("Ultra Space", "dungeon"),
        ("Silph Co.", "dungeon"),
        # town
        ("Littleroot Town", "town"),
        ("Rustboro City", "town"),
        ("Jubilife Village (Early Game)", "town"),
        ("Cinnabar Island Theme", "town"),
        ("Pokémon Center", "town"),
        ("Poké Mart", "town"),
        ("Birch Pokémon Lab", "town"),
        ("Pokémon Gym", "town"),
        ("Battle Tower (Hoenn Version)", "town"),
        ("Battle Frontier", "town"),
        ("Rocket Game Corner", "town"),
        # event
        ("Opening Movie: Double Battles", "event"),
        ("Title Screen: Main Theme", "event"),
        ("Ending Theme", "event"),
        ("Hall of Fame", "event"),
        ("Evolution", "event"),
        ("Team Magma Appears!", "event"),
        ("Guzma's Theme", "event"),
        ("Theme of Jessie and James", "event"),
        ("Super Contest: Cuteness", "event"),
        ("Mallow's Trial", "event"),
        ("A Captain's Trial Begins!", "event"),
        # other
        ("H-Help Me!", "other"),
        ("Interviewers", "other"),
    ],
)
def test_guess_category(title: str, category: str) -> None:
    assert guess_category(title) == category
