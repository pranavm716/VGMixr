"""Reads one disc's track table from a Bulbapedia soundtrack album page.

Album pages hold one wikitable per `===Disc N===` heading. Columns are found by header text: the
first column is the CD number, the English title is the header containing "English", the duration
is the header containing "Duration".
"""

import re
from dataclasses import dataclass

import mwparserfromhell
from mwparserfromhell.nodes import Tag
from mwparserfromhell.wikicode import Wikicode

# A heading is a whole line, `==Title==` with any level.
_HEADING = re.compile(r"^(=+)\s*(.+?)\s*\1\s*$", re.MULTILINE)
_DURATION = re.compile(r"(?:(\d+):)?(\d{1,2}):(\d{2})")
# Dropped with their contents: feat. credits, "(Pokémon Yellow)" notes and footnotes.
_DROPPED_TAGS = {"small", "ref", "sup"}
# Templates that render nothing visible; every other template is replaced by its first argument.
_DROPPED_TEMPLATES = {"anchor"}


@dataclass(frozen=True)
class Row:
    number: int
    title: str
    duration_ms: int | None


def parse_disc(
    wikitext: str, disc: str, first: int | None = None, last: int | None = None
) -> list[Row]:
    """Rows of the table under the heading `disc`, limited to CD numbers first..last inclusive.

    The section ends at the next heading of any level, so a disc's subsections (e.g. Bonus Tracks)
    are not part of it.
    """
    table = _table(mwparserfromhell.parse(_section(wikitext, disc)), disc)
    headers = [_text(c.contents) for c in _children(table.contents, "th")]
    title_col = _column(headers, "English", disc)
    duration_col = next((i for i, h in enumerate(headers) if "Duration" in h), None)
    rows = []
    for cells in _grid(table):
        number = int(_text(cells[0]))
        if (first is not None and number < first) or (last is not None and number > last):
            continue
        duration = parse_duration(_text(cells[duration_col])) if duration_col is not None else None
        rows.append(Row(number, _text(cells[title_col]), duration))
    return rows


def _section(wikitext: str, disc: str) -> str:
    """The text between the heading `disc` and the next heading.

    Split on heading lines before parsing: on some pages a stray '' makes the parser stretch an
    italic over the headings that follow, hiding them from mwparserfromhell's own sections.
    """
    headings = list(_HEADING.finditer(wikitext))
    for heading, following in zip(headings, [*headings[1:], None], strict=True):
        if heading.group(2) == disc:
            return wikitext[heading.end() : following.start() if following else None]
    raise ValueError(f"no section {disc!r}")


def _table(section: Wikicode, disc: str) -> Tag:
    tables = [t for t in section.filter_tags(recursive=False) if str(t.tag) == "table"]
    if len(tables) != 1:
        raise ValueError(f"section {disc!r} has {len(tables)} tables, expected 1")
    return tables[0]


def _column(headers: list[str], needle: str, disc: str) -> int:
    for i, header in enumerate(headers):
        if needle in header:
            return i
    raise ValueError(f"section {disc!r} has no {needle!r} column in {headers}")


def _children(code: Wikicode, *tags: str) -> list[Tag]:
    return [n for n in code.nodes if isinstance(n, Tag) and str(n.tag) in tags]


def _grid(table: Tag) -> list[list[Wikicode]]:
    """Body rows as cell contents by column, with rowspan cells repeated into the rows below.

    A row whose first (number) cell comes from a rowspan above is a continuation of that track
    (e.g. one composer credit of a medley) and is left out.
    """
    spans: dict[int, tuple[int, Wikicode]] = {}  # column -> (rows still covered, contents)
    rows = []
    for tr in _children(table.contents, "tr"):
        cells = _children(tr.contents, "td")
        if not cells:
            continue  # a header row
        row: dict[int, Wikicode] = {}
        spanned: set[int] = set()
        col = 0
        queue = list(cells)
        while queue or col in spans:
            if col in spans:
                remaining, contents = spans[col]
                row[col] = contents
                spanned.add(col)
                if remaining == 1:
                    del spans[col]
                else:
                    spans[col] = (remaining - 1, contents)
            else:
                cell = queue.pop(0)
                row[col] = cell.contents
                if cell.has("rowspan"):
                    span = int(str(cell.get("rowspan").value))
                    if span > 1:
                        spans[col] = (span - 1, cell.contents)
            col += 1
        if 0 not in spanned:
            rows.append([row[i] for i in range(len(row))])
    return rows


def _text(code: Wikicode) -> str:
    """Plain text: links unwrapped, templates replaced by their first argument (e.g. {{so|x}}),
    <br> as a space, small text and footnotes dropped, whitespace collapsed."""
    code = mwparserfromhell.parse(str(code))  # a copy, since nodes are removed below
    for tag in code.filter_tags(recursive=False):
        name = str(tag.tag).lower()
        if name in _DROPPED_TAGS:
            code.replace(tag, "")
        elif name == "br":
            code.replace(tag, " ")
    for template in code.filter_templates(recursive=False):
        name = str(template.name).strip()
        dropped = name.startswith("#") or name.lower() in _DROPPED_TEMPLATES
        keep = "" if dropped or not template.params else str(template.params[0])
        code.replace(template, _text(mwparserfromhell.parse(keep)))
    text = code.strip_code(normalize=True, collapse=True)
    return " ".join(text.split())


def parse_duration(text: str) -> int | None:
    """Milliseconds from "m:ss" or "h:mm:ss"; None for anything else (e.g. a blank cell)."""
    match = _DURATION.fullmatch(text)
    if match is None:
        return None
    hours, minutes, seconds = (int(g) if g else 0 for g in match.groups())
    return ((hours * 60 + minutes) * 60 + seconds) * 1000
