"""Parsing rules not covered by the shared fixtures: strict types, edge cases and unknown keys."""

import copy
import json
from collections.abc import Callable

import pytest

from vgmixr_pipeline.fixtures.spec import BASE, JsonObject, game, source, track
from vgmixr_pipeline.manifest.errors import ErrorCode
from vgmixr_pipeline.manifest.models import LocalSource, StreamSource, Track
from vgmixr_pipeline.manifest.parse import Invalid, Valid, parse

type Patch = Callable[[JsonObject], object]


def parse_patched(patch: Patch) -> Valid | Invalid:
    manifest = copy.deepcopy(BASE)
    patch(manifest)
    return parse(json.dumps(manifest))


def codes(result: Valid | Invalid) -> list[ErrorCode]:
    return [error.code for error in result.errors] if isinstance(result, Invalid) else []


def test_base_manifest_is_valid() -> None:
    assert isinstance(parse_patched(lambda m: None), Valid)


# The app ignores unknown keys so that new optional fields don't need a schemaVersion bump.
# The pipeline writes the manifest, so it rejects them to catch typos in its own output.
@pytest.mark.parametrize(
    "patch",
    [
        lambda m: m.update(generatedat="2026-10-14T08:00:00Z"),
        lambda m: game(m, "ruby").update(colour="#C8372D"),
        lambda m: track(m).update(categroy="route"),
        lambda m: source(m).update(endMS=1000),
        lambda m: track(m).update(sources=[{"type": "local", "path": "a.flac", "loops": {}}]),
        lambda m: track(m).update(
            sources=[
                {
                    "type": "local",
                    "path": "a.flac",
                    "loop": {"sampleRate": 1, "introEnd": 0, "loopEnd": 1, "x": 0},
                }
            ]
        ),
    ],
    ids=["top-level", "game", "track", "stream", "local", "loop"],
)
def test_unknown_keys_are_malformed(patch: Patch) -> None:
    assert codes(parse_patched(patch)) == [ErrorCode.MALFORMED]


def test_unknown_source_type_ignores_its_other_keys() -> None:
    result = parse_patched(lambda m: track(m).update(sources=[{"type": "midi", "anything": 1}]))
    assert codes(result) == [ErrorCode.SOURCE_TYPE_UNKNOWN]


# As in the app's StrictNumberSerializers, nothing is coerced into a field of another type.
@pytest.mark.parametrize(
    "patch",
    [
        lambda m: track(m).update(order="12"),
        lambda m: track(m).update(order=12.0),
        lambda m: track(m).update(order=True),
        lambda m: game(m, "ruby").update(generation=3.0),
        lambda m: source(m).update(startMs="0"),
        lambda m: source(m).update(confidence="0.5"),
        lambda m: source(m).update(confidence=True),
        lambda m: game(m, "ruby").update(name=1),
        lambda m: game(m, "ruby").update(name=None),
        lambda m: track(m).update(games="ruby"),
        lambda m: track(m).update(sources={"type": "stream"}),
        lambda m: track(m).update(sources=["stream"]),
        lambda m: track(m).update(sources=[{"videoId": "XXXXXXXXXXX"}]),
        lambda m: track(m).update(sources=[{"type": 1}]),
    ],
)
def test_wrong_types_are_malformed(patch: Patch) -> None:
    assert codes(parse_patched(patch)) == [ErrorCode.MALFORMED]


def test_confidence_accepts_a_json_integer() -> None:
    assert isinstance(parse_patched(lambda m: source(m).update(confidence=1)), Valid)


@pytest.mark.parametrize("text", ["", "[]", "null", '"manifest"', "1"])
def test_non_object_json_is_malformed(text: str) -> None:
    assert codes(parse(text)) == [ErrorCode.MALFORMED]


@pytest.mark.parametrize("version", [0, 1, 100])
def test_unsupported_schema_version_wins_over_a_malformed_body(version: int) -> None:
    result = parse(json.dumps({"schemaVersion": version}))
    assert codes(result) == [ErrorCode.SCHEMA_VERSION_UNSUPPORTED]


@pytest.mark.parametrize("version", ["2", 2.0, True, None])
def test_non_integer_schema_version_is_malformed(version: object) -> None:
    result = parse_patched(lambda m: m.update(schemaVersion=version))
    assert codes(result) == [ErrorCode.MALFORMED]


@pytest.mark.parametrize("value", ["2026-10-14T08:00:00.123Z", "2026-10-14T08:00:00.123456789Z"])
def test_generated_at_accepts_utc_instants(value: str) -> None:
    assert isinstance(parse_patched(lambda m: m.update(generatedAt=value)), Valid)


@pytest.mark.parametrize(
    "value",
    [
        "2026-10-14Z",
        "2026-10-14T08:00Z",
        "2026-10-14T08:00:00.1234567891Z",
        "2026-10-14 08:00:00Z",
        "2026-10-14T08:00:00",
        "2026-02-30T08:00:00Z",
        "2026-10-14T08:00:00z",
        "2026-10-14T08:00:00Z\n",
    ],
)
def test_generated_at_rejects_non_instants(value: str) -> None:
    result = parse_patched(lambda m: m.update(generatedAt=value))
    assert codes(result) == [ErrorCode.GENERATED_AT_INVALID]


def test_short_name_length_counts_code_points() -> None:
    twelve_emoji = "\U0001f525" * 12
    result = parse_patched(lambda m: game(m, "ruby").update(shortName=twelve_emoji))
    assert isinstance(result, Valid)


@pytest.mark.parametrize(
    ("patch", "code"),
    [
        (lambda m: game(m, "ruby").update(id="ruby\n"), ErrorCode.GAME_SLUG_INVALID),
        (lambda m: game(m, "ruby").update(color="#C8372D\n"), ErrorCode.GAME_COLOR_INVALID),
        (lambda m: source(m).update(videoId="XXXXXXXXXXX\n"), ErrorCode.STREAM_INVALID),
        (lambda m: track(m).update(id="rse-route-101\n"), ErrorCode.TRACK_ID_INVALID),
    ],
    ids=["game-id", "color", "videoId", "track-id"],
)
def test_patterns_match_the_whole_string(patch: Patch, code: ErrorCode) -> None:
    assert codes(parse_patched(patch)) == [code]


def test_tracks_accept_already_parsed_sources() -> None:
    """The catalog builder copies parsed sources into new models."""
    local = LocalSource(type="local", path="pla/test-tone.wav")
    stream = StreamSource(type="stream", videoId="XXXXXXXXXXX")
    built = Track(id="t", family="f", title="T", order=0, sources=[local, stream])
    assert built.sources == [local, stream]
