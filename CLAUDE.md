# VGMixr

Android music player for Pokémon (later other) game soundtracks. Kotlin, Jetpack Compose (Material 3), Media3, Room, Hilt. The repo also holds a Python pipeline (`pipeline/`) that builds the track catalog and extracts audio from ROMs the user owns.

## Scope
- Personal app with a single user (the developer), who does all setup by hand once. Prefer fixed configuration over pickers, onboarding or settings screens. In-app warnings and dev-facing debug/cataloging UI are welcome when cheap and useful.
- MVP: browse/filter tracks by game, family, generation, category (battle, route, town, dungeon, event, other); recently played; favorites; playback from remote streams; downloads/caching for offline use.
- Right after MVP: local files from ROM rips become the first source on each track.
- v2: seamless looping with a single global loop-cycle setting. Loop points are in the schema (on `local` sources) from day one.
- v3+: related tracks, recommendations, fan remixes. Do NOT build these yet.
- Work is planned in `docs/roadmap.md`, one step per session. Do only the step you are asked to do.

## Commands
- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest` (single: `--tests "*ClassName*"`)
- Lint/format: `./gradlew lint`, `./gradlew spotlessCheck` / `spotlessApply` (Spotless with ktlint, added in roadmap step 1)
- Pipeline tests: `cd pipeline && uv run pytest` (the Stop hook and CI run this only if `pipeline/` exists)
- All checks live in `scripts/check.sh`, run by both the Stop hook (`.claude/settings.json`, with `--hook`) and CI (`.github/workflows/ci.yml`). Add new checks there only.

## Architecture
- Single module `app`. Packages: `ui/` (Compose screens + ViewModels), `domain/` (models, use cases, interfaces; plain Kotlin, no Android imports), `data/` (Room, catalog import, repositories, stream resolution), `playback/` (Media3).
- MVVM: ViewModels expose immutable `StateFlow<UiState>`. Composables only render state and emit events; no logic in composables.
- DI: Hilt, constructor injection.
- Media3 is used only behind the `PlaybackController` interface in `domain/`. UI and ViewModels never touch Media3 types.
- Stream resolution (NewPipe Extractor) is used only behind the `StreamResolver` interface in `domain/`. No extractor types leak past `data/`.
- Room is the source of truth. Favorites and PlayHistory (trackId, playedAt) are separate tables.

## Catalog and manifest
- The UI renders only from the Room catalog, never from live network lookups. Browsing and filtering must work offline.
- The catalog is `catalog/manifest.json` (tracked in git, written by the pipeline). The app bundles it in assets for the MVP and imports it into Room on app start when its `generatedAt` changes. It is the only boundary between pipeline and app. Full schema and import rules: `docs/manifest.md`. Read it before touching import, validation or the Game/Track models.
- Shared fixtures in `docs/fixtures/` (one valid manifest, one invalid per validation error) are loaded by both pipeline and app tests so the two sides can't drift. Gradle reads `docs/fixtures/` as unit-test resources and `catalog/` as assets.
- Terminology: a **game** is one release (Ruby, Sapphire, Emerald, Platinum). A **family** groups games that share most of their soundtrack (RSE, DPPt). A game with no `family` field is its own single-game family (family id = game id). The manifest has no families array; families are derived.
- Game fields: id, name, shortName, color (`#RRGGBB`), optional generation, platform, optional family. Generation is an optional, unbounded int (Gen 10+ is coming; other franchises have none); never hard-code a maximum. Games in a family share a generation.
- UI colors come from each game's `color` field and are never hard-coded. Derive chip text color from it for contrast. A track's chip is its games' short names, each in its own game's color (e.g. "RSE", or just "R" for a Ruby-only track).
- Track fields: id, family, optional `games` (subset of the family's games; omitted = all), title, category, order, sources, optional `replaces`. Track ids are immutable: favorites and history reference them.
- One track entry per distinct piece of audio. Never duplicate shared tracks per game; use `games` for game-exclusive tracks.
- Import rules (details in `docs/manifest.md`): validate fully before writing, upsert by id, never delete user data, retire tracks and games missing from a new manifest instead of deleting them (un-retire if they reappear), honor `replaces` by re-pointing favorites/history. A failed import keeps the existing catalog and shows a warning.
- Categories: battle, route, town, dungeon, event, other. Unknown values import as `other`.
- Each track has an ordered `sources` list of `local` files, then `stream` candidates. The player tries local files (skipping missing ones), then a cached download of a stream source, then streams, and marks sources that exist but fail to play as broken. The cache is runtime state, never a manifest source. Unknown source types are a validation error; adding one bumps `schemaVersion`.
- Runtime source state lives in Room, keyed by source identity, never by list index: local by `path`; stream broken flag by `videoId`; stream cache by `(videoId, startMs, endMs)`.
- Local files live in a fixed app-specific folder (`files/music/`), copied there by hand.
- A stream source stores a video ID, never a URL (URLs expire). Resolve to a URL at play time via `StreamResolver`. Optional `startMs`/`endMs` for tracks inside whole-album videos.
- Loop points (`loop`) are optional and only on `local` sources, since they describe one exact file. Loop cycle count is a global app setting.
- Prefetch the next queued track; auto-cache favorites and recently played.
- Deferred, do not build yet: an optional `franchise` field on games for multi-franchise grouping (see `docs/manifest.md`).

## Testing (TDD)
- Write the failing test first. Run it, confirm it fails for the right reason, then implement. Commit when green.
- Never modify or delete a test to make it pass. If a test seems wrong, stop and ask.
- Stack: JUnit, MockK, Turbine, kotlinx-coroutines-test, Robolectric (Room DAO tests on the JVM). Inject dispatchers and clock. Prefer fakes for `PlaybackController`, `StreamResolver` and repositories.
- Domain and ViewModel logic require unit tests. Compose UI tests only for critical flows.
- Code paths unused until later versions (e.g. loop parsing) still get tests.
- Iterate with the single relevant test; run the full suite before finishing.

## Conventions
- Concise, idiomatic Kotlin. Immutable data classes, sealed types for events/errors, no `!!`.
- Pre-approved dependencies: Hilt, hilt-navigation-compose, Room, Media3, kotlinx-serialization (with its Gradle plugin), Navigation Compose, NewPipe Extractor (via the JitPack repository), KSP, Spotless with ktlint, MockK, Turbine, kotlinx-coroutines-test, Robolectric. Ask before adding anything else; prefer stdlib and AndroidX.
- Never put API keys or secrets in code.
- Pipeline: Python 3, type hints, pytest, uv for dependencies and running tools. Intermediate output goes to `pipeline/out/` (gitignored). Never commit ROMs or extracted audio.

## Workflow
- Phase 0 setup commits go directly to `main`. From roadmap step 1 on, each step gets its own branch, merged into `main` when green.
- Small commits on green with descriptive messages. One feature per session; plan first for changes spanning layers.
- When compacting, preserve the list of modified files and the test commands.

## Gotchas (add as discovered)
- The extractor breaks when YouTube changes. Cached downloads must keep playing without it.
- Lossy codecs (Opus/MP3) can leave gaps at loop seams. Use FLAC or verify before building v2 looping.
