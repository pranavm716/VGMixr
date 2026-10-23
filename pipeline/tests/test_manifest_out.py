"""Writing catalog/manifest.json from the draft and its matches (docs/roadmap.md, step 17)."""

import json
from pathlib import Path
from typing import Any

from vgmixr_pipeline.catalog.build import Draft, DraftTrack
from vgmixr_pipeline.catalog.manifest_out import (
    build_manifest,
    shipped_ids,
    write_catalog,
    write_manifest,
)
from vgmixr_pipeline.catalog.match import Match
from vgmixr_pipeline.manifest.models import Game, LocalSource, StreamSource
from vgmixr_pipeline.manifest.parse import Valid, parse

GAMES = [
    Game(id="ruby", name="Ruby", shortName="R", color="#CD2236", generation=3, platform="GBA",
         family="rse"),
    Game(id="sapphire", name="Sapphire", shortName="S", color="#3D51A7", generation=3,
         platform="GBA", family="rse"),
    Game(id="dev", name="Dev Test", shortName="DEV", color="#808080", platform="Dev"),
]  # fmt: skip
TONE = LocalSource(type="local", path="dev/test-tone.wav")


def track(id_: str, family: str = "rse", sources: list[Any] | None = None, **kw: Any) -> DraftTrack:
    fields: dict[str, Any] = {"games": None, "title": id_.removeprefix(family + "-"), "order": 0,
                              "durationMs": 60_000, "origins": ["page / Disc 1 #1"]}  # fmt: skip
    return DraftTrack(id=id_, family=family, sources=sources or [], **(fields | kw))


def stream(video_id: str, confidence: float = 0.9, **kw: Any) -> StreamSource:
    return StreamSource(type="stream", videoId=video_id, confidence=confidence, **kw)


def match(t: DraftTrack, *streams: StreamSource) -> Match:
    status = "matched" if streams else "unmatched"
    return Match(t.id, t.family, t.title, status, list(streams), [])  # type: ignore[arg-type]


DRAFT = Draft(
    generatedAt="2026-10-16T10:00:00Z",
    games=GAMES,
    tracks=[
        track("rse-route-101", games=["ruby"]),
        track("rse-route-102", order=1),
        track("dev-test-tone", family="dev", sources=[TONE], order=999),
    ],
)
MATCHES = [
    match(DRAFT.tracks[0], stream("aaaaaaaaaaa", 0.95), stream("bbbbbbbbbbb", 0.9, startMs=5)),
    match(DRAFT.tracks[1]),
    match(DRAFT.tracks[2]),
]


def test_tracks_get_their_streams_and_lose_draft_only_fields() -> None:
    manifest = build_manifest(DRAFT, MATCHES, "2026-10-16T10:00:00Z")
    route = manifest.tracks[0]
    assert (route.id, route.games, route.order) == ("rse-route-101", ["ruby"], 0)
    assert [s.videoId for s in route.sources if isinstance(s, StreamSource)] == [
        "aaaaaaaaaaa",
        "bbbbbbbbbbb",
    ]
    assert manifest.schemaVersion == 2


def test_unmatched_tracks_are_left_out_and_hand_sources_kept() -> None:
    manifest = build_manifest(DRAFT, MATCHES, "2026-10-16T10:00:00Z")
    assert [t.id for t in manifest.tracks] == ["rse-route-101", "dev-test-tone"]
    assert manifest.tracks[1].sources == [TONE]


def test_a_manifest_id_comes_from_the_final_title() -> None:
    t = track("dppt-canalave-city", family="dppt", title="Canalave City (Day)")
    draft = DRAFT.model_copy(update={"tracks": [t]})
    manifest = build_manifest(draft, [match(t, stream("aaaaaaaaaaa"))], "2026-10-16T10:00:00Z")
    assert [x.id for x in manifest.tracks] == ["dppt-canalave-city-day"]


def test_local_sources_stay_in_front_of_streams() -> None:
    local = LocalSource(type="local", path="rse/001.flac")
    t = track("rse-route-101", sources=[local])
    m = Match(t.id, t.family, t.title, "matched", [stream("aaaaaaaaaaa")], [])
    assert build_manifest(DRAFT.model_copy(update={"tracks": [t]}), [m], "2026-10-16T10:00:00Z")\
        .tracks[0].sources == [local, stream("aaaaaaaaaaa")]  # fmt: skip


def test_writes_a_valid_manifest_without_nulls(tmp_path: Path) -> None:
    path = tmp_path / "manifest.json"
    assert write_manifest(path, DRAFT, MATCHES, "2026-10-16T10:00:00Z") == []
    text = path.read_text(encoding="utf-8")
    assert isinstance(parse(text), Valid)
    data = json.loads(text)
    assert "null" not in text
    assert "games" not in data["tracks"][1] or data["tracks"][1]["games"]
    assert data["games"][2] == {"id": "dev", "name": "Dev Test", "shortName": "DEV",
                                "color": "#808080", "platform": "Dev"}  # fmt: skip


def test_an_invalid_manifest_is_never_written(tmp_path: Path) -> None:
    path = tmp_path / "manifest.json"
    path.write_bytes(b"old contents")
    bad = [match(DRAFT.tracks[0], stream("not-a-video-id")), *MATCHES[1:]]
    errors = write_manifest(path, DRAFT, bad, "2026-10-16T10:00:00Z")
    assert errors and "STREAM_INVALID" in errors[0].upper().replace("-", "_")
    assert path.read_bytes() == b"old contents"
    assert list(tmp_path.iterdir()) == [path]


def test_generated_at_is_kept_when_nothing_else_changed(tmp_path: Path) -> None:
    path = tmp_path / "manifest.json"
    write_manifest(path, DRAFT, MATCHES, "2026-10-16T10:00:00Z")
    first = path.read_bytes()
    write_manifest(path, DRAFT, MATCHES, "2026-10-17T09:00:00Z")
    assert path.read_bytes() == first

    changed = [match(DRAFT.tracks[0], stream("ccccccccccc")), *MATCHES[1:]]
    write_manifest(path, DRAFT, changed, "2026-10-17T09:00:00Z")
    assert json.loads(path.read_text(encoding="utf-8"))["generatedAt"] == "2026-10-17T09:00:00Z"


def test_a_manifest_id_is_frozen_in_ids_json() -> None:
    t = track("rse-route-101", title="Route 101 (Renamed)")
    draft = DRAFT.model_copy(update={"tracks": [t]})
    manifest = build_manifest(draft, [match(t, stream("aaaaaaaaaaa"))], "2026-10-16T10:00:00Z",
                              {"rse-route-101": "rse-route-101"})  # fmt: skip
    assert [x.id for x in manifest.tracks] == ["rse-route-101"]


def test_shipped_ids_are_the_tracks_with_sources() -> None:
    assert shipped_ids(DRAFT, MATCHES, {"dev-test-tone": "dev-tone"}) == {
        "rse-route-101": "rse-route-101",
        "dev-test-tone": "dev-tone",
    }


def test_write_catalog_adds_the_shipped_ids_to_ids_json(tmp_path: Path) -> None:
    manifest, ids = tmp_path / "manifest.json", tmp_path / "ids.json"
    ids.write_text('{\n  "rse-gone": "rse-gone"\n}\n', encoding="utf-8")
    frozen = {"rse-gone": "rse-gone"}
    result = write_catalog(manifest, ids, DRAFT, MATCHES, "2026-10-16T10:00:00Z", frozen,
                           ["rse", "dev"])  # fmt: skip
    assert result == ([], 2)
    assert ids.read_text(encoding="utf-8") == (
        '{\n  "rse-gone": "rse-gone",\n  "rse-route-101": "rse-route-101",\n\n'
        '  "dev-test-tone": "dev-test-tone"\n}\n'
    )


def test_an_invalid_manifest_adds_no_ids(tmp_path: Path) -> None:
    manifest, ids = tmp_path / "manifest.json", tmp_path / "ids.json"
    bad = [match(DRAFT.tracks[0], stream("not-a-video-id")), *MATCHES[1:]]
    errors, added = write_catalog(manifest, ids, DRAFT, bad, "2026-10-16T10:00:00Z", {},
                                  ["rse", "dev"])  # fmt: skip
    assert errors and added == 0
    assert not ids.exists() and not manifest.exists()
