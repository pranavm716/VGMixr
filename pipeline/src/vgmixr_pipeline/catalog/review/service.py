"""The review tool's request handlers, as plain methods that take and return JSON-ready data.

The tool loads the draft (pipeline/out/draft.json) and the data files, then matches every track
from the YouTube cache, with the builder's own code. Matching never makes requests: only explicit
actions (a search, looking up a pasted video) use the live client, spaced out. When YouTube asks to
confirm we're not a bot, the tool falls back to the cache for a cooldown, and the page says so.
Every decision is written to pipeline/data/ (see edits.py) and the track is rematched at once.

Playlists: the page ranks families by their gaps, searches YouTube for playlists and previews one
by rematching its families with it added (or removed), from the cache only. A playlist is fetched
once, live, and then read from the cache; adding it writes games.json and rematches its families.
Hand decisions stay first whatever the playlists are, since matching puts them first.

Broken: the videos the app reported (the pulled broken.json, read on every request), joined to
the draft's tracks, with each video's re-check the way the app sees it (vgmixr_pipeline.broken.
recheck). Re-checks are cached and asked only on request, spaced like the other live calls.
"""

import json
import subprocess
import sys
import time
from collections.abc import Callable
from datetime import datetime
from pathlib import Path
from typing import Any

from pydantic import ValidationError

from vgmixr_pipeline.broken import BrokenMark, read_pulled
from vgmixr_pipeline.broken.recheck import AppCheck, AppChecks, CheckFailed
from vgmixr_pipeline.catalog import slug
from vgmixr_pipeline.catalog.build import Draft, DraftTrack
from vgmixr_pipeline.catalog.config import (
    Playlist,
    Slice,
    Streams,
    TrackOverride,
    YouTube,
    load_games,
    load_overrides,
)
from vgmixr_pipeline.catalog.ids import catalog_id, load_ids
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
from vgmixr_pipeline.catalog.review.video_id import playlist_id_from, video_id_from
from vgmixr_pipeline.catalog.score import Candidate, Names, TrackFacts, classify, score
from vgmixr_pipeline.catalog.youtube import Entry, Video, YouTubeBlocked
from vgmixr_pipeline.manifest.models import Game

LIVE_SPACING_SECONDS = 5.0
BLOCK_COOLDOWN_SECONDS = 30 * 60.0
_STATUSES = ["review", "unmatched", "matched", "excluded"]

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
        manifest_path: Path | None = None,
        app_checks: AppChecks | None = None,
    ) -> None:
        self._data_dir = data_dir
        self._out_dir = out_dir
        self._manifest_path = manifest_path
        self._app_checks = app_checks
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
        self._load_games()
        self._overrides = load_overrides(self._data_dir / "overrides.json")
        # The frozen catalog ids (source key -> id), which the builder adds to on every Rebuild.
        self._frozen = load_ids(self._data_dir / "ids.json")
        self._frozen_owner = {id_: key for key, id_ in self._frozen.items()}
        draft = Draft.model_validate_json(
            (self._out_dir / "draft.json").read_text(encoding="utf-8")
        )
        self._families: dict[str, list[Game]] = {}
        for game in draft.games:
            self._families.setdefault(game.family_id, []).append(game)
        # The tracks as built, before the overrides changed in this session.
        self._built = {t.id: t for t in draft.tracks}
        self._draft = draft.model_copy(
            update={"tracks": [self._overridden(t) for t in draft.tracks]}
        )
        self._tracks = {t.id: t for t in self._draft.tracks}
        self._matches = {m.track_id: m for m in self._match(None)}
        self._load_manifest()

    def _load_manifest(self) -> None:
        """The manifest's generatedAt and stream videos, for the Broken tab; None without one."""
        self._manifest_at: datetime | None = None
        self._manifest_videos: set[str] | None = None
        if self._manifest_path is None:
            return
        try:
            data = json.loads(self._manifest_path.read_text(encoding="utf-8"))
            self._manifest_at = datetime.fromisoformat(data["generatedAt"])
            self._manifest_videos = {
                s["videoId"] for t in data["tracks"] for s in t["sources"] if "videoId" in s
            }
        except (OSError, ValueError, KeyError, TypeError):
            self._manifest_at = self._manifest_videos = None

    def _overridden(self, track: DraftTrack) -> DraftTrack:
        """Title and games overrides apply at once, as the next build would apply them. Without a
        games override, a track has the games its track lists give it (when the draft says)."""
        override = self._overrides.tracks.get(track.id, TrackOverride())
        listed = self._listed_games(track)
        games = override.games or (listed if listed is not None else track.games)
        return track.model_copy(update={
            "title": override.title or track.title,
            "games": self._in_family_order(track.family, games),
        })  # fmt: skip

    def _listed_games(self, track: DraftTrack) -> list[str] | None:
        """The games the track's lists give it, every one named; None when the draft (written
        before the builder recorded them) doesn't say."""
        if "listedGames" not in track.model_fields_set:
            return None
        return track.listedGames or [g.id for g in self._families[track.family]]

    def _in_family_order(self, family: str, games: list[str] | None) -> list[str] | None:
        """`games` in family order, or None when they cover the whole family."""
        ids = [g.id for g in self._families[family]]
        if games is None or set(games) >= set(ids):
            return None
        return [g for g in ids if g in games]

    def _load_games(self) -> None:
        self._games = load_games(self._data_dir / "games.json")
        self._names = Names.build(self._games.games, self._games.youtube.aliases)
        self._game_family = {g.id: g.family_id for g in self._games.games}

    def _match(self, only: set[str] | None, youtube: YouTube | None = None) -> list[Match]:
        youtube = youtube or self._games.youtube
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
        return sorted(rows, key=lambda r: r["order"])

    def track(self, track_id: str) -> dict[str, Any]:
        track = self._track(track_id)
        match = self._matches[track_id]
        facts = self._facts(match)
        decision = self._decision(track_id)
        accepted = {(s.videoId, s.startMs, s.endMs) for s in decision.accept}
        taken = {(s.videoId, s.startMs, s.endMs) for s in match.streams}
        return {
            "id": track.id,
            # The id the manifest (and the app's reports) gives it: frozen in ids.json once it
            # shipped, else the slug of today's title.
            "catalogId": self._catalog_id(track),
            "idFrozen": track.id in self._frozen,
            "family": track.family,
            "title": track.title,
            "order": track.order,
            "games": [g.id for g in self._families[track.family] if g.id in facts.games],
            "exclusive": track.games is not None,
            # The games its track lists give it (None when the draft doesn't say), and whether a
            # games override replaces them.
            "listedGames": self._listed_games(self._built[track_id]),
            "gamesOverride": self._overrides.tracks.get(track_id, TrackOverride()).games
            is not None,
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
            "renamed": self._override(track_id).title is not None,
            "note": self._override(track_id).note,
            # The app's reports on this track or on a video it uses, and cached re-checks of its
            # videos (unchecked ones are left out).
            "reported": self._reported(track_id),
            "checks": {
                video_id: check
                for video_id in [
                    *(s.videoId for s in match.streams),
                    *(s.candidate.video_id for s in match.candidates),
                    *(s.videoId for s in decision.accept),
                ]
                if (check := self._check_json(video_id)) is not None
            },
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

    def set_games(self, track_id: str, games: Any) -> dict[str, Any]:
        """Sets the games a track plays in (a `games` override) and rematches it. The games its
        track lists give it, or None, remove the override."""
        track = self._track(track_id)
        family = [g.id for g in self._families[track.family]]
        listed = self._listed_games(self._built[track_id])
        if games is None:
            if listed is None:
                raise ReviewError(["Rebuild first: this draft doesn't say which games the track's "
                                   "lists give it"])  # fmt: skip
            value = None
        else:
            if (
                not isinstance(games, list)
                or not games
                or not all(isinstance(g, str) for g in games)
                or len(set(games)) != len(games)
                or not set(games) <= set(family)
            ):
                raise ReviewError([f"games must be distinct games of {', '.join(family)}"])
            ordered = [g for g in family if g in games]
            value = None if listed is not None and set(ordered) == set(listed) else ordered
        try:
            edits.set_games(
                self._data_dir / "overrides.json",
                track_id,
                value,
                self._tracks.keys(),
                list(self._families),
            )
        except edits.EditError as e:
            raise ReviewError(e.errors) from e
        self._overrides = load_overrides(self._data_dir / "overrides.json")
        self._refresh(track_id)
        return self.track(track_id)

    def move(
        self, track_id: str, before: str | None = None, after: str | None = None
    ) -> dict[str, Any]:
        """Moves a track right before or after another track of its family and writes the
        family's whole order to order.json. The target is a row of whichever tab is shown, so a
        move from a filtered tab is still well defined. Matching doesn't use the order."""
        track = self._track(track_id)
        if (before is None) == (after is None):
            raise ReviewError(["name the track to move before or after"])
        target_id = before if before is not None else after
        assert target_id is not None
        target = self._track(target_id)
        if target.family != track.family:
            raise ReviewError([f"{target_id} is not a track of {track.family}"])
        if target_id == track_id:
            raise ReviewError(["a track can't move next to itself"])
        ids = [t.id for t in self._in_order(track.family) if t.id != track_id]
        at = ids.index(target_id) + (0 if before is not None else 1)
        ids.insert(at, track_id)
        try:
            edits.set_order(self._data_dir / "order.json", track.family, ids, list(self._families))
        except edits.EditError as e:
            raise ReviewError(e.errors) from e
        position = {id_: i for i, id_ in enumerate(ids)}
        self._replace({
            id_: self._tracks[id_].model_copy(update={"order": i}) for id_, i in position.items()
        })  # fmt: skip
        return {"family": track.family, "order": ids}

    def rename(self, track_id: str, title: str) -> dict[str, Any]:
        """Sets a track's title (a `title` override); a frozen catalog id stays as it is. Its
        current videos are pinned first, as accepted ones in order, since matching scores titles
        and cached searches are keyed by the old one: a rename never drops or reorders a source."""
        track = self._track(track_id)
        title = " ".join(str(title).split())
        if not title:
            raise ReviewError(["a title can't be empty"])
        if title == track.title:
            raise ReviewError([f"the track is already titled {title!r}"])
        # A family's titles stay distinct (titles that slug alike fail the build).
        title_slug = slug.track_id(track.family, title)
        for other in self._draft.tracks:
            if other.family == track.family and other.id != track_id:
                if slug.track_id(other.family, other.title) == title_slug:
                    raise ReviewError([f"{other.id} is already titled {other.title!r}"])
        # A track without a frozen id would ship under the slug, and ids are never reused.
        owner = self._frozen_owner.get(title_slug)
        if track_id not in self._frozen and owner is not None:
            raise ReviewError([f"ids.json already gives {title_slug} to {owner}; ids are never "
                               "reused, so pick another title"])  # fmt: skip
        decision = self._decision(track_id)
        pinned = [Slice(videoId=s.videoId, startMs=s.startMs, endMs=s.endMs)
                  for s in self._matches[track_id].streams]  # fmt: skip
        streams = None
        if pinned != list(decision.accept):
            streams = {"accept": [p.model_dump() for p in pinned], "reject": list(decision.reject)}
        try:
            edits.rename(
                self._data_dir / "overrides.json",
                track_id,
                title,
                streams,
                self._tracks.keys(),
                list(self._families),
            )
        except edits.EditError as e:
            raise ReviewError(e.errors) from e
        self._overrides = load_overrides(self._data_dir / "overrides.json")
        self._refresh(track_id)
        return self.track(track_id)

    def set_note(self, track_id: str, note: str) -> dict[str, Any]:
        """Sets a track's hand-written note (the override's `note`) as one line; blank clears it."""
        self._track(track_id)
        text = " ".join(str(note).split()) or None
        try:
            edits.set_note(
                self._data_dir / "overrides.json",
                track_id,
                text,
                self._tracks.keys(),
                list(self._families),
            )
        except edits.EditError as e:
            raise ReviewError(e.errors) from e
        self._overrides = load_overrides(self._data_dir / "overrides.json")
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

    # Playlists

    def playlists(self) -> dict[str, Any]:
        """Families with their gaps, those without playlists first, then by the share of tracks
        without sources; per game, its playlists and gaps; each family's playlists."""
        order = list(self._families)

        def rank(family: dict[str, Any]) -> tuple[bool, float, int]:
            counts = family["counts"]
            total = counts["matched"] + counts["review"] + counts["unmatched"]
            missing = (counts["review"] + counts["unmatched"]) / total if total else 0.0
            return (bool(family["playlists"]), -missing, order.index(family["id"]))

        families = [self._coverage(f, games) for f, games in self._families.items()]
        return {"families": sorted(families, key=rank), "youtube": self._youtube_mode()}

    def search_playlists(self, family: str, query: str) -> dict[str, Any]:
        if family not in self._families:
            raise ReviewError([f"{family} is not a family"])
        names = " ".join(g.name for g in self._families[family])
        query = query.strip() or f"Pokémon {names} soundtrack"
        hits, notice = self._ask(lambda videos: videos.search_playlists(query))
        playlists = self._games.youtube.playlists
        results = [
            {
                "id": hit.id,
                "title": hit.title,
                "channel": hit.channel,
                "listed": [i for i, p in enumerate(playlists) if p.playlist == hit.id],
                "entries": len(self._cached.playlist(hit.id)) or None,
            }
            for hit in hits
        ]
        extra = {"youtube": self._youtube_mode(), **({"notice": notice} if notice else {})}
        return {"query": query, "results": results, **extra}

    def preview_playlist(
        self, text: str, games: Any, first: int | None, last: int | None
    ) -> dict[str, Any]:
        """What adding the playlist would change; writes nothing."""
        playlist = self._playlist(text, games, first, last, None)
        entries, notice = self._entries(playlist.playlist)
        youtube = self._games.youtube
        added = youtube.model_copy(update={"playlists": [*youtube.playlists, playlist]})
        extra = {"youtube": self._youtube_mode(), **({"notice": notice} if notice else {})}
        return {**self._preview(added, playlist, entries, added=True), **extra}

    def preview_removal(self, index: int, playlist_id: str) -> dict[str, Any]:
        """What removing the playlist at `index` would change; writes nothing."""
        playlist = self._listed(index, playlist_id)
        youtube = self._games.youtube
        rest = [p for i, p in enumerate(youtube.playlists) if i != index]
        removed = youtube.model_copy(update={"playlists": rest})
        entries = self._cached.playlist(playlist_id)
        preview = self._preview(removed, playlist, entries, added=False)
        return {**preview, "youtube": self._youtube_mode()}

    def add_playlist(
        self, text: str, games: Any, first: int | None, last: int | None, note: str | None
    ) -> dict[str, Any]:
        playlist = self._playlist(text, games, first, last, note)
        self._entries(playlist.playlist)  # fetched once, so the rematch reads it from the cache
        try:
            edits.add_playlist(
                self._data_dir / "games.json", playlist.model_dump(exclude_none=True)
            )
        except edits.EditError as e:
            raise ReviewError(e.errors) from e
        self._after_playlist_edit(playlist)
        return self.playlists()

    def remove_playlist(self, index: int, playlist_id: str) -> dict[str, Any]:
        playlist = self._listed(index, playlist_id)
        try:
            edits.remove_playlist(self._data_dir / "games.json", index, playlist_id)
        except edits.EditError as e:
            raise ReviewError(e.errors) from e
        self._after_playlist_edit(playlist)
        return self.playlists()

    def rebuild(self) -> dict[str, Any]:
        ok, output = self._rebuild()
        self._load()
        return {"ok": ok, "output": output}

    def retry_youtube(self) -> dict[str, Any]:
        self._blocked_until = None
        return self._youtube_mode()

    # Broken

    def broken(self) -> dict[str, Any]:
        """The app's reports, those whose video a draft track still uses first, newest first."""
        pulled = read_pulled(self._out_dir)
        marks = sorted(pulled.broken.marks if pulled.broken else [],
                       key=lambda m: m.marked_at, reverse=True)  # fmt: skip
        catalog_ids = self._catalog_ids()
        reports = sorted(
            (self._report(m, catalog_ids) for m in marks), key=lambda r: r["status"] != "needs-fix"
        )
        return {
            "pulled": {
                "exists": pulled.broken is not None or pulled.error is not None,
                "at": _iso(pulled.pulled_at),
                "error": pulled.error,
            },
            "manifestGeneratedAt": _iso(self._manifest_at),
            "reports": reports,
            "youtube": self._youtube_mode(),
        }

    def app_check(self, text: str) -> dict[str, Any]:
        """Re-checks a video the way the app sees it, now. While YouTube blocks requests, the
        last check (if any) is kept and the notice says so; network trouble is an error."""
        video_id = video_id_from(text)
        if video_id is None:
            raise ReviewError([f"{text.strip()!r} is not a YouTube video id or link"])
        checks = self._app_checks
        if checks is None or self._live is None:
            raise ReviewError(["YouTube can't be asked (cached-only): App check needs it"])
        notice = None
        if self._is_blocked():
            check, notice = self._cached_check(video_id), "YouTube is blocking requests"
        else:
            try:
                check = self._spaced(lambda: checks.fresh(video_id))
            except YouTubeBlocked as e:
                check, notice = self._cached_check(video_id), f"YouTube is blocking requests ({e})"
            except CheckFailed as e:
                raise ReviewError([f"Couldn't check {video_id}: {e}"]) from e
        if notice:
            notice += "; showing the last check." if check else "; not checked."
        return {
            "check": check.model_dump(mode="json", by_alias=True) if check else None,
            "youtube": self._youtube_mode(),
            **({"notice": notice} if notice else {}),
        }

    def _catalog_id(self, track: DraftTrack) -> str:
        return catalog_id(self._frozen, track.id, track.family, track.title)

    def _catalog_ids(self) -> dict[str, str]:
        """Catalog ids (the manifest's, the app's) to source keys: the frozen ones, then for
        tracks without one, from the titles as built, which the phone has, and from today's
        titles, so a rename in this session still resolves."""

        def ids(tracks: Any) -> dict[str, str]:
            return {
                slug.track_id(t.family, t.title): t.id for t in tracks if t.id not in self._frozen
            }

        frozen = {id_: key for key, id_ in self._frozen.items() if key in self._tracks}
        return {**ids(self._tracks.values()), **ids(self._built.values()), **frozen}

    def _report(self, mark: BrokenMark, catalog_ids: dict[str, str]) -> dict[str, Any]:
        used_by = self._tracks_using(mark.video_id)
        guess = self._family_of(mark.track_id)
        key = catalog_ids.get(mark.track_id)
        track = (
            self._tracks[key]
            if key is not None
            # A track renamed since: the one using the reported video, in its family if possible.
            else next((t for t in used_by if t.family == guess), next(iter(used_by), None))
        )
        sources = self._matches[track.id].streams if track is not None else []
        return {
            "trackId": mark.track_id,
            "videoId": mark.video_id,
            "reason": mark.reason,
            "detail": mark.detail,
            "markedAt": _iso(mark.marked_at),
            "catalogGeneratedAt": _iso(mark.catalog_generated_at),
            "olderCatalog": None
            if self._manifest_at is None
            else mark.catalog_generated_at != self._manifest_at,
            "track": _ref(track) if track is not None else None,
            "family": track.family if track is not None else guess,
            "status": "needs-fix" if used_by else "fixed",
            "inManifest": None
            if self._manifest_videos is None
            else mark.video_id in self._manifest_videos,
            "usedBy": [_ref(t) for t in used_by],
            "check": self._check_json(mark.video_id),
            "sources": [
                {
                    "videoId": s.videoId,
                    "startMs": s.startMs,
                    "endMs": s.endMs,
                    "check": self._check_json(s.videoId),
                }
                for s in sources
            ],  # fmt: skip
        }

    def _reported(self, track_id: str) -> list[dict[str, Any]]:
        def concerns(report: dict[str, Any]) -> bool:
            refs = [report["track"], *report["usedBy"]]
            return any(ref is not None and ref["id"] == track_id for ref in refs)

        keys = ["trackId", "videoId", "reason", "detail", "markedAt", "check"]
        return [{k: r[k] for k in keys} for r in self.broken()["reports"] if concerns(r)]

    def _tracks_using(self, video_id: str) -> list[DraftTrack]:
        """The draft's tracks (not removed) whose sources, as matched today, use the video."""
        return [
            t
            for t in self._draft.tracks
            if self._status(t.id) != "excluded"
            and any(
                getattr(s, "videoId", None) == video_id
                for s in [*t.sources, *self._matches[t.id].streams]
            )
        ]

    def _family_of(self, catalog_id: str) -> str | None:
        """The family a catalog id starts with (the longest, should one family's id prefix
        another's)."""
        families = [f for f in self._families if catalog_id.startswith(f + "-")]
        return max(families, key=len, default=None)

    def _cached_check(self, video_id: str) -> AppCheck | None:
        if self._app_checks is None:
            return None
        try:
            return self._app_checks.cached(video_id)
        except ValueError:  # not a video id
            return None

    def _check_json(self, video_id: str) -> dict[str, Any] | None:
        check = self._cached_check(video_id)
        return check.model_dump(mode="json", by_alias=True) if check is not None else None

    # Helpers

    def _ask[T](self, call: Callable[[Videos], T]) -> tuple[T, str | None]:
        """Asks the live client when YouTube may be asked, else the cache; a block falls back."""
        live = self._live
        if live is None or self._is_blocked():
            return call(self._cached), None
        try:
            return self._spaced(lambda: call(live)), None
        except YouTubeBlocked as e:
            return call(self._cached), f"YouTube is blocking requests ({e}); using the cache."
        except Exception as e:  # yt-dlp's errors: unavailable video, network...
            raise ReviewError([f"YouTube: {e}"]) from e

    def _spaced[T](self, call: Callable[[], T]) -> T:
        """A live call, spaced from the last one; a bot check starts the cooldown and re-raises."""
        if self._last_live is not None:
            wait = LIVE_SPACING_SECONDS - (self._clock() - self._last_live)
            if wait > 0:
                self._sleep(wait)
        self._last_live = self._clock()
        try:
            return call()
        except YouTubeBlocked:
            self._blocked_until = self._clock() + BLOCK_COOLDOWN_SECONDS
            raise

    def _playlist(
        self, text: str, games: Any, first: int | None, last: int | None, note: str | None
    ) -> Playlist:
        playlist_id = playlist_id_from(text)
        if playlist_id is None:
            raise ReviewError([f"{text.strip()!r} is not a YouTube playlist id or link"])
        data = {"games": games, "playlist": playlist_id, "first": first, "last": last,
                "note": note or None}  # fmt: skip
        try:
            playlist = Playlist.model_validate(data)
        except ValidationError as e:
            raise ReviewError([err["msg"] for err in e.errors()]) from e
        unknown = [g for g in playlist.games if g not in self._game_family]
        if not playlist.games or unknown:
            raise ReviewError([f"pick the playlist's games (unknown: {', '.join(unknown)})"])
        return playlist

    def _listed(self, index: int, playlist_id: str) -> Playlist:
        playlists = self._games.youtube.playlists
        if not 0 <= index < len(playlists) or playlists[index].playlist != playlist_id:
            raise ReviewError([f"playlist {index + 1} is not {playlist_id}; reload the page"])
        return playlists[index]

    def _entries(self, playlist_id: str) -> tuple[list[Entry], str | None]:
        """A playlist's entries: from the cache, else fetched live (which caches them)."""
        cached = self._cached.playlist(playlist_id)
        if cached:
            return cached, None
        found, notice = self._ask(lambda videos: videos.playlist(playlist_id))
        if not found:
            mode = self._youtube_mode()["mode"]
            if mode == "live":
                raise ReviewError([f"YouTube lists no entries for {playlist_id}"])
            raise ReviewError([f"{playlist_id} is not cached and YouTube can't be asked ({mode})"])
        return found, notice

    def _playlist_families(self, playlist: Playlist) -> list[str]:
        """The families of a playlist's games, in games-file order."""
        families = {self._game_family[g] for g in playlist.games}
        return [f for f in self._families if f in families]

    def _family_tracks(self, families: list[str]) -> set[str]:
        return {t.id for t in self._draft.tracks if t.family in families}

    def _after_playlist_edit(self, playlist: Playlist) -> None:
        self._load_games()
        self._rematch(self._family_tracks(self._playlist_families(playlist)))

    def _preview(
        self, youtube: YouTube, playlist: Playlist, entries: list[Entry], added: bool
    ) -> dict[str, Any]:
        """Rematches the playlist's families with `youtube` (from the cache), against today's
        matches. Entries are matched to tracks in whichever side has the playlist."""
        families = self._playlist_families(playlist)
        only = self._family_tracks(families)
        after = {m.track_id: m for m in self._match(only, youtube)}
        before = {track_id: self._matches[track_id] for track_id in only}
        first = playlist.first or 1
        last = playlist.last or max((e.position for e in entries), default=first)
        return {
            "playlist": playlist.playlist,
            "games": playlist.games,
            "first": playlist.first,
            "last": playlist.last,
            "entries": [
                {
                    "position": e.position,
                    "videoId": e.id,
                    "title": e.title,
                    "durationMs": e.duration_ms,
                    "channel": e.channel,
                    "inRange": first <= e.position <= last,
                    "match": _entry_match(e, after if added else before),
                }
                for e in entries
            ],
            "counts": {
                f: {"before": self._counts(before, f), "after": self._counts(after, f)}
                for f in families
            },
            "changes": [
                self._change(t, before[t.id], after[t.id])
                for t in self._draft.tracks
                if t.id in only and _differs(before[t.id], after[t.id])
            ],
        }

    def _counts(self, matches: dict[str, Match], family: str) -> dict[str, int]:
        statuses = [self._status_of(m) for m in matches.values() if m.family == family]
        return {s: statuses.count(s) for s in _STATUSES if s != "excluded" or s in statuses}

    def _change(self, track: DraftTrack, before: Match, after: Match) -> dict[str, Any]:
        override = self._overrides.tracks.get(track.id, TrackOverride())
        return {
            "id": track.id,
            "title": track.title,
            "family": track.family,
            "hand": override.streams is not None,
            "before": self._outcome(before),
            "after": self._outcome(after),
        }

    def _outcome(self, match: Match) -> dict[str, Any]:
        accepted = {(s.videoId, s.startMs, s.endMs) for s in self._decision(match.track_id).accept}
        sources = [
            {"videoId": s.videoId, "startMs": s.startMs, "endMs": s.endMs,
             "confidence": s.confidence,
             "auto": (s.videoId, s.startMs, s.endMs) not in accepted}
            for s in match.streams
        ]  # fmt: skip
        return {"status": self._status_of(match), "sources": sources}

    def _is_blocked(self) -> bool:
        return self._blocked_until is not None and self._clock() < self._blocked_until

    def _youtube_mode(self) -> dict[str, Any]:
        if self._live is None:
            return {"mode": "cached-only"}
        if self._is_blocked():
            return {"mode": "blocked", "until": self._blocked_until}
        return {"mode": "live"}

    def _refresh(self, track_id: str) -> None:
        """Reapplies a track's title and games overrides (keeping its order) and rematches it."""
        updated = self._overridden(self._built[track_id])
        self._replace(
            {track_id: updated.model_copy(update={"order": self._tracks[track_id].order})}
        )
        self._rematch({track_id})

    def _replace(self, updated: dict[str, DraftTrack]) -> None:
        self._tracks.update(updated)
        self._draft = self._draft.model_copy(
            update={"tracks": [updated.get(t.id, t) for t in self._draft.tracks]}
        )

    def _in_order(self, family: str) -> list[DraftTrack]:
        tracks = [t for t in self._draft.tracks if t.family == family]
        return sorted(tracks, key=lambda t: t.order)

    def _override(self, track_id: str) -> TrackOverride:
        return self._overrides.tracks.get(track_id, TrackOverride())

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
        return self._status_of(self._matches[track_id])

    def _status_of(self, match: Match) -> str:
        """The match status, or "excluded" for a track removed since the last build."""
        if self._overrides.tracks.get(match.track_id, TrackOverride()).exclude:
            return "excluded"
        return match.status

    def _track(self, track_id: str) -> DraftTrack:
        if track_id not in self._tracks:
            raise ReviewError([f"{track_id} is not a track in the draft"])
        return self._tracks[track_id]

    def _facts(self, match: Match) -> TrackFacts:
        assert match.facts is not None  # match_tracks always sets them
        return match.facts

    def _decision(self, track_id: str) -> Streams:
        return self._overrides.tracks.get(track_id, TrackOverride()).streams or Streams()

    def _coverage(self, family: str, games: list[Game]) -> dict[str, Any]:
        """A family with per-game gaps and its playlists (entry counts when cached)."""
        playlists = self._games.youtube.playlists
        ids = {g.id for g in games}
        matches = [self._matches[t.id] for t in self._draft.tracks if t.family == family]
        rows = []
        for game in games:
            statuses = [
                s
                for m in matches
                if game.id in self._facts(m).games and (s := self._status_of(m)) != "excluded"
            ]
            rows.append({
                "id": game.id, "shortName": game.shortName, "color": game.color,
                "playlists": sum(game.id in p.games for p in playlists),
                "tracks": len(statuses),
                "review": statuses.count("review"),
                "unmatched": statuses.count("unmatched"),
            })  # fmt: skip
        listed = [
            {"index": i, "playlist": p.playlist, "games": p.games, "first": p.first,
             "last": p.last, "note": p.note,
             "entries": len(self._cached.playlist(p.playlist)) or None}
            for i, p in enumerate(playlists)
            if ids & set(p.games)
        ]  # fmt: skip
        return {**self._family(family, games), "games": rows, "playlists": listed}

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
            "games": track.games,
            "order": track.order,
            "durationMs": track.durationMs,
            "best": round(match.candidates[0].score.value, 3) if match.candidates else None,
            # The first source's confidence (1.0 when picked by hand), for matched tracks.
            "confidence": match.streams[0].confidence if match.streams else None,
            "sources": len(match.streams),
            "decided": override.streams is not None,
            "noted": override.note is not None,
        }


def _identity(candidate: Candidate) -> tuple[str, int, int | None]:
    return (candidate.video_id, candidate.start_ms, candidate.end_ms)


def _entry_match(entry: Entry, matches: dict[str, Match]) -> dict[str, Any] | None:
    """The track that scores the entry highest among those keeping it as a candidate."""
    best: tuple[Match, Scored] | None = None
    for match in matches.values():
        for scored in match.candidates:
            if _identity(scored.candidate) == (entry.id, 0, None) and (
                best is None or scored.score.value > best[1].score.value
            ):
                best = (match, scored)
    if best is None:
        return None
    match, scored = best
    return {
        "trackId": match.track_id,
        "title": match.title,
        "score": round(scored.score.value, 3),
        "verdict": classify(scored.score.value).value,
        "source": any(s.videoId == entry.id for s in match.streams),
    }


def _differs(before: Match, after: Match) -> bool:
    def sources(match: Match) -> list[tuple[str, int, int | None]]:
        return [(s.videoId, s.startMs, s.endMs) for s in match.streams]

    return before.status != after.status or sources(before) != sources(after)


def _ref(track: DraftTrack) -> dict[str, Any]:
    return {"id": track.id, "family": track.family, "title": track.title}


def _iso(at: datetime | None) -> str | None:
    """UTC times as the app writes them, with a Z."""
    return None if at is None else at.isoformat().replace("+00:00", "Z")
