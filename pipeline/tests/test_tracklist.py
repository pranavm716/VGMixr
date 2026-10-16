from pathlib import Path

import pytest

from vgmixr_pipeline.catalog.tracklist import Row, parse_disc

PAGES = Path(__file__).parent / "data" / "bulbapedia"


def page(name: str) -> str:
    return (PAGES / f"{name}.wikitext").read_text(encoding="utf-8")


def test_reads_number_cleaned_title_and_duration() -> None:
    rows = parse_disc(page("rs"), "Disc 1")
    assert rows[:3] == [
        Row(1, "Opening Movie: Setting out on a Journey in the Hoenn Region", 35_000),
        Row(2, "Opening Movie: Double Battles", 16_000),
        Row(5, "Littleroot Town", 88_000),
    ]
    assert [r.number for r in rows] == [1, 2, 5, 11, 14, 53, 54]


def test_reads_cells_with_attributes() -> None:
    assert parse_disc(page("rs"), "Disc 1")[-1] == Row(54, "Surf", 133_000)


def test_disc_stops_at_a_subsection() -> None:
    """Disc 2's Bonus Tracks are a ==== subsection, so they're not part of Disc 2."""
    titles = [r.title for r in parse_disc(page("rs"), "Disc 2")]
    assert titles == ["Route 119", "Fortree City"]


def test_reads_a_subsection_by_its_own_heading() -> None:
    rows = parse_disc(page("rs"), "Bonus Tracks")
    assert rows == [Row(53, "Trick Master (feat. Karakuri King & Karakuri Chorus)", 143_000)]


def test_selects_an_inclusive_number_range() -> None:
    rows = parse_disc(page("b2w2"), "Disc 4", first=17, last=18)
    assert rows == [
        Row(17, "Battle! (Mew)", 88_000),
        Row(18, "Title Screen (Pokémon Platinum)", 106_000),
    ]


def test_skips_rowspan_continuation_rows() -> None:
    rows = parse_disc(page("hgss"), "Disc 3")
    assert rows == [
        Row(1, "Opening Movie (Pokémon Gold & Pokémon Silver)", 44_000),
        Row(2, "Title Screen (Pokémon Gold & Pokémon Silver)", 86_000),
        Row(3, "New Bark Town", 44_000),
    ]


def test_drops_small_text() -> None:
    titles = [r.title for r in parse_disc(page("lgpe"), "Disc 3")]
    assert titles == ["Opening Movie", "Title Screen", "Pallet Town Theme", "The Sea"]


def test_blank_duration_and_footnotes() -> None:
    rows = parse_disc(page("za"), "Disc 2")
    assert rows[1] == Row(4, "Battle! (Rogue Mega-Evolved Pokémon)", None)
    assert all(r.duration_ms is None for r in rows)


TINY_TABLE = """===Disc 1===
{|
! No.
! Track Title (English)
! Duration
|-
| 57{{anchor|An Unwavering Heart}}
| An Unwavering Heart
| 1:47
|}
"""


def test_drops_anchors() -> None:
    """From the BW album page."""
    assert parse_disc(TINY_TABLE, "Disc 1") == [Row(57, "An Unwavering Heart", 107_000)]


def test_finds_headings_after_an_unclosed_italic() -> None:
    """The FRLG album page has a stray '' in its lead, which the parser stretches over the
    following headings up to the next ''. Headings are line-based in MediaWiki, so they count."""
    text = "Intro with a stray ''.\n\n==Tracklist==\n" + TINY_TABLE + "\n==Notes==\nLater ''.\n"
    assert [r.number for r in parse_disc(text, "Disc 1")] == [57]


def test_missing_disc_raises() -> None:
    with pytest.raises(ValueError, match="Disc 9"):
        parse_disc(page("rs"), "Disc 9")
