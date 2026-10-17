import pytest

from vgmixr_pipeline.catalog.build import (
    BuildError,
    BuildResult,
    DraftTrack,
    Occurrence,
    build_tracks,
)
from vgmixr_pipeline.catalog.config import Extra, Overrides, Split, TrackOverride
from vgmixr_pipeline.manifest.models import Game, LocalSource

GAMES = [
    Game(id="red", name="Red", shortName="R", color="#DA3914", generation=1, platform="GB",
         family="rby"),
    Game(id="blue", name="Blue", shortName="B", color="#2E50D8", generation=1, platform="GB",
         family="rby"),
    Game(id="yellow", name="Yellow", shortName="Y", color="#FFD733", generation=1, platform="GB",
         family="rby"),
    Game(id="pla", name="Legends: Arceus", shortName="PLA", color="#36597B", generation=8,
         platform="Switch"),
]  # fmt: skip
RB = ("red", "blue")
Y = ("yellow",)


def occ(title: str, games: tuple[str, ...] | None = None, seconds: int | None = 60,
        family: str = "rby") -> Occurrence:  # fmt: skip
    duration = seconds * 1000 if seconds is not None else None
    return Occurrence(family, games, title, duration, f"Album / Disc 1 #{title}")


def build(occurrences: list[Occurrence], overrides: Overrides | None = None) -> BuildResult:
    return build_tracks(GAMES, occurrences, overrides or Overrides())


def ids(result: BuildResult) -> list[str]:
    return [t.id for t in result.tracks]


def track(result: BuildResult, track_id: str) -> DraftTrack:
    return next(t for t in result.tracks if t.id == track_id)


def test_one_track_per_occurrence_with_id_title_and_guessed_category() -> None:
    result = build([occ("Pallet Town Theme"), occ("Battle! (Wild Pokémon)")])
    assert ids(result) == ["rby-pallet-town-theme", "rby-battle-wild-pokemon"]
    pallet = track(result, "rby-pallet-town-theme")
    assert (pallet.family, pallet.title, pallet.category, pallet.order) == (
        "rby", "Pallet Town Theme", "town", 0
    )  # fmt: skip
    assert pallet.games is None
    assert pallet.sources == []
    assert pallet.durationMs == 60_000
    assert pallet.origins == ["Album / Disc 1 #Pallet Town Theme"]


def test_same_title_merges_and_unions_games() -> None:
    result = build([occ("Viridian Forest", RB, 60), occ("Viridian Forest", Y, 90)])
    forest = track(result, "rby-viridian-forest")
    assert ids(result) == ["rby-viridian-forest"]
    assert forest.games is None  # every rby game
    assert forest.durationMs == 90_000  # the longest
    assert len(forest.origins) == 2


def test_partial_union_lists_games_in_family_order() -> None:
    result = build([occ("Pikachu's Beach", Y), occ("Pikachu's Beach", ("red",))])
    assert track(result, "rby-pikachus-beach").games == ["red", "yellow"]


def test_orders_tracks_by_first_occurrence_per_family() -> None:
    result = build([occ("A Town"), occ("Arceus", family="pla"), occ("B City"), occ("A Town", Y)])
    assert [(t.id, t.order) for t in result.tracks] == [
        ("rby-a-town", 0),
        ("rby-b-city", 1),
        ("pla-arceus", 0),
    ]


def test_excludes_jingles_and_reports_them() -> None:
    result = build([occ("Pokémon Healed", seconds=5), occ("Route 1")])
    assert ids(result) == ["rby-route-1"]
    assert track(result, "rby-route-1").order == 0  # orders close the gap
    assert [(e.id, e.reason) for e in result.excluded] == [("rby-pokemon-healed", "jingle")]


def test_jingle_uses_the_longest_merged_duration() -> None:
    result = build([occ("Pokémon Gym", RB, 15), occ("Pokémon Gym", Y, 71)])
    assert ids(result) == ["rby-pokemon-gym"]


def test_exclude_false_keeps_a_jingle() -> None:
    overrides = Overrides(tracks={"rby-pokemon-healed": TrackOverride(exclude=False)})
    assert ids(build([occ("Pokémon Healed", seconds=5)], overrides)) == ["rby-pokemon-healed"]


def test_exclude_true_drops_a_track() -> None:
    overrides = Overrides(tracks={"rby-route-1": TrackOverride(exclude=True)})
    result = build([occ("Route 1")], overrides)
    assert ids(result) == []
    assert [(e.id, e.reason) for e in result.excluded] == [("rby-route-1", "override")]


def test_title_category_and_games_overrides_keep_the_id() -> None:
    overrides = Overrides(
        tracks={
            "rby-route-1": TrackOverride(title="Route 1 (Fixed)", category="other", games=["blue"])
        }
    )
    route = track(build([occ("Route 1")], overrides), "rby-route-1")
    assert (route.title, route.category, route.games) == ("Route 1 (Fixed)", "other", ["blue"])


def test_games_override_covering_the_family_is_omitted() -> None:
    overrides = Overrides(tracks={"rby-route-1": TrackOverride(games=["yellow", "red", "blue"])})
    assert track(build([occ("Route 1", Y)], overrides), "rby-route-1").games is None


@pytest.mark.parametrize("games", [[], ["red", "red"], ["pla"]])
def test_invalid_games_override_is_an_error(games: list[str]) -> None:
    overrides = Overrides(tracks={"rby-route-1": TrackOverride(games=games)})
    with pytest.raises(BuildError, match="rby-route-1"):
        build([occ("Route 1")], overrides)


def test_merge_into_combines_two_titles() -> None:
    overrides = Overrides(
        tracks={"rby-pallet-town": TrackOverride(mergeInto="rby-pallet-town-theme")}
    )
    result = build([occ("Pallet Town Theme", RB), occ("Pallet Town", Y)], overrides)
    assert ids(result) == ["rby-pallet-town-theme"]
    assert track(result, "rby-pallet-town-theme").games is None


def test_merge_into_an_unknown_track_is_an_error() -> None:
    overrides = Overrides(tracks={"rby-route-1": TrackOverride(mergeInto="rby-nowhere")})
    with pytest.raises(BuildError, match="rby-nowhere"):
        build([occ("Route 1")], overrides)


def test_split_moves_occurrences_of_the_given_games() -> None:
    overrides = Overrides(
        tracks={
            "rby-title-screen": TrackOverride(
                split={"rby-title-screen-yellow": Split(games=["yellow"], title="Title Screen (Y)")}
            )
        }
    )
    result = build([occ("Title Screen", RB), occ("Route 1"), occ("Title Screen", Y)], overrides)
    assert ids(result) == ["rby-title-screen", "rby-title-screen-yellow", "rby-route-1"]
    assert track(result, "rby-title-screen").games == ["red", "blue"]
    yellow = track(result, "rby-title-screen-yellow")
    assert (yellow.games, yellow.title, yellow.origins) == (
        ["yellow"],
        "Title Screen (Y)",
        ["Album / Disc 1 #Title Screen"],
    )


def test_split_that_moves_nothing_is_an_error() -> None:
    overrides = Overrides(
        tracks={"rby-route-1": TrackOverride(split={"rby-route-1-y": Split(games=["yellow"])})}
    )
    with pytest.raises(BuildError, match="rby-route-1-y"):
        build([occ("Route 1", RB)], overrides)


def test_extras_get_defaults_like_generated_tracks() -> None:
    overrides = Overrides(extras=[Extra(family="rby", title="Pikachu's Beach", games=["yellow"])])
    result = build([occ("Route 1"), occ("Arceus", family="pla")], overrides)
    beach = track(result, "rby-pikachus-beach")
    assert (beach.games, beach.category, beach.order, beach.sources) == (["yellow"], "other", 1, [])
    assert beach.durationMs is None
    assert ids(result) == ["rby-route-1", "rby-pikachus-beach", "pla-arceus"]


def test_extras_keep_explicit_id_order_category_and_sources() -> None:
    source = LocalSource(type="local", path="pla/test-tone.wav")
    extra = Extra(id="pla-test-tone", family="pla", title="Test Tone (Local)", category="other",
                  order=999, sources=[source])  # fmt: skip
    tone = track(build([], Overrides(extras=[extra])), "pla-test-tone")
    assert (tone.order, tone.category, tone.sources) == (999, "other", [source])


def test_extras_can_be_overridden() -> None:
    overrides = Overrides(
        tracks={"rby-beach": TrackOverride(category="event")},
        extras=[Extra(family="rby", title="Beach")],
    )
    assert track(build([], overrides), "rby-beach").category == "event"


def test_extra_colliding_with_a_generated_track_is_an_error() -> None:
    overrides = Overrides(extras=[Extra(family="rby", title="Route 1")])
    with pytest.raises(BuildError, match="rby-route-1"):
        build([occ("Route 1")], overrides)


def test_extra_in_an_unknown_family_is_an_error() -> None:
    with pytest.raises(BuildError, match="gsc"):
        build([], Overrides(extras=[Extra(family="gsc", title="New Bark Town")]))


def test_override_for_an_unknown_track_is_an_error() -> None:
    overrides = Overrides(tracks={"rby-rout-1": TrackOverride(category="route")})
    with pytest.raises(BuildError, match="rby-rout-1"):
        build([occ("Route 1")], overrides)


def test_reports_every_error_at_once() -> None:
    overrides = Overrides(
        tracks={"rby-a": TrackOverride(category="route"), "rby-b": TrackOverride(exclude=True)}
    )
    with pytest.raises(BuildError) as error:
        build([], overrides)
    assert len(error.value.errors) == 2


def test_extra_duration_sets_length_and_can_make_a_jingle() -> None:
    overrides = Overrides(
        extras=[
            Extra(family="rby", title="Beach", duration="2:01"),
            Extra(family="rby", title="Banner Intro", duration="0:03"),
        ]
    )
    result = build([], overrides)
    assert track(result, "rby-beach").durationMs == 121_000
    assert [(e.id, e.duration_ms, e.reason) for e in result.excluded] == [
        ("rby-banner-intro", 3_000, "jingle")
    ]


def test_duration_override_replaces_the_computed_length() -> None:
    overrides = Overrides(
        tracks={
            "rby-intro-part-2": TrackOverride(mergeInto="rby-intro-part-1"),
            "rby-intro-part-1": TrackOverride(title="Intro", duration="0:45"),
        }
    )
    result = build([occ("Intro Part 1", seconds=33), occ("Intro Part 2", seconds=12)], overrides)
    intro = track(result, "rby-intro-part-1")
    assert (intro.title, intro.durationMs, len(intro.origins)) == ("Intro", 45_000, 2)


def test_duration_override_is_what_the_jingle_rule_sees() -> None:
    overrides = Overrides(tracks={"rby-short-theme": TrackOverride(duration="0:30")})
    assert ids(build([occ("Short Theme", seconds=10)], overrides)) == ["rby-short-theme"]


def test_a_local_override_puts_files_in_front_of_a_tracks_sources() -> None:
    local = LocalSource(type="local", path="rby/route-1.m4a")
    overrides = Overrides(tracks={"rby-route-1": TrackOverride(local=[local])})
    assert track(build([occ("Route 1")], overrides), "rby-route-1").sources == [local]
