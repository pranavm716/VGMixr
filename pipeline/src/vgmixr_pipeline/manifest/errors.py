"""Manifest validation errors. The codes are shared with the app (docs/fixtures/README.md)."""

from dataclasses import dataclass
from enum import StrEnum


class ErrorCode(StrEnum):
    MALFORMED = "malformed"
    SCHEMA_VERSION_UNSUPPORTED = "schema-version-unsupported"
    GENERATED_AT_INVALID = "generated-at-invalid"
    GAME_ID_DUPLICATE = "game-id-duplicate"
    GAME_SLUG_INVALID = "game-slug-invalid"
    GAME_FIELD_EMPTY = "game-field-empty"
    GAME_GENERATION_INVALID = "game-generation-invalid"
    GAME_SHORT_NAME_INVALID = "game-short-name-invalid"
    GAME_COLOR_INVALID = "game-color-invalid"
    FAMILY_GENERATION_MISMATCH = "family-generation-mismatch"
    FAMILY_ID_COLLISION = "family-id-collision"
    TRACK_ID_DUPLICATE = "track-id-duplicate"
    TRACK_ID_INVALID = "track-id-invalid"
    TRACK_TITLE_EMPTY = "track-title-empty"
    TRACK_ORDER_INVALID = "track-order-invalid"
    TRACK_FAMILY_UNKNOWN = "track-family-unknown"
    TRACK_GAMES_INVALID = "track-games-invalid"
    TRACK_SOURCES_EMPTY = "track-sources-empty"
    SOURCE_IDENTITY_DUPLICATE = "source-identity-duplicate"
    TRACK_REPLACES_INVALID = "track-replaces-invalid"
    SOURCE_TYPE_UNKNOWN = "source-type-unknown"
    LOCAL_PATH_INVALID = "local-path-invalid"
    STREAM_INVALID = "stream-invalid"
    STREAM_CONFIDENCE_INVALID = "stream-confidence-invalid"
    STREAM_LOOP_PRESENT = "stream-loop-present"
    LOOP_INVALID = "loop-invalid"


@dataclass(frozen=True)
class ManifestError:
    code: ErrorCode
    message: str
