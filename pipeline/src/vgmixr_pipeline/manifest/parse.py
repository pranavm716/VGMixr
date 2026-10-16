"""Parses manifest JSON (docs/manifest.md) into a valid Manifest or its validation errors."""

import json
from dataclasses import dataclass

from pydantic import ValidationError

from vgmixr_pipeline.manifest.errors import ErrorCode, ManifestError
from vgmixr_pipeline.manifest.models import Manifest
from vgmixr_pipeline.manifest.validate import validate

SUPPORTED_SCHEMA_VERSION = 1


@dataclass(frozen=True)
class Valid:
    manifest: Manifest


@dataclass(frozen=True)
class Invalid:
    errors: list[ManifestError]


def parse(text: str) -> Valid | Invalid:
    try:
        data = json.loads(text)
    except json.JSONDecodeError as e:
        return _invalid(ErrorCode.MALFORMED, str(e))
    # Checked before the rest: another version may have a different shape, and "unsupported" is
    # more useful than "malformed".
    version = data.get("schemaVersion") if isinstance(data, dict) else None
    # bool is a subclass of int, but `true` is the wrong type, not a version.
    if type(version) is int and version != SUPPORTED_SCHEMA_VERSION:
        return _invalid(ErrorCode.SCHEMA_VERSION_UNSUPPORTED, f"schemaVersion {version}")
    try:
        manifest = Manifest.model_validate(data)
    except ValidationError as e:
        return _invalid(ErrorCode.MALFORMED, str(e))
    errors = validate(manifest)
    return Invalid(errors) if errors else Valid(manifest)


def _invalid(code: ErrorCode, message: str) -> Invalid:
    return Invalid([ManifestError(code, message)])
