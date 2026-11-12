---
name: plan-feature
description: Plan a multi-session VGMixr feature (e.g. ROM extraction, v2 looping, a roadmap step or candidate too big for one session) into a per-feature doc in docs/features/ with numbered, checkable sub-steps, then plan the next unchecked sub-step in detail. Use whenever the user wants to plan, scope, break down, design, or continue/resume a feature or roadmap step, asks "what's next for <feature>", or wants to turn a roadmap candidate into steps, even if they don't say "plan".
---

# Plan a feature

VGMixr's work happens one step per session (`docs/roadmap.md`). Some features are too big for one roadmap entry: step 20 (ROM extraction) spans three consoles, a matching pipeline, the review tool and app playback. This skill gives such a feature its own tracking doc, `docs/features/<slug>.md`, so every later session can pick up where the last stopped by reading one file.

Each run does one of two things:
- **New feature** (no doc yet): interview, research, write the doc.
- **Continue** (doc exists): reconcile the doc with reality, then plan the next unchecked sub-step.

Planning only. Don't write app or pipeline code in this skill: the user writes the code and Claude plans and reviews (a step session does the work later).

## Ground rules from the project

Read `CLAUDE.md` and keep to it; these matter most when planning:
- One sub-step = one session = one branch = one PR, ending green (`scripts/check.sh`). Size steps so that is true. A step that "also does X" is two steps.
- Test-first. Each build step names the failing tests that come first and what they assert. Domain and ViewModel logic need unit tests; pipeline code needs pytest.
- Respect the boundaries: the manifest is the only pipeline/app boundary (schema changes bump `schemaVersion`, touch `docs/manifest.md`, the fixture spec and both validators); Media3 only behind `PlaybackController`; NewPipe only behind `StreamResolver`; Room is the source of truth, runtime source state keyed by source identity.
- Don't build what's deferred (v3 discovery, `franchise`), and don't pull later roadmap steps into this feature. Note the dependency instead.
- New dependencies or tools (ffmpeg, numpy, vgmstream, ...) need the user's approval: list them as open questions, never assume them.
- Never commit ROMs or extracted audio; outputs go under `pipeline/out/`.
- Prefer fixed configuration over pickers or settings (single-user app); dev-facing review-tool UI is fine.
- UI work is tested on the phone (mockups first, then screenshot loop); say so in UI steps.

## New feature

1. **Gather context.** Read the feature's roadmap entry and every roadmap line or candidate that mentions it (grep for its key words), plus `docs/manifest.md`, and any doc it touches (`docs/adding-games.md`, `docs/broken.md`). Look at the code it will touch, enough to name real files and classes. Don't re-ask what the roadmap already decided.
2. **Map the full effects.** Before interviewing, walk every area below and write down, for each, what the feature changes, what it could break, and what you can't decide alone. A feature named after one layer ("ROM extraction" sounds like pipeline work) usually reaches all of them; the plan fails on the area nobody thought about. Mark an area "untouched" only after checking, and say why.
    - **Pipeline**: new commands, inputs, caches under `pipeline/out/`, the review tool, `pipeline/data/` files, rebuild behaviour (`--cached-only`, ids in `ids.json`).
    - **Manifest and schema**: new or changed fields, `schemaVersion`, validation errors, fixtures, `docs/manifest.md`, import rules (retire, `replaces`), what happens to installs on the old manifest.
    - **Data/storage (app)**: Room tables and migrations, runtime source state keyed by identity, files on the phone (`files/music/`, the media cache), disk size, how files get there, what survives reinstall and backup/restore (`vgmixr_pipeline.backup`).
    - **Playback**: source order (local, cached download, stream), the queue and queued Media3 items, broken marking, offline behaviour, gapless/looping effects, the media notification.
    - **Network**: what still streams or downloads, YouTube bot checks and request pacing, offline sync (`domain/offline/`) and the 200-track cache, data use.
    - **UI**: which screens change (rows, now playing, queue, search, playlists, the review tool), new states (missing file, rip vs stream, greyed tracks), and how the user can tell what is playing.
    - **UX**: the user's whole workflow end to end, including manual steps (dumping a ROM, copying files over adb), what feels different when listening, failure cases and how the user finds out, and whether it fits the Shuttle look and Spotify-style behaviour.
    - **Performance and size**: APK and asset size, import time, phone storage, pipeline run time.
    - **Testing and checks**: fixtures, fakes, `scripts/check.sh` and CI additions, what can only be checked by ear on the phone.
    - **Docs and other features**: CLAUDE.md (commands, gotchas), `docs/adding-games.md`, and roadmap steps or candidates this enables, blocks or changes (for ROM extraction: local file playback, v2 looping, stream looping, full-album slices).
    Put the map in the doc (the "Effects" section of the template) so later sessions see it.
3. **Interview.** Ask what the map couldn't settle, grouped by area, plus what changes the plan overall: order of work, what "done" looks like for the user, hardware/files the user has (ROM dumps, which games), tolerance for new tools and disk use. Use AskUserQuestion with a recommended option first; run several rounds if the map has many open points, but never ask what the code or roadmap already answers. Update the map with the answers.
4. **Split unknowns from work.** Anything the plan can't decide without trying (a file format, whether a library handles a case, how well fingerprinting matches) becomes a **spike** step: a question, how to answer it, what result decides what. Spikes come before the build steps that depend on them, and their findings get written back into the doc. Build steps after an open spike may stay rough until it lands.
5. **Write the doc** from the template below. Number sub-steps after the roadmap step (`20a`, `20b`, ...; nest `20a.1` only if needed). Order them so each one leaves something usable or verified, and the earliest steps reduce the biggest risks.
6. **Link it from the roadmap.** Shorten the roadmap entry to its one-paragraph goal plus `Plan: docs/features/<slug>.md`, keeping decided facts that live nowhere else (move them into the doc rather than deleting them).
7. **Plan the first step** as in "Next step" below.
8. Commit the doc and roadmap edit on a branch for the feature's first step (roadmap edits go on the current step's branch, not a separate PR). Ask before committing.

## Continue

1. Read the feature doc, then `git log --oneline main -20` and the branches, to see which steps merged since the doc was last updated.
2. Reconcile: check off merged steps (on their own branch as the last commit, so `main` never shows a merged step unchecked; if one merged unchecked, check it now), copy spike findings and decisions made during the step into the doc as "Decided:" lines, and re-plan later steps the findings changed, and update the Effects map where a finding changed an area. Tell the user what changed and why.
3. Plan the next unchecked step.

## Next step

Write the next step's detail under its entry in the doc (not in chat only, so the step session can read it):
- **Goal** and **done when** (observable: a test, a command's output, something heard on the phone).
- **Tests first**: test names/files and what each asserts.
- **Changes** by area (pipeline, manifest/fixtures, data, domain, playback, ui, docs, checks), naming real files. Check the step against the Effects map: list knock-on effects in other areas (a migration, a greyed-row state, a check.sh entry) even when small.
- **Commands** to run and how to verify by hand.
- **Out of scope** for this step.
- **Open questions** for the user, if any; ask them now rather than leaving them for the step.

Then end the session with a short summary in chat: what the doc now says, the next step, and the questions still open.

## Doc template

```markdown
# <Feature name>

Roadmap step <N>. <One-paragraph goal: what the user gets, and why.>

## Status
<One line: the next step, and what blocks it, if anything.>

## Decided
- <Decision. Why.> (step <id>)

## Effects
- **Pipeline**: <change / risk / open point, or "untouched: why">
- **Manifest and schema**: ...
- **Data/storage**: ...
- **Playback**: ...
- **Network**: ...
- **UI**: ...
- **UX**: ...
- **Performance and size**: ...
- **Testing and checks**: ...
- **Docs and other features**: ...

## Open questions
- <Question.> Blocks <step id>.

## Steps
- [ ] <N>a. **Spike: <question>.** How: <...>. Decides: <...>.
- [ ] <N>b. <Build step, one session.> Depends on <N>a.
    - Goal / done when / tests first / changes / commands / out of scope  (filled in for the next step only)

## Not in this feature
- <Related work left to other steps or candidates, with where it lives.>
```

Keep the doc in the roadmap's voice: plain prose, decisions with reasons, no filler. A finished step's detail can shrink to a line or two plus its "Decided" entries: the PR holds the rest.
