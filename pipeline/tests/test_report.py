from vgmixr_pipeline.catalog.build import BuildResult, DraftTrack, Excluded
from vgmixr_pipeline.catalog.match import Match, Scored
from vgmixr_pipeline.catalog.report import family_report, match_report, match_summary, summary
from vgmixr_pipeline.catalog.score import Candidate, Score
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


def draft(id_: str, games: list[str] | None) -> DraftTrack:
    return DraftTrack(id=id_, family="rse", games=games, title=id_.title(),
                      order=0, sources=[], durationMs=None, origins=[])  # fmt: skip


RESULT = BuildResult(
    tracks=[
        draft("rse-a", None),
        draft("rse-b", None),
        draft("rse-c", ["emerald"]),
        draft("rse-d", ["emerald"]),
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
    assert "- Excluded jingles: 1 (Pokémon Healed 0:05)" in report
    assert "- Excluded by override: 1 (X)" in report


def test_family_report_warns_about_families_without_tracks() -> None:
    report = family_report(GAMES, RESULT)
    assert "## solo: Solo" in report
    assert "- **No tracks.**" in report


def test_summary_has_one_line_per_family() -> None:
    lines = summary(GAMES, RESULT).splitlines()
    assert lines[1:] == [
        "rse      5 tracks   2 shared   3 exclusive   2 excluded",
        "solo     0 tracks   0 shared   0 exclusive   0 excluded",
    ]


def match(id_: str, status: str, best: float | None = None) -> Match:
    candidates = []
    if best is not None:
        candidate = Candidate("abcdefghijk", "Video", None, None, "search")
        candidates = [Scored(candidate, Score(best, []))]
    return Match(id_, "rse", id_.title(), status, [], candidates)  # type: ignore[arg-type]


MATCHES = [
    match("rse-a", "matched"),
    match("rse-b", "matched"),
    match("rse-c", "review", 0.62),
    match("rse-d", "unmatched"),
    match("rse-e", "unmatched"),
]


def test_match_report_counts_and_lists_titles_per_family() -> None:
    report = match_report(GAMES, MATCHES)
    assert "## rse: Ruby / Sapphire / Emerald" in report
    assert "- Matched: 2, needs review: 1, unmatched: 2" in report
    assert "- Needs review: Rse-C (best 0.62)" in report
    assert "- Unmatched: Rse-D, Rse-E" in report
    assert "## solo: Solo\n\n- **No tracks.**" in report


def test_match_summary_has_a_line_per_family() -> None:
    lines = match_summary(GAMES, MATCHES).splitlines()
    assert lines[1].split() == ["rse", "2", "matched", "1", "review", "2", "unmatched"]
    assert lines[2].split() == ["solo", "0", "matched", "0", "review", "0", "unmatched"]
