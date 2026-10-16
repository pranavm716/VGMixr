from pathlib import Path

from vgmixr_pipeline.manifest.parse import Valid, parse

CATALOG_MANIFEST = Path(__file__).resolve().parents[2] / "catalog" / "manifest.json"


def test_catalog_manifest_is_valid() -> None:
    result = parse(CATALOG_MANIFEST.read_text(encoding="utf-8"))
    assert isinstance(result, Valid), result
