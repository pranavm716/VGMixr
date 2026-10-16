"""Writing catalog/manifest.json from the draft and its matches (docs/roadmap.md, step 17)."""

import json
from pathlib import Path
from typing import Any

from vgmixr_pipeline.catalog.build import Draft, DraftTrack
from vgmixr_pipeline.catalog.manifest_out import build_manifest, write_manifest
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
    fields: dict[str, Any] = {"games": None, "title": id_, "category": "route", "order": 0,
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
        track("dev-test-tone", family="dev", sources=[TONE], category="other", order=999),
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
    assert (route.id, route.games, route.category, route.order) == (
        "rse-route-101", ["ruby"], "route", 0
    )  # fmt: skip
    assert [s.videoId for s in route.sources if isinstance(s, StreamSource)] == [
        "aaaaaaaaaaa",
        "bbbbbbbbbbb",
    ]
    assert manifest.schemaVersion == 1


def test_unmatched_tracks_are_left_out_and_hand_sources_kept() -> None:
    manifest = build_manifest(DRAFT, MATCHES, "2026-10-16T10:00:00Z")
    assert [t.id for t in manifest.tracks] == ["rse-route-101", "dev-test-tone"]
    assert manifest.tracks[1].sources == [TONE]


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
