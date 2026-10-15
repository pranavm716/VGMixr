# VGMixr roadmap

One step per Claude Code session. Run `/clear` between steps. Phase 0 commits go directly to `main`. From step 1 on, each step gets its own branch, is test-first, ends green, and is merged into `main`. Check a step off when it is merged.

## Phase 0: manual setup (not a Claude Code session)
- [x] Create the Android project (Empty Activity, Compose, Kotlin), run it once, commit.
- [x] Add `.claude/` files, CI workflow, `.gitattributes`, `scripts/check-forbidden-files.sh`. The Stop hook and CI start with the checks that exist today (`assembleDebug`, `testDebugUnitTest`, `lint`); `pytest` runs only if `pipeline/` exists. Confirm those Gradle commands work.

## Phase 1: foundation
1. [x] Skeleton and tooling: version catalog dependencies (incl. KSP, kotlinx-serialization plugin, JitPack repository), Hilt wiring, Spotless with ktlint, a smoke test. No empty placeholder packages: `domain/` and `data/` are created in step 3, `playback/` in step 8 (`ui/` already exists). Add `spotlessCheck` to the Stop hook and CI. Done when build, test and lint are green, and the Stop hook and CI have run.
2. [ ] Manifest fixtures: `docs/fixtures/` with a valid manifest (including a `local` source with `loop`) and one invalid manifest per validation error, plus a README. Review gate: the user reviews the fixtures before step 3.
3. [ ] Parse and validate (Kotlin): models, validator, parser, test-first against the fixtures. Wire `docs/fixtures/` into Gradle as unit-test resources.
4. [ ] Pipeline validator (Python): same fixtures, pytest. Set up `pipeline/pyproject.toml` with uv (ruff, mypy, pytest as dev dependencies; mypy in strict mode). Add `uv run ruff format --check`, `uv run ruff check` and `uv run mypy .` to `scripts/check.sh` alongside pytest.
5. [ ] Room and catalog import: entities, DAOs, upsert, retire and un-retire, `replaces` rules (including favorite merge), stored game positions, import on app start when `generatedAt` changes, failed import keeps the old catalog. DAO tests use Robolectric.
6. [ ] Seed catalog and repository: hand-written `catalog/manifest.json` (about 10 tracks, 3 families, including game-exclusive tracks) bundled in assets (wire `catalog/` into Gradle as an assets dir), plus repository queries for game, family, generation and category filters. The user supplies a handful of real video IDs.

## Phase 2: core app
7. [ ] Browse and filter UI: ViewModel and UiState tests against a fake repository; game chips in per-game colors with contrast text; filter sheet; empty states and failed-import warning. No playback yet.
8. [ ] Playback core: `PlaybackController`, Media3 implementation, queue, background playback and notification, tested with fakes. Plays a local test file.
9. [ ] Now-playing and mini player UI.
10. [ ] Stream resolution: `StreamResolver` with a NewPipe implementation and a fake for tests; first-usable-source logic (skip missing local files, then cache, then streams); broken marking; `startMs`/`endMs` slicing. Now-playing shows which source is playing and why earlier ones were skipped. Remote tracks play end to end. Highest-risk step.
11. [ ] Favorites: table, toggle, favorites view.
12. [ ] Recently played: history writes with an injected clock, recents section.
13. [ ] Downloads and cache: offline playback, auto-cache favorites and recents, prefetch the next track. Cache keyed by `(videoId, startMs, endMs)`.
14. [ ] Robustness pass: error and offline states, retired tracks and games shown as unavailable, `/code-review`, CLAUDE.md update.
15. [ ] Visual polish: theme, navigation, icon, using the screenshot loop.

## Phase 3: real catalog (independent of the app; can run in a separate worktree alongside Phase 2)
16. [ ] Catalog builder, part A: Bulbapedia track lists into a draft manifest.
17. [ ] Catalog builder, part B: YouTube candidate matching and a review tool for low-confidence matches.

## After the MVP
18. [ ] ROM extraction: 3DS first, then DS, then GBA. Local files (with loop points where known) become the first source on each track; copied by hand into the app's `files/music/` folder.
19. [ ] v2 looping: seamless playback using `local` source loop points, global loop-cycle setting.

## Candidates (not scheduled)
- Seamless looping for `stream` sources, after v2 proves local looping. Loop points are per video, keyed by `(videoId, startMs, endMs)`, because uploads differ from ROM rips in offset, sample rate and encoding; the pipeline could derive them by aligning a video's audio against the local rip. Loops play only from the cached download (a live stream would rebuffer on every seek back to `introEnd`); verify that lossy Opus/AAC seams are gapless first. Allowing `loop` on `stream` sources removes the `stream-loop-present` error, so it bumps `schemaVersion`.
