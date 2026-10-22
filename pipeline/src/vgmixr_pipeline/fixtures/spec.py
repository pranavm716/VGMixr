"""The invalid manifest fixtures in docs/fixtures/invalid/, as one base manifest plus a patch each.

Edit this file, not the generated fixtures, then run
`uv run python -m vgmixr_pipeline.fixtures.generate`. List order is the README table's order:
by field, as the fields appear in a manifest.
"""

from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

from vgmixr_pipeline.manifest.errors import ErrorCode

type JsonObject = dict[str, Any]

# A two-game family (ruby, sapphire in rse), a standalone game (pla) and one track with one
# stream source. A fixture adds a second track or source only when its rule needs one.
BASE: JsonObject = {
    "schemaVersion": 2,
    "generatedAt": "2026-10-14T08:00:00Z",
    "games": [
        {
            "id": "ruby",
            "name": "Ruby",
            "shortName": "R",
            "color": "#C8372D",
            "generation": 3,
            "platform": "GBA",
            "family": "rse",
        },
        {
            "id": "sapphire",
            "name": "Sapphire",
            "shortName": "S",
            "color": "#2D5BC8",
            "generation": 3,
            "platform": "GBA",
            "family": "rse",
        },
        {
            "id": "pla",
            "name": "Legends: Arceus",
            "shortName": "PLA",
            "color": "#7A8FB5",
            "generation": 8,
            "platform": "Switch",
        },
    ],
    "tracks": [
        {
            "id": "rse-route-101",
            "family": "rse",
            "title": "Route 101",
            "order": 12,
            "sources": [{"type": "stream", "videoId": "XXXXXXXXXXX", "startMs": 0, "endMs": None}],
        }
    ],
}

LOOP: JsonObject = {"sampleRate": 32000, "introEnd": 96000, "loopEnd": 1824000}


@dataclass(frozen=True)
class Fixture:
    file: str
    code: ErrorCode
    defect: str
    # Mutates a deep copy of BASE. Any return value is ignored, so `dict.update` and `dict.pop`
    # work in a lambda.
    patch: Callable[[JsonObject], object]


@dataclass(frozen=True)
class RawFixture:
    """A fixture written verbatim, for text that isn't valid JSON."""

    file: str
    code: ErrorCode
    defect: str
    text: str


def game(manifest: JsonObject, game_id: str) -> JsonObject:
    found: JsonObject = next(g for g in manifest["games"] if g["id"] == game_id)
    return found


def track(manifest: JsonObject) -> JsonObject:
    first: JsonObject = manifest["tracks"][0]
    return first


def source(manifest: JsonObject) -> JsonObject:
    first: JsonObject = track(manifest)["sources"][0]
    return first


def stream(video_id: str, **fields: Any) -> JsonObject:
    return {"type": "stream", "videoId": video_id, "startMs": 0, "endMs": None, **fields}


def local(path: str, **fields: Any) -> JsonObject:
    return {"type": "local", "path": path, **fields}


def route_102(**fields: Any) -> JsonObject:
    return {
        "id": "rse-route-102",
        "family": "rse",
        "title": "Route 102",
        "order": 13,
        "sources": [stream("YYYYYYYYYYY")],
        **fields,
    }


def set_source(new: JsonObject) -> Callable[[JsonObject], object]:
    return lambda m: track(m).update(sources=[new])


SYNTAX_ERROR = """\
{
  "schemaVersion": 2,
  "generatedAt": "2026-10-14T08:00:00Z",
  "games": [
    {
      "id": "ruby",
      "name": "Ruby",
      "shortName": "R",
      "color": "#C8372D",
      "generation": 3,
      "platform": "GBA",
      "family": "rse"
    },
    {
      "id": "sapphire",
      "name": "Sapphire",
      "shortName": "S",
      "color": "#2D5BC8",
      "generation": 3,
      "platform": "GBA",
      "family": "rse"
    },
    {
      "id": "pla",
      "name": "Legends: Arceus",
      "shortName": "PLA",
      "color": "#7A8FB5",
      "generation": 8,
      "platform": "Switch"
    }
  ],
  "tracks": [
    {
      "id": "rse-route-101",
      "family": "rse",
      "title": "Route 101",
      "order": 12,
      "sources": [
        { "type": "stream", "videoId": "XXXXXXXXXXX", "startMs": 0, "endMs": null }
      ]
    }
  ]
"""

E = ErrorCode

FIXTURES: list[Fixture | RawFixture] = [
    RawFixture("syntax-error.json", E.MALFORMED, "Not valid JSON (unclosed brace)", SYNTAX_ERROR),
    Fixture(
        "schemaVersion-string.json",
        E.MALFORMED,
        '`schemaVersion` is `"2"`, a string',
        lambda m: m.update(schemaVersion="2"),
    ),
    Fixture(
        "schemaVersion-unsupported.json",
        E.SCHEMA_VERSION_UNSUPPORTED,
        "`schemaVersion` is `1`, an older version",
        lambda m: m.update(schemaVersion=1),
    ),
    Fixture(
        "generatedAt-offset.json",
        E.GENERATED_AT_INVALID,
        "`generatedAt` is `2026-10-14T10:00:00+02:00`: ISO-8601, but not UTC",
        lambda m: m.update(generatedAt="2026-10-14T10:00:00+02:00"),
    ),
    Fixture(
        "generatedAt-not-iso.json",
        E.GENERATED_AT_INVALID,
        "`generatedAt` is `14/10/2026`",
        lambda m: m.update(generatedAt="14/10/2026"),
    ),
    Fixture(
        "games.id-duplicate.json",
        E.GAME_ID_DUPLICATE,
        "Two games with id `ruby`",
        lambda m: game(m, "sapphire").update(id="ruby"),
    ),
    Fixture(
        "games.id-slug.json",
        E.GAME_SLUG_INVALID,
        "Game id is `Ruby`",
        lambda m: game(m, "ruby").update(id="Ruby"),
    ),
    Fixture(
        "games.name-empty.json",
        E.GAME_FIELD_EMPTY,
        '`ruby` name is `""`',
        lambda m: game(m, "ruby").update(name=""),
    ),
    Fixture(
        "games.shortName-empty.json",
        E.GAME_SHORT_NAME_INVALID,
        '`ruby` shortName is `""`',
        lambda m: game(m, "ruby").update(shortName=""),
    ),
    Fixture(
        "games.shortName-too-long.json",
        E.GAME_SHORT_NAME_INVALID,
        "`ruby` shortName is 13 chars",
        lambda m: game(m, "ruby").update(shortName="RubyVersion13"),
    ),
    Fixture(
        "games.color-missing.json",
        E.MALFORMED,
        "`ruby` has no `color`",
        lambda m: game(m, "ruby").pop("color"),
    ),
    Fixture(
        "games.color-alpha.json",
        E.GAME_COLOR_INVALID,
        "`ruby` color is `#C8372DFF`; catches a pattern not anchored at the end",
        lambda m: game(m, "ruby").update(color="#C8372DFF"),
    ),
    Fixture(
        "games.generation-zero.json",
        E.GAME_GENERATION_INVALID,
        "`pla` generation is `0`",
        lambda m: game(m, "pla").update(generation=0),
    ),
    Fixture(
        "games.generation-mismatch.json",
        E.FAMILY_GENERATION_MISMATCH,
        "`ruby` is generation 3, `sapphire` is 4",
        lambda m: game(m, "sapphire").update(generation=4),
    ),
    Fixture(
        "games.generation-partial.json",
        E.FAMILY_GENERATION_MISMATCH,
        "`ruby` is generation 3, `sapphire` omits it",
        lambda m: game(m, "sapphire").pop("generation"),
    ),
    Fixture(
        "games.platform-empty.json",
        E.GAME_FIELD_EMPTY,
        '`ruby` platform is `""`',
        lambda m: game(m, "ruby").update(platform=""),
    ),
    Fixture(
        "games.family-slug.json",
        E.GAME_SLUG_INVALID,
        "`pla` family is `Legends` (no track references it)",
        lambda m: game(m, "pla").update(family="Legends"),
    ),
    Fixture(
        "games.family-collision.json",
        E.FAMILY_ID_COLLISION,
        "`pla` family is `ruby`, the id of a game in `rse`",
        lambda m: game(m, "pla").update(family="ruby"),
    ),
    Fixture(
        "tracks-missing.json",
        E.MALFORMED,
        "Top-level `tracks` is missing",
        lambda m: m.pop("tracks"),
    ),
    Fixture(
        "tracks.id-duplicate.json",
        E.TRACK_ID_DUPLICATE,
        "Two tracks with id `rse-route-101`",
        lambda m: m["tracks"].append(route_102(id="rse-route-101", title="Route 101", order=12)),
    ),
    Fixture(
        "tracks.id-slug.json",
        E.TRACK_ID_INVALID,
        "Track id is `RSE-Route-101`",
        lambda m: track(m).update(id="RSE-Route-101"),
    ),
    Fixture(
        "tracks.family-unknown.json",
        E.TRACK_FAMILY_UNKNOWN,
        "Track family is `dppt`",
        lambda m: track(m).update(family="dppt"),
    ),
    Fixture(
        "tracks.games-empty.json",
        E.TRACK_GAMES_INVALID,
        "`games` is `[]`",
        lambda m: track(m).update(games=[]),
    ),
    Fixture(
        "tracks.games-duplicate.json",
        E.TRACK_GAMES_INVALID,
        '`games` is `["ruby", "ruby"]`',
        lambda m: track(m).update(games=["ruby", "ruby"]),
    ),
    Fixture(
        "tracks.games-outside-family.json",
        E.TRACK_GAMES_INVALID,
        '`games` is `["pla"]`, a game outside `rse`',
        lambda m: track(m).update(games=["pla"]),
    ),
    Fixture(
        "tracks.title-empty.json",
        E.TRACK_TITLE_EMPTY,
        '`title` is `""`',
        lambda m: track(m).update(title=""),
    ),
    Fixture(
        "tracks.order-negative.json",
        E.TRACK_ORDER_INVALID,
        "`order` is `-1`",
        lambda m: track(m).update(order=-1),
    ),
    Fixture(
        "tracks.sources-empty.json",
        E.TRACK_SOURCES_EMPTY,
        "`sources` is `[]`",
        lambda m: track(m).update(sources=[]),
    ),
    Fixture(
        "tracks.sources-duplicate-local.json",
        E.SOURCE_IDENTITY_DUPLICATE,
        "Two `local` sources with path `rse/012.flac`",
        lambda m: track(m).update(sources=[local("rse/012.flac"), local("rse/012.flac")]),
    ),
    Fixture(
        "tracks.sources-duplicate-stream.json",
        E.SOURCE_IDENTITY_DUPLICATE,
        "Two streams of one video, one with `startMs: 0` and one omitting it (defaults to 0)",
        lambda m: track(m)["sources"].append(
            {"type": "stream", "videoId": "XXXXXXXXXXX", "endMs": None}
        ),
    ),
    Fixture(
        "tracks.sources.type-unknown.json",
        E.SOURCE_TYPE_UNKNOWN,
        "Source type is `midi`",
        set_source({"type": "midi", "path": "rse/012.mid"}),
    ),
    Fixture(
        "tracks.sources.path-empty.json",
        E.LOCAL_PATH_INVALID,
        '`path` is `""`',
        set_source(local("")),
    ),
    Fixture(
        "tracks.sources.path-absolute.json",
        E.LOCAL_PATH_INVALID,
        "`path` is `/rse/012.flac`",
        set_source(local("/rse/012.flac")),
    ),
    Fixture(
        "tracks.sources.path-dotdot.json",
        E.LOCAL_PATH_INVALID,
        "`path` is `rse/../012.flac`",
        set_source(local("rse/../012.flac")),
    ),
    Fixture(
        "tracks.sources.path-backslash.json",
        E.LOCAL_PATH_INVALID,
        "`path` is `rse\\012.flac`",
        set_source(local("rse\\012.flac")),
    ),
    Fixture(
        "tracks.sources.loop-on-stream.json",
        E.STREAM_LOOP_PRESENT,
        "A `stream` source has a valid `loop`",
        lambda m: source(m).update(loop=LOOP),
    ),
    Fixture(
        "tracks.sources.loop.sampleRate-zero.json",
        E.LOOP_INVALID,
        "`sampleRate` is `0`",
        set_source(local("rse/012.flac", loop=LOOP | {"sampleRate": 0})),
    ),
    Fixture(
        "tracks.sources.loop.introEnd-negative.json",
        E.LOOP_INVALID,
        "`introEnd` is `-1`",
        set_source(local("rse/012.flac", loop=LOOP | {"introEnd": -1})),
    ),
    Fixture(
        "tracks.sources.loop.loopEnd-equals-introEnd.json",
        E.LOOP_INVALID,
        "`loopEnd` equals `introEnd`",
        set_source(local("rse/012.flac", loop=LOOP | {"loopEnd": LOOP["introEnd"]})),
    ),
    Fixture(
        "tracks.sources.loop.loopEnd-missing.json",
        E.MALFORMED,
        "`loop` has no `loopEnd`",
        set_source(local("rse/012.flac", loop={"sampleRate": 32000, "introEnd": 96000})),
    ),
    Fixture(
        "tracks.sources.videoId-length.json",
        E.STREAM_INVALID,
        "`videoId` is 10 chars",
        lambda m: source(m).update(videoId="XXXXXXXXXX"),
    ),
    Fixture(
        "tracks.sources.videoId-charset.json",
        E.STREAM_INVALID,
        "`videoId` is 11 chars including `!`",
        lambda m: source(m).update(videoId="XXXXXXXXXX!"),
    ),
    Fixture(
        "tracks.sources.startMs-negative.json",
        E.STREAM_INVALID,
        "`startMs` is `-1`",
        lambda m: source(m).update(startMs=-1),
    ),
    Fixture(
        "tracks.sources.endMs-equals-startMs.json",
        E.STREAM_INVALID,
        "`endMs` equals `startMs`",
        lambda m: source(m).update(startMs=1000, endMs=1000),
    ),
    Fixture(
        "tracks.sources.confidence-above-one.json",
        E.STREAM_CONFIDENCE_INVALID,
        "`confidence` is `1.5`",
        lambda m: source(m).update(confidence=1.5),
    ),
    Fixture(
        "tracks.sources.confidence-negative.json",
        E.STREAM_CONFIDENCE_INVALID,
        "`confidence` is `-0.1`",
        lambda m: source(m).update(confidence=-0.1),
    ),
    Fixture(
        "tracks.replaces-slug.json",
        E.TRACK_REPLACES_INVALID,
        "Entry is `Old Route`",
        lambda m: track(m).update(replaces=["Old Route"]),
    ),
    Fixture(
        "tracks.replaces-self.json",
        E.TRACK_REPLACES_INVALID,
        "Track replaces its own id",
        lambda m: track(m).update(replaces=["rse-route-101"]),
    ),
    Fixture(
        "tracks.replaces-live.json",
        E.TRACK_REPLACES_INVALID,
        "Track replaces another track in the manifest",
        lambda m: (
            track(m).update(replaces=["rse-route-102"]),
            m["tracks"].append(route_102()),
        ),
    ),
    Fixture(
        "tracks.replaces-twice.json",
        E.TRACK_REPLACES_INVALID,
        "Two tracks replace `rse-old-route`",
        lambda m: (
            track(m).update(replaces=["rse-old-route"]),
            m["tracks"].append(route_102(replaces=["rse-old-route"])),
        ),
    ),
]
