# Report wrong source

Roadmap step 15e. A one-tap button in now playing marks the playing track's video as wrong (another track, a remix, the wrong version, a bad cut) while listening, and the review tool lists those reports beside the broken marks, so a replacement is one click after listening. Wrong streams are found by ear on the phone and fixed by hand today; this closes that loop with the least code: one file on the phone, one pull, one tab.

## Status
Done: 15e-a and 15e-b on one branch (the user chose one PR).

## Decided
- One tap, no note, no kinds. Tapping again on a reported track takes the report back (a mis-tap fix). The button is accent-tinted while the playing track is reported, as shuffle is when on. (planning)
- Playback never changes: a reported video keeps playing until a rebuild replaces it. A report is a note for the pipeline, unlike a broken mark. (planning)
- It takes the far-right slot of now playing's controls, replacing the placeholder repeat icon; step 21 finds it another place when looping is built. (planning)
- A report is per track and video: `trackId` plus the `videoId` playback would pick (its first stream source not marked broken, as `Failover.kt` chooses), with its `startMs`/`endMs`, the position when tapped, the time and the catalog's `generatedAt`. The app computes the video in domain from the catalog and the broken marks; nothing new crosses the `PlaybackController` boundary. (planning)
- No snackbar on a tap: the flag's tint says it, and less code. A report can only be made for a track with a stream it would play. (15e-a)
- Reports live in a file, not Room: `reports.json` in the app's external files folder next to `broken.json`, written whole like `BrokenMarksMirror` does, and read back on start. They are dev diagnostics, not user data: no migration, not in backups. (planning)
- A report clears itself on the phone when an imported catalog no longer picks that video for that track (the fix shipped). Nothing else clears it but a second tap. (planning)
- Review tool: reports show in the Broken tab as their own group, sorted into "Needs a fix" / "Fixed in the draft" as broken marks are. Each candidate on the opened track gets "Replace with this": reject the reported video, accept the candidate, run the app check. No automatic search or pick; the user listens first. (planning)

- The flag is the outline glyph at 33 dp (sized by eye against shuffle) and the filled glyph in the accent while reported. (15e-a)
- Reports reach the review tool as Broken-tab reports with reason `wrong` and a "heard at m:ss" detail, so the tab's grouping, track lookup and Open track work unchanged. "Replace with this" is client-side: one `streams` call (accept the candidate first, reject the reported video), then the app check; no new endpoint. A report is fixed once its own track stops using the video, whatever other tracks do. (15e-b)
- Unit tests get a 2 GB heap: the suite in one JVM ran out of the 512 MB default and hung. (15e-a)

## Effects
- **Pipeline**: `vgmixr_pipeline.broken` also pulls `reports.json` into `pipeline/out/reports.json` (a missing file is no reports, not an error). The review service gains a reports reader, the grouping, and one swap endpoint built on the existing `set_streams`. No `pipeline/data/` format change; the swap writes ordinary review decisions.
- **Manifest and schema**: untouched: reports never enter the manifest.
- **Data/storage**: one small JSON file on the phone; lost on uninstall, which is fine for a dev queue. No Room table or migration.
- **Playback**: untouched by design (never skip). The picked video is computed outside the player, so a track playing a cached download reports the same video the download is of.
- **Network**: untouched in the app. The tool's swap runs one app check (paced, as Check is today).
- **UI**: now playing's far-right slot becomes a flag button with two states (accent when reported). No snackbar: the tint is the feedback. Phone screenshot loop for the icon size and tint.
- **UX**: hear it, tap it, keep listening. Later: `uv run python -m vgmixr_pipeline.broken`, open the review tool's Broken tab, open the track, listen to candidates, Replace with this, Rebuild, install. The report disappears on the phone once the new catalog imports.
- **Performance and size**: negligible.
- **Testing and checks**: a shared fixture `docs/fixtures/reports.json` that the app writer and the pipeline parser are both tested against, as `broken.json` is. No new check.sh entry.
- **Docs and other features**: `docs/broken.md` gains a reports section (or the file is renamed later if it grows); CLAUDE.md's broken-sources command line mentions reports. Replaces the roadmap candidate "In-app flags" (free-text notes dropped). Step 21 must move this button or share the slot.

## Open questions
- None.

## Steps
- [x] 15e-a. **App: the report button and `reports.json`.**
    - Goal: tapping the button in now playing records or removes a report for the playing track, mirrored to `reports.json`.
    - Done when: on the phone, tap on a playing track, then `adb shell cat /sdcard/Android/data/io.github.pranavm716.vgmixr/files/reports.json` shows it with the right `videoId`; a second tap removes it; it survives an app restart.
    - Tests first:
        - `WrongSourceReportsTest` (domain): `pickedVideo` returns the first stream source not in the broken set, with its slice; null for a track with no playable stream. Toggle adds, then removes; clearing against a new catalog drops reports whose track now picks another video, or left the catalog, and keeps the rest.
        - `ReportsFileTest` (data, JVM): writing the shared fixture's reports gives the fixture's text; reading it back round-trips; a missing or invalid file reads as no reports.
        - `NowPlayingViewModelTest`: `onReport` toggles the report for the playing track; `uiState.reported` follows the playing track; no-op with nothing playing.
    - Changes:
        - domain: `domain/report/WrongSourceReport.kt` (data class, `WrongSourceReports` interface with `reports: Flow`, `toggle`, `retain(catalog, broken)`), a pure `pickedVideo(track, broken)` beside `Failover.kt`'s logic (reuse it rather than copy).
        - data: a file-backed implementation in `data/diagnostics/` (reads on start, writes whole via temp file + atomic move, as `BrokenMarksMirror`); call `retain` after a catalog import.
        - di: bind it in the module that provides `BrokenMarksMirror`, with the same external files folder.
        - ui: `NowPlaying.kt` replaces the repeat icon in `ControlSlot(OUTER_OFFSET)` with a flag icon (Material Symbols `flag`, new drawable), tinted like shuffle; `NowPlayingViewModel.onReport`.
        - docs: `docs/fixtures/reports.json`, the `reports.json` format in `docs/broken.md`.
    - Commands: `./gradlew testDebugUnitTest --tests "*Report*"`, then the full suite, `scripts/check.sh`; install and test on the phone.
    - Out of scope: the pipeline pull and the review tool (15e-b).
- [x] 15e-b. **Pipeline: pull reports and swap in the review tool.** Depends on 15e-a.
    - `vgmixr_pipeline.broken` pulls and prints reports per family too (pytest against the shared fixture). The Broken tab lists reports as their own group, Needs a fix / Fixed in the draft by whether the draft track still picks the reported video, "older catalog" tag as for marks. Open track marks the reported video and the position heard. "Replace with this" on each candidate: reject reported + accept candidate + app check, one service call (pytest on the service, as the broken tests do).

## Not in this feature
- Notes or reasons on a report: dropped from the "In-app flags" candidate for less code.
- Skipping a reported video in playback.
- Reporting a local file or a remix: local sources wait for step 20; remixes are the user's own picks.
- Repeat/loop: step 21.
