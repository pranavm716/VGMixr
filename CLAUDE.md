# VGMixr

Android music player for Pokémon (later other) game soundtracks. Kotlin, Jetpack Compose (Material 3), Media3, Room, Hilt. The repo also holds a Python pipeline (`pipeline/`) that builds the track catalog and extracts audio from ROMs the user owns.

## Scope
- Personal app with a single user (the developer), who does all setup by hand once. Prefer fixed configuration over pickers, onboarding or settings screens. In-app warnings and dev-facing debug/cataloging UI are welcome when cheap and useful.
- MVP (done, roadmap step 14): browse/filter tracks by game, family, generation; search; play history (for search ranking and caching); favorites; playback from remote streams; downloads/caching for offline use.
- Right after MVP: local files from ROM rips become the first source on each track.
- v2: seamless looping with a single global loop-cycle setting. Loop points are in the schema (on `local` sources) from day one.
- After downloads/cache: user-added YouTube tracks (mainly fan remixes), added by pasting a link (see `docs/roadmap.md`).
- v3+: related tracks, recommendations. Do NOT build these yet.
- Work is planned in `docs/roadmap.md`, one step per session. Do only the step you are asked to do.

## Commands
- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest` (single: `--tests "*ClassName*"`)
- Lint/format: `./gradlew lint`, `./gradlew spotlessCheck` / `spotlessApply` (Spotless with ktlint, added in roadmap step 1)
- Pipeline tests: `cd pipeline && uv run pytest`; lint/format/types: `uv run ruff check .`, `uv run ruff format .`, `uv run mypy .` (the Stop hook and CI run these only if `pipeline/` exists)
- Build the catalog (Bulbapedia track lists + YouTube + `pipeline/data/`): `cd pipeline && uv run python -m vgmixr_pipeline.catalog`. Writes `pipeline/out/draft.json`, `family-report.md`, `match-report.md` and `candidates.json`, then `catalog/manifest.json` if it validates, and then adds the manifest's new tracks to `pipeline/data/ids.json` (commit the two together). Responses are cached in `pipeline/out/bulbapedia/` and `pipeline/out/youtube/`; `--refresh` / `--refresh-youtube` refetch them, `--no-search` matches from playlists and albums only, `--cached-only` makes no YouTube requests.
- Review tool (decide streams by hand, find and add playlists, drag tracks into order, rename tracks, write notes, fix the sources the app reported broken in its Broken tab; writes `pipeline/data/`): `cd pipeline && uv run python -m vgmixr_pipeline.catalog.review` then open http://127.0.0.1:8765 (`--cached-only` never asks YouTube). Needs `pipeline/out/draft.json` from a catalog build.
- Rewrite the data files in their canonical format after hand edits: `cd pipeline && uv run python -m vgmixr_pipeline.catalog.jsonfmt` (a test fails otherwise)
- Regenerate the invalid manifest fixtures after editing `pipeline/src/vgmixr_pipeline/fixtures/spec.py`: `cd pipeline && uv run python -m vgmixr_pipeline.fixtures.generate` (`--check` reports stale files without writing; check.sh runs it)
- Family covers: put hand-cropped 16:9 images named `<family id>.<any image format>` in `pipeline/covers-in/` (gitignored), then `cd pipeline && uv run python -m vgmixr_pipeline.covers` writes `catalog/covers/<family>.webp` (1280x720) and lists families still without one
- Broken sources the app reported: `cd pipeline && uv run python -m vgmixr_pipeline.broken` pulls the phone's `broken.json` over adb into `pipeline/out/broken.json` and prints them per family (format and marking rules: `docs/broken.md`). The review tool's Broken tab reads that file and re-checks each video the way the app sees it (yt-dlp's `visionos` client, NewPipe's), cached in `pipeline/out/youtube/app-check/`. The playback log sits next to it: `adb shell cat /sdcard/Android/data/io.github.pranavm716.vgmixr/files/playback.log`.
- Back up user data (playlists, favorites, play history): `cd pipeline && uv run python -m vgmixr_pipeline.backup pull` stops VGMixr (refusing while it plays) and copies `vgmixr.db` with its `-wal` and `-shm` into `backups/<local time>/` at the repo root (gitignored); `... backup push <folder>` pulls first as the rollback point, asks (`--yes` skips), writes the folder back and deletes a `-wal`/`-shm` it lacks. Needs a debug build on the phone; a backup restores into the same or a newer app version only.
- All checks live in `scripts/check.sh`, run by both the Stop hook (`.claude/settings.json`, with `--hook`) and CI (`.github/workflows/ci.yml`). Add new checks there only, as a named check (`scripts/check.sh NAME` runs one), plus a matching CI step.

## Architecture
- Single module `app`. Packages: `ui/` (Compose screens + ViewModels), `domain/` (models, use cases, interfaces; plain Kotlin, no Android imports), `data/` (Room, catalog import, repositories, stream resolution), `playback/` (Media3).
- MVVM: ViewModels expose immutable `StateFlow<UiState>`. Composables only render state and emit events; no logic in composables.
- DI: Hilt, constructor injection.
- Media3 is used only behind the `PlaybackController` interface in `domain/`. UI and ViewModels never touch Media3 types.
- Stream resolution (NewPipe Extractor) is used only behind the `StreamResolver` interface in `domain/`. No extractor types leak past `data/`.
- Room is the source of truth. Favorites is a built-in playlist (`Playlist`, `PlaylistEntry`); PlayHistory (trackId, playedAt) is its own table, written only by the playback service once a track has played 10 s (`PlayCounter`). Tracks the user adds from YouTube live in `UserTrack`, with `yt:<videoId>` ids that can't collide with catalog ids, and an optional `remixOf` catalog track id. All of this is user data in Room; the manifest stays pipeline-only.

## Catalog and manifest
- The UI renders only from the Room catalog, never from live network lookups. Browsing and filtering must work offline.
- The catalog is `catalog/manifest.json` (tracked in git, written by the pipeline). The app bundles it in assets for the MVP and imports it into Room on app start when its `generatedAt` changes. It is the only boundary between pipeline and app. Full schema and import rules: `docs/manifest.md`. Read it before touching import, validation or the Game/Track models.
- Shared fixtures in `docs/fixtures/` (one valid manifest, one invalid per validation error) are loaded by both pipeline and app tests so the two sides can't drift. Gradle reads `docs/fixtures/` as unit-test resources and `catalog/` as assets.
- Terminology: a **game** is one release (Ruby, Sapphire, Emerald, Platinum). A **family** groups games that share most of their soundtrack (RSE, DPPt). A game with no `family` field is its own single-game family (family id = game id). The manifest has no families array; families are derived.
- A family's cover is `catalog/covers/<family id>.webp`, bundled with the manifest in assets and found by file name, not by a manifest field. A family without one shows a fallback tile. Box and album art stay out of the manifest.
- Game fields: id, name, shortName, color (`#RRGGBB`), optional generation, platform, optional family. Generation is an optional, unbounded int (Gen 10+ is coming; other franchises have none); never hard-code a maximum. Games in a family share a generation.
- UI colors come from each game's `color` field and are never hard-coded. Derive chip text color from it for contrast. A track's chip is its games' short names, each in its own game's color (e.g. "RSE", or just "R" for a Ruby-only track).
- Track fields: id, family, optional `games` (subset of the family's games; omitted = all), title, order, sources, optional `replaces`. A track id is frozen in `pipeline/data/ids.json` (source key -> catalog id): the first build that ships a track adds it as `<family>-<slug of its final title>`, and from then on a rename changes only `title`. Ids are never reused (a new track whose slug is already in the file fails the build) and entries are never removed. A vanished key (listed in `family-report.md`) means an upstream title changed; moving its id onto the new key is a hand edit. Overrides and the review tool key tracks by their source key (the slug of the source's title) instead. Favorites and history reference catalog ids.
- One track entry per distinct piece of audio. Never duplicate shared tracks per game; use `games` for game-exclusive tracks.
- Tracks in one family that share a name are told apart by their versions in parentheses, joined with "&": "Opening Movie (Ruby & Sapphire)" and "Opening Movie (Emerald)", "Title Screen (Black & White)" and "Title Screen (Black 2 & White 2)". Both sides get the qualifier.
- Import rules (details in `docs/manifest.md`): validate fully before writing, upsert by id, never delete user data, retire tracks and games missing from a new manifest instead of deleting them (un-retire if they reappear), honor `replaces` by re-pointing playlist entries (favorites included), history and `remixOf`. A failed import keeps the existing catalog and shows a warning.
- Each track has an ordered `sources` list of `local` files, then `stream` candidates. The player tries local files (skipping missing ones), then a cached download of a stream source, then streams, and marks sources that exist but fail to play as broken. The cache is runtime state, never a manifest source. Unknown source types are a validation error; adding one bumps `schemaVersion`.
- Runtime source state lives in Room, keyed by source identity, never by list index: local by `path`; stream broken flag by `videoId`; stream downloads by `videoId` (a download is always a video's whole audio; slices, an album's included, play from it clipped). A stream video is marked broken only for no playable audio or unavailable, never for network trouble or a 403 (`docs/broken.md`).
- Local files live in a fixed app-specific folder (`files/music/`), copied there by hand. `local` sources are for ROM rips (with loop points); audio from YouTube stays a `stream` source, downloaded by the app.
- A stream source stores a video ID, never a URL (URLs expire). Resolve to a URL at play time via `StreamResolver`. Optional `startMs`/`endMs` for tracks inside whole-album videos.
- Loop points (`loop`) are optional and only on `local` sources, since they describe one exact file. Loop cycle count is a global app setting.
- Offline (roadmap step 13): **downloaded** = every track in a playlist or Remixes, kept; **cached** = the last 200 played and the next queued track, evicted. The rules are plain Kotlin in `domain/offline/` (`OfflineSync` plans, Room's `offline_videos` records); Media3's download manager fills one store (`files/media-cache/`, keyed by video id) that playback only reads. Offline, a track plays when the video playback would pick is on the phone; the rest are dimmed and skipped.
- Deferred, do not build yet: an optional `franchise` field on games for multi-franchise grouping (see `docs/manifest.md`).
- Adding a game or family (or a spin-off, or another franchise): follow the checklist in `docs/adding-games.md`, which lists the data files and every place in the code that knows about Pokémon.

## Testing (TDD)
- Write the failing test first. Run it, confirm it fails for the right reason, then implement. Commit when green.
- Never modify or delete a test to make it pass. If a test seems wrong, stop and ask.
- Stack: JUnit, MockK, Turbine, kotlinx-coroutines-test, Robolectric (Room DAO tests on the JVM). Inject dispatchers and clock. Prefer fakes for `PlaybackController`, `StreamResolver` and repositories.
- Domain and ViewModel logic require unit tests. Compose UI tests only for critical flows.
- Code paths unused until later versions (e.g. loop parsing) still get tests.
- Iterate with the single relevant test; run the full suite before finishing.

## Conventions
- Concise, idiomatic Kotlin. Immutable data classes, sealed types for events/errors, no `!!`.
- Pre-approved dependencies: Hilt, hilt-navigation-compose, Room, Media3, kotlinx-serialization (with its Gradle plugin), Navigation Compose, NewPipe Extractor (via the JitPack repository) with `desugar_jdk_libs_nio` (core library desugaring, which NewPipe needs below API 33), OkHttp (NewPipe's HTTP client), KSP, Spotless with ktlint, MockK, Turbine, kotlinx-coroutines-test, Robolectric; pipeline: pydantic, mwparserfromhell (Bulbapedia wikitext), yt-dlp (YouTube search and playlist metadata, no API key), Pillow (family covers), with pytest, ruff and mypy as dev dependencies. Ask before adding anything else; prefer stdlib and AndroidX.
- Never put API keys or secrets in code.
- Pipeline: Python 3, type hints, pytest, uv for dependencies and running tools. Intermediate output goes to `pipeline/out/` (gitignored). Never commit ROMs or extracted audio.

## Workflow
- Phase 0 setup commits go directly to `main`. From roadmap step 1 on, each step gets its own branch, merged into `main` when green.
- Each step ends with a PR whose description details the changes, grouped by area (dependencies, wiring, tests, checks, roadmap edits) with the reasons behind non-obvious decisions. Describe only what changed, not how the work was done.
- Small commits on green with descriptive messages. One feature per session; plan first for changes spanning layers.
- When compacting, preserve the list of modified files and the test commands.

## Gotchas (add as discovered)
- YouTube's media servers slow an open-ended request to about playback speed (35 KB/s), but serve bounded byte ranges at full speed (MB/s): downloads read through `ChunkedDataSource` (4 MB ranges).
- Only downloads write the media store. If playback wrote too, the player (holding a stream open while it buffers) and a download of the same video would wait on each other forever, stalling every download.
- The extractor breaks when YouTube changes. Cached downloads must keep playing without it.
- Lossy codecs (Opus/MP3) can leave gaps at loop seams. Use FLAC or verify before building v2 looping.
- YouTube answers a burst of yt-dlp requests with "Sign in to confirm you're not a bot". Cached responses keep builds offline (`--cached-only`); space out fresh runs. The block lifts after a while. `--cookies-from-browser` fails on Windows while Edge or Chrome is running (it locks its cookie database).
- A title override written by hand can drop a track's auto-matches (renaming in the review tool pins the track's videos first, so it can't). Cached searches are keyed by the query, which includes the title, so after a rename the old search results are no longer found and a track matched only by search becomes unmatched (it leaves the manifest). Hand-accepted videos and playlist matches survive a rename. After renaming, Rebuild and check the family's matched count; accept the old videos by hand or search again.
- YouTube bot-checks anonymous player requests ("Sign in to confirm that you're not a bot", `LOGIN_REQUIRED`) from this home network's IPv6 range, while its IPv4 address still works; Android prefers IPv6, desktop Java IPv4. NewPipe's downloader (OkHttp) therefore connects over IPv4 when the host has an IPv4 address (`Ipv4OnlyDns`). Stream URLs themselves play over IPv6. To check from the PC: `./gradlew testDebugUnitTest --tests "*LiveTest*" -Pvgmixr.live`.
- Android drops toasts from an app in the background unless it may post notifications, so sharing a video to VGMixr ends silently without `POST_NOTIFICATIONS`. The app never asks for it: grant it once by hand (app info > Notifications, or `adb shell pm grant io.github.pranavm716.vgmixr android.permission.POST_NOTIFICATIONS`), again after an uninstall.
- Queued Media3 items keep the sources they were built with; the service never rebuilds them. Anything that must change a queued track (it left the catalog, a remix was deleted) has to replace or remove its item (`leftCatalog`, `PlayQueue.removeTrack`).
- Installing a new build kills the app and its queue, so a manifest update can't retire a track mid-queue on the phone; deleting a remix can. Retired tracks live in `Catalog.retiredTracks`, reached only through `trackOrRetired` (playlists and the player), never `track`.
- Toggle airplane mode from the PC for offline checks: `adb shell cmd connectivity airplane-mode enable|disable`. Media keys (`input keyevent KEYCODE_MEDIA_NEXT`) skip without touching the screen.
- adb isn't on PATH: use `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`. In Git Bash, `/sdcard/...` arguments get rewritten to Windows paths (`C:/Program Files/Git/sdcard/...`); prefix the command with `MSYS_NO_PATHCONV=1`. `adb exec-out cat` exits 0 for a missing file; `adb pull` fails properly. `exec-out` returns `run-as` errors as output with exit code 0. `adb exec-in` works only with the device command as one string (`"run-as <pkg> sh -c 'cat > f'"`; separate arguments write nothing, exit 0), and returns before the phone has finished writing.
