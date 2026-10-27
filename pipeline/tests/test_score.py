"""Scoring a YouTube candidate against a track (docs/roadmap.md, step 17)."""

import pytest

from vgmixr_pipeline.catalog.score import (
    Candidate,
    Kind,
    Names,
    TrackFacts,
    Verdict,
    classify,
    duration_fits,
    red_flags,
    score,
    title_similarity,
)
from vgmixr_pipeline.manifest.models import Game


def game(id_: str, name: str, short: str, family: str, generation: int = 1) -> Game:
    return Game(id=id_, name=name, shortName=short, color="#000000", generation=generation,
                platform="P", family=family)  # fmt: skip


GAMES = [
    game("red", "Red", "R", "rby"),
    game("blue", "Blue", "B", "rby"),
    game("yellow", "Yellow", "Y", "rby"),
    game("gold", "Gold", "G", "gsc", 2),
    game("silver", "Silver", "S", "gsc", 2),
    game("crystal", "Crystal", "C", "gsc", 2),
    game("ruby", "Ruby", "R", "rse", 3),
    game("sapphire", "Sapphire", "S", "rse", 3),
    game("emerald", "Emerald", "E", "rse", 3),
    game("firered", "FireRed", "FR", "frlg", 3),
    game("leafgreen", "LeafGreen", "LG", "frlg", 3),
    game("diamond", "Diamond", "D", "dppt", 4),
    game("pearl", "Pearl", "P", "dppt", 4),
    game("platinum", "Platinum", "Pt", "dppt", 4),
    game("heartgold", "HeartGold", "HG", "hgss", 4),
    game("soulsilver", "SoulSilver", "SS", "hgss", 4),
    game("black", "Black", "B", "bw", 5),
    game("white", "White", "W", "bw", 5),
    game("black-2", "Black 2", "B2", "b2w2", 5),
    game("white-2", "White 2", "W2", "b2w2", 5),
    game("omega-ruby", "Omega Ruby", "OR", "oras", 6),
    game("alpha-sapphire", "Alpha Sapphire", "AS", "oras", 6),
    game("lets-go-pikachu", "Let's Go, Pikachu!", "LGP", "lgpe", 7),
    game("lets-go-eevee", "Let's Go, Eevee!", "LGE", "lgpe", 7),
    game("brilliant-diamond", "Brilliant Diamond", "BD", "bdsp", 8),
    game("shining-pearl", "Shining Pearl", "SP", "bdsp", 8),
]
NAMES = Names.build(GAMES, {"lgpe": ["Let's Go"]})


def facts(
    title: str = "Route 101",
    family: str = "rse",
    games: tuple[str, ...] = ("ruby", "sapphire", "emerald"),
    duration_ms: int | None = 60_000,
    position: float | None = None,
) -> TrackFacts:
    return TrackFacts(family=family, games=frozenset(games), title=title,
                      duration_ms=duration_ms, position=position)  # fmt: skip


def candidate(
    title: str,
    kind: Kind = "search",
    duration_ms: int | None = 120_000,
    games: tuple[str, ...] = (),
    position: float | None = None,
    channel: str | None = None,
) -> Candidate:
    return Candidate(video_id="abcdefghijk", title=title, duration_ms=duration_ms,
                     channel=channel, kind=kind, games=frozenset(games),
                     position=position)  # fmt: skip


# Title similarity


@pytest.mark.parametrize(
    "video",
    [
        "Route 101",
        "Pokémon Ruby/Sapphire/Emerald - Route 101",
        "Route 101 - Pokemon Ruby & Sapphire OST",
        "#12 Route 101 [HQ]",
        "12. Route 101 (GBA)",
        "Pokémon Emerald Music: Route 101",
        "Route 101 | Pokémon RSE Soundtrack",
    ],
)
def test_noise_is_stripped_before_comparing(video: str) -> None:
    assert title_similarity(facts("Route 101"), video, NAMES) == 1.0


@pytest.mark.parametrize(
    "video", ["The Legend of Zelda OST - Hateno Village", "Hateno Village | Zelda Music"]
)
def test_zelda_franchise_words_are_noise(video: str) -> None:
    assert title_similarity(facts("Hateno Village"), video, NAMES) == 1.0


def test_zeldas_in_a_track_title_is_still_compared() -> None:
    assert title_similarity(facts("Zelda's Wish"), "Zelda's Wish", NAMES) == 1.0
    assert title_similarity(facts("Zelda's Wish"), "Wish", NAMES) < 1.0


def test_accents_ampersands_and_apostrophes_compare_equal() -> None:
    track = facts("Battle! (Wild Pokémon)")
    assert title_similarity(track, "Battle Wild Pokemon", NAMES) == 1.0
    track = facts("Trainers' Eyes Meet (Boy & Girl)")
    assert title_similarity(track, "Trainers Eyes Meet - Boy and Girl", NAMES) == 1.0


def test_different_numbers_score_low() -> None:
    assert title_similarity(facts("Route 1"), "Route 10", NAMES) < 0.6
    assert title_similarity(facts("Route 209 (Night)"), "Route 209 (Night)", NAMES) == 1.0


def test_unrelated_titles_score_low() -> None:
    assert title_similarity(facts("Littleroot Town"), "Battle! Wild Pokémon", NAMES) < 0.4


def test_reordered_words_still_match() -> None:
    assert title_similarity(facts("Battle! (Gym Leader)"), "Gym Leader Battle", NAMES) > 0.85


def test_game_names_in_the_track_title_are_kept() -> None:
    black = facts("Opelucid City (Pokémon Black)", family="b2w2", games=("black-2",))
    assert title_similarity(black, "Opelucid City (Pokémon Black)", NAMES) == 1.0
    assert title_similarity(black, "Opelucid City", NAMES) < 1.0


# Game evidence


def evidence(track: TrackFacts, video: str, **kwargs: object) -> str:
    return NAMES.evidence(track, candidate(video, **kwargs))  # type: ignore[arg-type]


@pytest.mark.parametrize(
    ("track", "video"),
    [
        (facts(), "Route 101 - Pokémon Omega Ruby & Alpha Sapphire"),
        (facts(), "Pokemon ORAS - Route 101"),
        (facts(family="gsc", games=("gold",)), "Pokemon HeartGold & SoulSilver - Route 1"),
        (facts(family="dppt", games=("diamond",)), "Pokémon Brilliant Diamond - Route 201"),
        (facts(family="rby", games=("yellow",)), "Let's Go Pikachu - Route 1"),
        (facts(family="rby", games=("red",)), "Pokemon FireRed - Route 1"),
        (facts(), "Pokémon Mystery Dungeon - Route 101"),
    ],
    ids=["oras", "oras-label", "hgss", "bdsp", "lgpe", "frlg", "spin-off"],
)
def test_remakes_and_other_games_are_wrong(track: TrackFacts, video: str) -> None:
    assert evidence(track, video) == "wrong"


@pytest.mark.parametrize(
    ("track", "video"),
    [
        (facts(), "Pokémon Ruby/Sapphire/Emerald - Route 101"),
        (facts(games=("ruby",)), "Pokemon Sapphire - Route 101"),
        (facts(family="gsc", games=("gold",)), "Pokemon Gold - Route 29"),
        (facts(family="dppt", games=("diamond",)), "Pokemon Diamond and Pearl - Route 201"),
        (facts(family="hgss", games=("heartgold",)), "HeartGold - Route 29"),
    ],
    ids=["family", "same-family-other-version", "gold", "and-chain", "compound-name"],
)
def test_naming_the_tracks_family_is_right(track: TrackFacts, video: str) -> None:
    assert evidence(track, video) == "right"


def test_naming_both_the_family_and_its_remake_is_right() -> None:
    assert (
        evidence(facts(), "Pokémon Ruby/Sapphire vs Omega Ruby/Alpha Sapphire - Route 101")
        == "right"
    )


def test_common_words_are_not_game_names_outside_pokemon_chains() -> None:
    track = facts(family="rby", games=("red", "blue", "yellow"), title="Mt. Moon")
    assert evidence(track, "Mt. Moon Gold Edition") == "neutral"
    assert evidence(facts(), "Route 101 - Pokémon Gold") == "wrong"


def test_names_in_the_track_title_are_not_evidence() -> None:
    track = facts(family="b2w2", games=("black-2",), title="Opelucid City (Pokémon Black)")
    assert evidence(track, "Opelucid City (Pokémon Black)") == "neutral"


def test_a_playlist_tagged_with_the_tracks_games_is_right() -> None:
    assert evidence(facts(), "Route 101", kind="playlist", games=("ruby",)) == "right"
    assert evidence(facts(), "Route 101", kind="playlist", games=("omega-ruby",)) == "neutral"


def test_the_channel_can_name_the_family() -> None:
    assert evidence(facts(), "Route 101", channel="Pokémon Emerald OST") == "right"


def test_no_names_is_neutral() -> None:
    assert evidence(facts(), "Route 101 Music") == "neutral"


# Red flags and duration


@pytest.mark.parametrize(
    "video",
    [
        "Route 101 Remix",
        "Route 101 (Orchestral Cover)",
        "Route 101 - Piano Arrangement",
        "Route 101 Extended",
        "Route 101 1 Hour",
        "Route 101 [10 Hours]",
        "Route 101 lofi",
        "Route 101 (slowed + reverb)",
        "Route 101 Remastered",
        "Route 101 - Metal Version",
    ],
)
def test_red_flags(video: str) -> None:
    assert red_flags(facts(), video)


def test_words_in_the_track_title_are_not_red_flags() -> None:
    track = facts(title="Route 101 (Remix)")
    assert red_flags(track, "Route 101 (Remix)") == []
    assert red_flags(facts(), "Route 101 (Rocket Hideout)") == []


def test_8_bit_is_a_red_flag_except_on_game_boy_families() -> None:
    assert red_flags(facts(), "Route 101 8-bit")
    gb = facts(family="rby", games=("red",), title="Route 1")
    assert red_flags(gb, "Route 1 8-bit", game_boy=True) == []


@pytest.mark.parametrize(
    ("expected", "actual", "fits"),
    [
        (60_000, 60_000, True),
        (60_000, 46_000, True),
        (60_000, 23_000, False),
        (60_000, 200_000, True),
        (60_000, 215_000, False),
        (None, 3_000_000, True),
        (60_000, None, True),
    ],
)
def test_duration_fits(expected: int | None, actual: int | None, fits: bool) -> None:
    assert duration_fits(expected, actual) is fits


# The score


def test_a_perfect_playlist_entry_scores_1() -> None:
    entry = candidate("Route 101", kind="playlist", games=("ruby",), position=0.2,
                      duration_ms=60_000)  # fmt: skip
    result = score(facts(position=0.2), entry, NAMES)
    assert result.value == pytest.approx(1.0)
    assert classify(result.value) is Verdict.ACCEPT


def test_a_clean_search_hit_naming_the_game_is_accepted() -> None:
    hit = candidate("Pokémon Ruby & Sapphire - Route 101")
    assert classify(score(facts(), hit, NAMES).value) is Verdict.ACCEPT


def test_a_wrong_game_hit_is_rejected_even_with_the_same_title() -> None:
    hit = candidate("Pokémon Omega Ruby & Alpha Sapphire - Route 101")
    result = score(facts(), hit, NAMES)
    assert classify(result.value) is Verdict.REJECT
    assert any("wrong game" in r for r in result.reasons)


def test_a_red_flag_drops_an_accept_out_of_the_accept_band() -> None:
    hit = candidate("Pokémon Ruby & Sapphire - Route 101 Remix")
    result = score(facts(), hit, NAMES)
    assert classify(result.value) is not Verdict.ACCEPT
    assert "red flag: remix" in result.reasons


def test_an_hour_long_upload_is_not_accepted() -> None:
    hit = candidate("Pokémon Ruby & Sapphire - Route 101", duration_ms=3_600_000)
    assert classify(score(facts(), hit, NAMES).value) is not Verdict.ACCEPT


def test_an_album_chapter_is_not_penalized_for_the_whole_videos_length() -> None:
    chapter = candidate("Route 101", kind="album", games=("ruby",), duration_ms=70_000)
    assert classify(score(facts(), chapter, NAMES).value) is Verdict.ACCEPT


def test_position_breaks_ties_between_identical_titles() -> None:
    near = candidate("Route 101", kind="playlist", games=("ruby",), position=0.21)
    far = candidate("Route 101", kind="playlist", games=("ruby",), position=0.9)
    track = facts(position=0.2)
    assert score(track, near, NAMES).value > score(track, far, NAMES).value


@pytest.mark.parametrize(
    ("value", "verdict"),
    [(0.95, Verdict.ACCEPT), (0.8, Verdict.ACCEPT), (0.79, Verdict.REVIEW),
     (0.5, Verdict.REVIEW), (0.49, Verdict.REJECT)],
)  # fmt: skip
def test_classify(value: float, verdict: Verdict) -> None:
    assert classify(value) is verdict


# Fixes from the first real run


def test_compound_names_also_match_as_two_words() -> None:
    track = facts(family="gsc", games=("gold",), title="Route 29")
    assert evidence(track, "Pokemon Heart Gold/Soul Silver OST - Route 29") == "wrong"
    hgss = facts(family="hgss", games=("heartgold",), title="Route 29")
    assert title_similarity(hgss, "Pokemon Heart Gold/Soul Silver - Route 29", NAMES) == 1.0


def test_disc_and_track_markers_are_noise() -> None:
    track = facts(title="New Bark Town")
    assert title_similarity(track, "OST (D1;T4) New Bark Town (HD)", NAMES) == 1.0
    assert title_similarity(track, "Disc 1 Track 4 - New Bark Town", NAMES) == 1.0


def test_words_common_to_a_playlist_are_noise() -> None:
    entry = Candidate("abcdefghijk", "New Bark Town ~ Johto Sounds Remastered Collection", None,
                      None, "playlist", noise=frozenset({"johto", "sounds", "remastered",
                      "collection"}))  # fmt: skip
    assert title_similarity(facts(title="New Bark Town"), entry.title, NAMES, entry.noise) == 1.0
    assert red_flags(facts(), entry.title, noise=entry.noise) == []


def test_a_title_inside_the_other_is_close() -> None:
    crystal = facts(title="Title Screen (Crystal)")
    assert 0.8 <= title_similarity(crystal, "Title (Crystal)", NAMES) < 1.0
    oak = facts(title="Professor Oak's Laboratory")
    assert 0.8 <= title_similarity(oak, "Oak's Laboratory", NAMES) < 1.0
    single = facts(title="Battle! (Gym Leader)")
    assert title_similarity(single, "Battle", NAMES) < 0.8


def test_a_one_loop_rip_of_a_two_loop_album_track_fits() -> None:
    assert duration_fits(215_000, 98_000)
    assert not duration_fits(215_000, 80_000)


def test_a_short_title_inside_a_long_one_is_not_close() -> None:
    road = facts(title="Road to Fuchsia City: Leaving Lavender Town")
    assert title_similarity(road, "Fuchsia City", NAMES) < 0.7
    sevii = facts(title="Sevii Islands: Four & Five Islands")
    assert title_similarity(sevii, "Sevii Islands", NAMES) < 0.8


@pytest.mark.parametrize(
    ("title", "video"),
    [
        ("Battle! (Gym Leader—Kanto Version)", "Battle! Gym Leader (Johto)"),
        ("Route 209 (Night)", "Route 209 (Day)"),
        ("Battle! (Wild Pokémon)", "Wild Pokémon Battle (Johto)"),
    ],
)
def test_a_variant_the_track_lacks_is_another_track(title: str, video: str) -> None:
    assert title_similarity(facts(title=title), video, NAMES) < 0.6


def test_a_variant_missing_from_the_video_is_only_close() -> None:
    track = facts(title="Route 209 (Night)")
    assert 0.8 <= title_similarity(track, "Route 209", NAMES) < 1.0


def test_joke_rip_channels_are_red_flags() -> None:
    joke = candidate("Battle! (Steven) - Pokémon Ruby & Sapphire", channel="SiIvaGunner")
    result = score(facts(title="Battle! (Steven)"), joke, NAMES)
    assert "red flag: channel SiIvaGunner" in result.reasons
    assert classify(result.value) is Verdict.REJECT


def test_guides_are_red_flags() -> None:
    assert red_flags(facts(title="Dive"), "How To Get HM08 Dive in Pokemon Ruby/Sapphire/Emerald")


# Fixes from the labeled set


@pytest.mark.parametrize(
    "video",
    [
        "[GBC] Opening Movie - Pokémon Ruby & Sapphire Music",
        'Opening Movie (From "Pokemon Ruby & Sapphire")',
        "Opening Movie - Pokémon Ruby & Sapphire (Spanish Version)",
        "Opening Movie / Pokémon Ruby & Sapphire [Music Box]",
        "Pokémon RSE | Opening Movie [DS] Transcription",
        "Pokémon Ruby - Opening Movie (MIDI version)",
        "Pokémon Ruby, Sapphire, and Emerald - Opening Movie - Without Melody",
    ],
)
def test_versions_that_are_not_the_game_audio_are_red_flags(video: str) -> None:
    assert red_flags(facts(title="Opening Movie"), video)


def test_game_boy_words_are_no_red_flag_on_game_boy_games() -> None:
    gb = facts(family="gsc", games=("gold",), title="Route 29")
    assert red_flags(gb, "Pokémon Gold (GBC) - Route 29", game_boy=True) == []


def test_separated_family_labels_are_names() -> None:
    oras = facts(family="oras", games=("omega-ruby",), title="Trainers' School")
    assert evidence(oras, "Pokemon R/S/E Soundtrack: Trainer's School") == "wrong"
    hgss = facts(family="hgss", games=("heartgold",), title="Safari Zone")
    assert evidence(hgss, "Pokémon HG/SS - Safari Zone") == "right"


def test_gb_sounds_must_match_on_later_games() -> None:
    gb = facts(family="hgss", games=("heartgold",), title="Route 24 (GB Sounds)")
    assert title_similarity(gb, "Pokémon HGSS OST - Route 24", NAMES) < 0.6
    assert title_similarity(gb, "Pokémon HGSS OST - Route 24 (GB Sound)", NAMES) > 0.8
    plain = facts(family="hgss", games=("heartgold",), title="Poké Mart")
    assert title_similarity(plain, "PokéMart (GB Sound)", NAMES) < 0.6


def test_a_video_title_missing_a_descriptive_word_is_closer_than_one_with_an_extra_word() -> None:
    moon = facts(title="Caves of Mt. Moon")
    assert title_similarity(moon, "Mt. Moon", NAMES) >= 0.87
    safari = facts(title="Safari Zone")
    assert title_similarity(safari, "Safari Zone Gate", NAMES) < 0.87


def test_a_video_title_missing_a_role_is_only_close() -> None:
    leaders = facts(title="Battle! (Team Aqua / Team Magma Leaders)")
    assert title_similarity(leaders, "Battle! Team Magma/Aqua", NAMES) < 0.87
