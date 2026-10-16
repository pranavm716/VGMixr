"""Finds stream sources for draft tracks (docs/roadmap.md, step 17).

Per track: entries of the playlists tagged with the track's games first, then a search when
nothing was accepted, then chapters of album videos. Every candidate is scored; the best accepted
ones (at most three) become sources. Review decisions in the overrides come first: accepted slices
lead, rejected videos are dropped. Every track is matched, also one with local files, which stay in
front of its streams.
"""

from collections import Counter
from collections.abc import Iterable
from dataclasses import dataclass
from typing import Any, Literal, Protocol

from vgmixr_pipeline.catalog.build import Draft, DraftTrack
from vgmixr_pipeline.catalog.config import Overrides, Streams, TrackOverride, YouTube
from vgmixr_pipeline.catalog.score import (
    Candidate,
    Names,
    Score,
    TrackFacts,
    Verdict,
    classify,
    score,
    video_words,
)
from vgmixr_pipeline.catalog.youtube import Entry, Video
from vgmixr_pipeline.manifest.models import StreamSource

MAX_SOURCES = 3
# Candidates kept per track (whatever their verdict) for the report, the review tool and labeling.
KEPT_CANDIDATES = 5
SEARCH_RESULTS = 10
_GAME_BOY = {"GB", "GBC"}
# A word in at least this share of a playlist's titles is noise (needs a few entries to tell).
NOISE_SHARE = 0.6
NOISE_MIN_ENTRIES = 4

type Status = Literal["matched", "review", "unmatched"]


class Videos(Protocol):
    def playlist(self, playlist_id: str) -> list[Entry]: ...
    def search(self, query: str, n: int = ...) -> list[Entry]: ...
    def video(self, video_id: str) -> Video: ...


@dataclass(frozen=True)
class Scored:
    candidate: Candidate
    score: Score


@dataclass(frozen=True)
class Match:
    track_id: str
    family: str
    title: str
    status: Status
    streams: list[StreamSource]
    # The best-scoring candidates not rejected by override, best first.
    candidates: list[Scored]


def match_tracks(
    draft: Draft,
    youtube: YouTube,
    overrides: Overrides,
    videos: Videos,
    names: Names,
    search: bool = True,
) -> list[Match]:
    matcher = _Matcher(draft, youtube, videos, names, search)
    return [
        matcher.match(track, overrides.tracks.get(track.id, TrackOverride()).streams or Streams())
        for track in draft.tracks
    ]


class _Matcher:
    def __init__(
        self, draft: Draft, youtube: YouTube, videos: Videos, names: Names, search: bool
    ) -> None:
        self._youtube = youtube
        self._videos = videos
        self._names = names
        self._search = search
        self._games = {g.id: g for g in draft.games}
        self._family_games: dict[str, list[str]] = {}
        for game in draft.games:
            self._family_games.setdefault(game.family_id, []).append(game.id)
        self._last_order: dict[str, int] = {}
        for track in draft.tracks:
            self._last_order[track.family] = max(self._last_order.get(track.family, 0), track.order)
        self._playlists: dict[str, list[Entry]] = {}
        self._albums: dict[str, Video] = {}
        self._playlist_noise: dict[str, frozenset[str]] = {}

    def match(self, track: DraftTrack, streams: Streams) -> Match:
        facts = self._facts(track)
        rejected = set(streams.reject)
        pool: dict[tuple[str, int, int | None], Scored] = {}
        stages = [self._playlist_candidates, self._search_candidates, self._album_candidates]
        for stage in stages:
            for candidate in stage(track, facts):
                if candidate.video_id in rejected:
                    continue
                scored = Scored(candidate, score(facts, candidate, self._names))
                key = _identity(candidate)
                if key not in pool or pool[key].score.value < scored.score.value:
                    pool[key] = scored
            if any(classify(s.score.value) is Verdict.ACCEPT for s in pool.values()):
                break
        ranked = sorted(pool.values(), key=lambda s: -s.score.value)
        sources = [_stream(s.videoId, s.startMs, s.endMs, 1.0) for s in streams.accept]
        taken = {(s.videoId, s.startMs, s.endMs) for s in sources}
        for scored in ranked:
            c = scored.candidate
            accepted = classify(scored.score.value) is Verdict.ACCEPT
            if len(sources) < MAX_SOURCES and accepted and _identity(c) not in taken:
                value = round(scored.score.value, 2)
                sources.append(_stream(c.video_id, c.start_ms, c.end_ms, value))
        if sources:
            status: Status = "matched"
        elif any(classify(s.score.value) is Verdict.REVIEW for s in ranked):
            status = "review"
        else:
            status = "unmatched"
        return Match(track.id, track.family, track.title, status, sources, ranked[:KEPT_CANDIDATES])

    def _facts(self, track: DraftTrack) -> TrackFacts:
        games = track.games or self._family_games[track.family]
        last = self._last_order.get(track.family, 0)
        return TrackFacts(
            family=track.family,
            games=frozenset(games),
            title=track.title,
            duration_ms=track.durationMs,
            position=track.order / last if last > 0 else None,
            game_boy=all(self._games[g].platform in _GAME_BOY for g in games),
        )

    def _playlist_candidates(self, track: DraftTrack, facts: TrackFacts) -> Iterable[Candidate]:
        for playlist in self._youtube.playlists:
            if not facts.games & set(playlist.games):
                continue
            if playlist.playlist not in self._playlists:
                self._playlists[playlist.playlist] = self._videos.playlist(playlist.playlist)
            all_entries = self._playlists[playlist.playlist]
            noise = self._noise(playlist.playlist, all_entries)
            first = playlist.first or 1
            last = playlist.last or max((e.position for e in all_entries), default=first)
            for entry in all_entries:
                if first <= entry.position <= last:
                    position = (entry.position - first) / (last - first) if last > first else None
                    yield Candidate(entry.id, entry.title, entry.duration_ms, entry.channel,
                                    "playlist", frozenset(playlist.games), position,
                                    noise=noise)  # fmt: skip

    def _noise(self, playlist_id: str, entries: list[Entry]) -> frozenset[str]:
        """Words in most of a playlist's titles, such as " - Pokémon Red/Blue Soundtrack"."""
        if playlist_id not in self._playlist_noise:
            counts = Counter(w for e in entries for w in set(video_words(e.title)))
            common = {w for w, n in counts.items() if n >= NOISE_SHARE * len(entries)}
            self._playlist_noise[playlist_id] = frozenset(
                common if len(entries) >= NOISE_MIN_ENTRIES else ()
            )
        return self._playlist_noise[playlist_id]

    def _search_candidates(self, track: DraftTrack, facts: TrackFacts) -> Iterable[Candidate]:
        if not self._search:
            return
        game_names = [self._games[g].name for g in self._family_games[track.family]
                      if g in facts.games]  # fmt: skip
        query = f"Pokémon {' '.join(game_names)} {track.title}"
        for entry in self._videos.search(query, SEARCH_RESULTS):
            yield Candidate(entry.id, entry.title, entry.duration_ms, entry.channel, "search")

    def _album_candidates(self, track: DraftTrack, facts: TrackFacts) -> Iterable[Candidate]:
        for album in self._youtube.albums:
            if not facts.games & set(album.games):
                continue
            if album.video not in self._albums:
                self._albums[album.video] = self._videos.video(album.video)
            video = self._albums[album.video]
            count = len(video.chapters)
            for index, chapter in enumerate(video.chapters):
                end = chapter.end_ms if chapter.end_ms is not None else video.duration_ms
                length = end - chapter.start_ms if end is not None else None
                yield Candidate(
                    video.id, chapter.title, length, None, "album", frozenset(album.games),
                    index / (count - 1) if count > 1 else None, chapter.start_ms, chapter.end_ms,
                )  # fmt: skip


def candidates_json(matches: list[Match]) -> list[dict[str, Any]]:
    """Each track's status and best candidates, for the review tool and the labeled set."""
    return [
        {
            "id": m.track_id,
            "family": m.family,
            "title": m.title,
            "status": m.status,
            "candidates": [
                {
                    "videoId": s.candidate.video_id,
                    "startMs": s.candidate.start_ms,
                    "endMs": s.candidate.end_ms,
                    "title": s.candidate.title,
                    "channel": s.candidate.channel,
                    "durationMs": s.candidate.duration_ms,
                    "kind": s.candidate.kind,
                    "games": sorted(s.candidate.games),
                    "position": s.candidate.position,
                    "score": round(s.score.value, 3),
                    "reasons": s.score.reasons,
                }
                for s in m.candidates
            ],
        }
        for m in matches
    ]


def _stream(video_id: str, start_ms: int, end_ms: int | None, confidence: float) -> StreamSource:
    return StreamSource(
        type="stream", videoId=video_id, startMs=start_ms, endMs=end_ms, confidence=confidence
    )


def _identity(candidate: Candidate) -> tuple[str, int, int | None]:
    return (candidate.video_id, candidate.start_ms, candidate.end_ms)
