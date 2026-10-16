from vgmixr_pipeline.catalog.build import BuildResult, DraftTrack, Excluded
from vgmixr_pipeline.catalog.report import family_report, summary
from vgmixr_pipeline.manifest.models import Game


def game(id_: str, name: str, family: str | None) -> Game:
    return Game(id=id_, name=name, shortName=name[0], color="#000000", generation=3,
                platform="GBA", family=family)  # fmt: skip


GAMES = [
    game("ruby", "Ruby", "rse"),
    game("sapphire", "Sapphire", "rse"),
    game("emerald", "Emerald", "rse"),
    game("solo", "Solo", None),
]


def draft(id_: str, games: list[str] | None, category: str = "route") -> DraftTrack:
    return DraftTrack(id=id_, family="rse", games=games, title=id_.title(), category=category,
                      order=0, sources=[], durationMs=None, origins=[])  # fmt: skip


RESULT = BuildResult(
    tracks=[
        draft("rse-a", None),
        draft("rse-b", None, "other"),
        draft("rse-c", ["emerald"]),
        draft("rse-d", ["emerald"], "battle"),
        draft("rse-e", ["ruby"]),
    ],
    excluded=[
        Excluded("rse", "rse-healed", "Pokémon Healed", 5_000, "jingle"),
        Excluded("rse", "rse-x", "X", None, "override"),
    ],
)


def test_family_report_counts_shared_and_exclusive_tracks() -> None:
    report = family_report(GAMES, RESULT)
    assert "## rse: Ruby / Sapphire / Emerald" in report
    assert "- Tracks: 5 (shared by all games: 2)" in report
    assert "- Exclusive to emerald: 2 (Rse-C, Rse-D)" in report
    assert "- Exclusive to ruby: 1 (Rse-E)" in report
    assert "- Categories: battle 1, route 3, other 1" in report
    assert "- Excluded jingles: 1 (Pokémon Healed 0:05)" in report
    assert "- Excluded by override: 1 (X)" in report


def test_family_report_warns_about_families_without_tracks() -> None:
    report = family_report(GAMES, RESULT)
    assert "## solo: Solo" in report
    assert "- **No tracks.**" in report


def test_summary_has_one_line_per_family() -> None:
    lines = summary(GAMES, RESULT).splitlines()
    assert lines[1:] == [
        "rse      5 tracks   2 shared   3 exclusive   1 other   2 excluded",
        "solo     0 tracks   0 shared   0 exclusive   0 other   0 excluded",
    ]
