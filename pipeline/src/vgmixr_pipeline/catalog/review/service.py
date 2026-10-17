"""The review tool's request handlers, as plain methods that take and return JSON-ready data.

The tool loads the draft (pipeline/out/draft.json) and the data files, then matches every track
from the YouTube cache, with the builder's own code. Matching never makes requests: only explicit
actions (a search, looking up a pasted video) use the live client, spaced out. When YouTube asks to
confirm we're not a bot, the tool falls back to the cache for a cooldown, and the page says so.
Every decision is written to pipeline/data/ (see edits.py) and the track is rematched at once.
"""

import subprocess
import sys
import time
from collections.abc import Callable
from pathlib import Path
from typing import Any

from vgmixr_pipeline.catalog.build import Draft, DraftTrack
from vgmixr_pipeline.catalog.config import (
    Slice,
    Streams,
    TrackOverride,
    load_games,
    load_overrides,
)
from vgmixr_pipeline.catalog.jingles import is_jingle
from vgmixr_pipeline.catalog.match import (
    SEARCH_RESULTS,
    Match,
    Scored,
    Videos,
    match_tracks,
    scored_json,
    search_query,
)
from vgmixr_pipeline.catalog.review import edits
from vgmixr_pipeline.catalog.review.video_id import video_id_from
from vgmixr_pipeline.catalog.score import Candidate, Names, TrackFacts, score
from vgmixr_pipeline.catalog.youtube import Video, YouTubeBlocked
from vgmixr_pipeline.manifest.models import Game

LIVE_SPACING_SECONDS = 5.0
BLOCK_COOLDOWN_SECONDS = 30 * 60.0
_STATUS_RANK = {"review": 0, "unmatched": 1, "matched": 2, "excluded": 3}
_STATUSES = list(_STATUS_RANK)

# Runs the whole builder; returns whether it succeeded and what it printed.
type Rebuild = Callable[[], tuple[bool, str]]


class ReviewError(Exception):
    """A request the tool refuses; nothing was written."""

    def __init__(self, errors: list[str]) -> None:
        super().__init__("\n".join(errors))
        self.errors = errors


def run_builder() -> tuple[bool, str]:
    """The builder from the cache only, as `python -m vgmixr_pipeline.catalog --cached-only`."""
    result = subprocess.run(
        [sys.executable, "-m", "vgmixr_pipeline.catalog", "--cached-only"],
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    return result.returncode == 0, (result.stdout + result.stderr).strip()


class Review:
    def __init__(
        self,
        data_dir: Path,
        out_dir: Path,
        cached: Videos,
        live: Videos | None,
        clock: Callable[[], float] = time.time,
        sleep: Callable[[float], Any] = time.sleep,
        rebuild: Rebuild = run_builder,
        cached_only: bool = False,
    ) -> None:
        self._data_dir = data_dir
        self._out_dir = out_dir
        self._cached = cached
        self._live = None if cached_only else live
        self._clock = clock
        self._sleep = sleep
        self._rebuild = rebuild
        self._blocked_until: float | None = None
        self._last_live: float | None = None
        # Videos looked up live in this session, for details the cached-only client can't see.
        self._details: dict[str, Video] = {}
        self._load()

    # Loading and matching

    def _load(self) -> None:
        self._games = load_games(self._data_dir / "games.json")
        self._overrides = load_overrides(self._data_dir / "overrides.json")
        draft = Draft.model_validate_json(
            (self._out_dir / "draft.json").read_text(encoding="utf-8")
        )
        # Title overrides apply at once, as the next build would apply them.
        titles = {k: o.title for k, o in self._overrides.tracks.items() if o.title is not None}
        self._draft = draft.model_copy(update={"tracks": [
            t.model_copy(update={"title": titles[t.id]}) if t.id in titles else t
            for t in draft.tracks
        ]})  # fmt: skip
        self._names = Names.build(self._games.games, self._games.youtube.aliases)
        self._tracks = {t.id: t for t in self._draft.tracks}
        self._families: dict[str, list[Game]] = {}
        for game in self._draft.games:
            self._families.setdefault(game.family_id, []).append(game)
        self._matches = {m.track_id: m for m in self._match(None)}

    def _match(self, only: set[str] | None) -> list[Match]:
        youtube = self._games.youtube
        return match_tracks(
            self._draft, youtube, self._overrides, self._cached, self._names, only=only
        )

    # Endpoints

    def state(self) -> dict[str, Any]:
        return {
            "families": [self._family(f, games) for f, games in self._families.items()],
            "youtube": self._youtube_mode(),
        }

    def tracks(self, family: str | None, status: str | None) -> list[dict[str, Any]]:
        rows = [
            self._row(t, self._matches[t.id])
            for t in self._draft.tracks
            if (family is None or t.family == family)
            and (status in (None, "all") or self._status(t.id) == status)
        ]
        return sorted(rows, key=lambda r: (_STATUS_RANK[r["status"]], r["order"]))

    def track(self, track_id: str) -> dict[str, Any]:
        track = self._track(track_id)
        match = self._matches[track_id]
        facts = self._facts(match)
        decision = self._decision(track_id)
        accepted = {(s.videoId, s.startMs, s.endMs) for s in decision.accept}
        taken = {(s.videoId, s.startMs, s.endMs) for s in match.streams}
        return {
            "id": track.id,
            "family": track.family,
            "title": track.title,
            "category": track.category,
            "order": track.order,
            "games": [g.id for g in self._families[track.family] if g.id in facts.games],
            "exclusive": track.games is not None,
            "durationMs": track.durationMs,
            "origins": track.origins,
            "status": self._status(track_id),
            "streams": [
                {
                    "videoId": s.videoId,
                    "startMs": s.startMs,
                    "endMs": s.endMs,
                    "confidence": s.confidence,
                    "auto": (s.videoId, s.startMs, s.endMs) not in accepted,
                }
                for s in match.streams
            ],
            "decision": decision.model_dump(),
            "candidates": [
                {**scored_json(s), "accepted": _identity(s.candidate) in taken}
                for s in match.candidates
            ],
            # Hand-accepted slices in order, with details even when they aren't top candidates.
            "accepted": [self._accepted_json(facts, match, s) for s in decision.accept],
            "query": search_query(facts, self._families[track.family]),
        }

    def set_streams(self, track_id: str, streams: dict[str, Any]) -> dict[str, Any]:
        self._track(track_id)
        try:
            edits.set_streams(
                self._data_dir / "overrides.json",
                track_id,
                streams,
                self._tracks.keys(),
                list(self._families),
            )
        except edits.EditError as e:
            raise ReviewError(e.errors) from e
        self._overrides = load_overrides(self._data_dir / "overrides.json")
        self._rematch({track_id})
        self._look_up_accepted(track_id)
        return self.track(track_id)

    def exclude(self, track_id: str, removed: bool) -> dict[str, Any]:
        """Removes a track from the catalog (`exclude: true`), or restores it. A restored track
        the jingle rule would drop again keeps `exclude: false`."""
        track = self._track(track_id)
        value = True if removed else (False if is_jingle(track.title, track.durationMs) else None)
        try:
            edits.set_exclude(
                self._data_dir / "overrides.json",
                track_id,
                value,
                self._tracks.keys(),
                list(self._families),
            )
        except edits.EditError as e:
            raise ReviewError(e.errors) from e
        self._overrides = load_overrides(self._data_dir / "overrides.json")
        return self.track(track_id)

    def search(self, track_id: str, query: str) -> dict[str, Any]:
        facts = self._facts(self._matches[self._track(track_id).id])
        query = query.strip() or search_query(facts, self._families[facts.family])
        found, notice = self._ask(lambda videos: videos.search(query, SEARCH_RESULTS))
        rejected = set(self._decision(track_id).reject)
        scored = [
            Scored(c, score(facts, c, self._names))
            for c in (
                Candidate(e.id, e.title, e.duration_ms, e.channel, "search")
                for e in found
                if e.id not in rejected
            )
        ]
        # The builder's own query is cached now, so a rematch can use its results.
        self._rematch({track_id})
        return {
            "query": query,
            "results": [scored_json(s) for s in sorted(scored, key=lambda s: -s.score.value)],
            "youtube": self._youtube_mode(),
            **({"notice": notice} if notice else {}),
        }

    def video(
        self, track_id: str, text: str, start_ms: int | None, end_ms: int | None
    ) -> dict[str, Any]:
        facts = self._facts(self._matches[self._track(track_id).id])
        video_id = video_id_from(text)
        if video_id is None:
            raise ReviewError([f"{text.strip()!r} is not a YouTube video id or link"])
        try:
            piece = Slice(videoId=video_id, startMs=start_ms or 0, endMs=end_ms)
        except ValueError as e:
            raise ReviewError([str(e)]) from e
        video, notice = self._ask(lambda videos: videos.video(video_id))
        if video.title:
            self._details[video_id] = video
        extra = {"youtube": self._youtube_mode(), **({"notice": notice} if notice else {})}
        return {"candidate": self._slice_json(facts, piece, video), **extra}

    def rebuild(self) -> dict[str, Any]:
        ok, output = self._rebuild()
        self._load()
        return {"ok": ok, "output": output}

    def retry_youtube(self) -> dict[str, Any]:
        self._blocked_until = None
        return self._youtube_mode()

    # Helpers

    def _ask[T](self, call: Callable[[Videos], T]) -> tuple[T, str | None]:
        """Asks the live client when YouTube may be asked, else the cache; a block falls back."""
        if self._live is None or self._is_blocked():
            return call(self._cached), None
        if self._last_live is not None:
            wait = LIVE_SPACING_SECONDS - (self._clock() - self._last_live)
            if wait > 0:
                self._sleep(wait)
        self._last_live = self._clock()
        try:
            return call(self._live), None
        except YouTubeBlocked as e:
            self._blocked_until = self._clock() + BLOCK_COOLDOWN_SECONDS
            return call(self._cached), f"YouTube is blocking requests ({e}); using the cache."
        except Exception as e:  # yt-dlp's errors: unavailable video, network...
            raise ReviewError([f"YouTube: {e}"]) from e

    def _is_blocked(self) -> bool:
        return self._blocked_until is not None and self._clock() < self._blocked_until

    def _youtube_mode(self) -> dict[str, Any]:
        if self._live is None:
            return {"mode": "cached-only"}
        if self._is_blocked():
            return {"mode": "blocked", "until": self._blocked_until}
        return {"mode": "live"}

    def _rematch(self, only: set[str]) -> None:
        for match in self._match(only):
            self._matches[match.track_id] = match

    def _accepted_json(self, facts: TrackFacts, match: Match, piece: Slice) -> dict[str, Any]:
        """A hand-accepted slice: its candidate when it is one, else scored from the cached video
        (pasted or searched videos are cached when looked up)."""
        identity = (piece.videoId, piece.startMs, piece.endMs)
        for scored in match.candidates:
            if _identity(scored.candidate) == identity:
                return scored_json(scored)
        return self._slice_json(facts, piece, self._video(piece.videoId))

    def _video(self, video_id: str) -> Video:
        return self._details.get(video_id) or self._cached.video(video_id)

    def _look_up_accepted(self, track_id: str) -> None:
        """Looks up hand-accepted videos whose details aren't known yet (e.g. accepted by id),
        when YouTube may be asked. A failed lookup only leaves the card without details."""
        match = self._matches[track_id]
        candidates = {s.candidate.video_id for s in match.candidates}
        for piece in self._decision(track_id).accept:
            video_id = piece.videoId
            if video_id not in candidates and not self._video(video_id).title:
                self._look_up(video_id)

    def _look_up(self, video_id: str) -> None:
        try:
            video, _ = self._ask(lambda videos: videos.video(video_id))
        except ReviewError:
            return
        if video.title:
            self._details[video_id] = video

    def _slice_json(self, facts: TrackFacts, piece: Slice, video: Video) -> dict[str, Any]:
        """A slice of `video` scored against the track, or unscored when its details are unknown."""
        if not video.title:
            return {"videoId": piece.videoId, "startMs": piece.startMs, "endMs": piece.endMs,
                    "title": None, "channel": None, "durationMs": None, "kind": "search",
                    "score": None, "reasons": ["no details: not cached"]}  # fmt: skip
        end = piece.endMs if piece.endMs is not None else video.duration_ms
        length = end - piece.startMs if end is not None else None
        candidate = Candidate(piece.videoId, video.title, length, None, "search",
                              start_ms=piece.startMs, end_ms=piece.endMs)  # fmt: skip
        return scored_json(Scored(candidate, score(facts, candidate, self._names)))

    def _status(self, track_id: str) -> str:
        """The match status, or "excluded" for a track removed since the last build."""
        if self._overrides.tracks.get(track_id, TrackOverride()).exclude:
            return "excluded"
        return self._matches[track_id].status

    def _track(self, track_id: str) -> DraftTrack:
        if track_id not in self._tracks:
            raise ReviewError([f"{track_id} is not a track in the draft"])
        return self._tracks[track_id]

    def _facts(self, match: Match) -> TrackFacts:
        assert match.facts is not None  # match_tracks always sets them
        return match.facts

    def _decision(self, track_id: str) -> Streams:
        return self._overrides.tracks.get(track_id, TrackOverride()).streams or Streams()

    def _family(self, family: str, games: list[Game]) -> dict[str, Any]:
        ids = {g.id for g in games}
        statuses = [self._status(m.track_id) for m in self._matches.values() if m.family == family]
        return {
            "id": family,
            "label": "".join(g.shortName for g in games),
            "name": " / ".join(g.name for g in games),
            "games": [{"id": g.id, "shortName": g.shortName, "color": g.color} for g in games],
            # "excluded" only appears once a track was removed since the last build.
            "counts": {s: statuses.count(s) for s in _STATUSES if s != "excluded" or s in statuses},
            "playlists": sum(bool(ids & set(p.games)) for p in self._games.youtube.playlists),
        }

    def _row(self, track: DraftTrack, match: Match) -> dict[str, Any]:
        override = self._overrides.tracks.get(track.id, TrackOverride())
        return {
            "id": track.id,
            "title": track.title,
            "status": self._status(track.id),
            "category": track.category,
            "games": track.games,
            "order": track.order,
            "durationMs": track.durationMs,
            "best": round(match.candidates[0].score.value, 3) if match.candidates else None,
            # The first source's confidence (1.0 when picked by hand), for matched tracks.
            "confidence": match.streams[0].confidence if match.streams else None,
            "sources": len(match.streams),
            "decided": override.streams is not None,
        }


def _identity(candidate: Candidate) -> tuple[str, int, int | None]:
    return (candidate.video_id, candidate.start_ms, candidate.end_ms)
