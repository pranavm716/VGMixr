# Broken sources

The app marks a `stream` source's video broken when it is dead for the app, keeps the marks in Room (table `broken_streams`, keyed by `videoId`) and mirrors them to `broken.json` for the PC. The review tool's Broken tab (roadmap step 10b, [below](#the-review-tools-broken-tab)) reads the pulled copy.

## What marks a video

| What happens | Reason | Marked? |
|---|---|---|
| The video resolves but has no audio stream the player can use. Since YouTube's PO-token enforcement, this is how YouTube refusing a video to NewPipe's client shows: a browser still plays it. The app can't tell a refusal from a video that has no audio at all; the review tool's re-check can. | `no-audio` | yes |
| NewPipe reports the video unavailable: removed, private, age- or region-restricted, paid, live. YouTube's message is kept as `detail`. | `unavailable` | yes |
| The stream server refuses a freshly resolved URL (HTTP 403), also after the one retry with a new URL. Such refusals come and go, and one album video can back a whole family (SV, SM/USUM), so a mark would silence too much. | | no: the track moves on this time |
| Any other player error (undecodable audio, a broken container). | | no: the track moves on this time |
| No network, YouTube's bot check, or the extractor broken by a YouTube change. Nothing can play, not just this video. | | never: playback stops with an error |

A mark is per video, so it covers every slice of it. A marked source is skipped from then on, without asking YouTube, and a track whose sources are all marked is skipped at once. Marking a video that is already marked keeps the first mark.

The app tells the user with a snackbar when a source fails for the first time (not when a marked one is skipped again). A track whose sources are all marked is greyed in the family list and the queue: tapping it plays nothing and shows the reason, with Try again; it can't be swiped to the queue, and a tap on another track plays the family list without it.

Neither marked reason is retried by the player's load-error policy, so a dead track moves on in about a second. A refused URL isn't retried as is either: the app's own retry looks the video up again first.

## What clears a mark

- An import of a newer catalog in which no stream source of an active (not retired) track uses the video any more.
- Try again, on the snackbar a tap on a greyed track shows. It looks the track's marked videos up again, the way the player does, and clears the mark of each one that now resolves (for every track sharing it). A track un-greys only then ("… can play again"); one still dead keeps its marks and shows the "can't play" snackbar again, and without a connection nothing changes ("Couldn't check … right now").

Nothing else: marks don't expire.

## `broken.json`

In the app's external files folder, next to `playback.log`: `/sdcard/Android/data/io.github.pranavm716.vgmixr/files/broken.json`. The app rewrites the whole file whenever the marks change (also when the last one is cleared), and once on start. Pull it into `pipeline/out/broken.json` with:

```
cd pipeline && uv run python -m vgmixr_pipeline.broken
```

It uses adb from the Android SDK (`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`, `--adb` for another path), replaces the last pull only with a valid file, and prints the marks per family.

Example (also the shared fixture [`fixtures/broken.json`](fixtures/broken.json), which the app's writer and the pipeline's parser are both tested against):

```json
{
  "version": 1,
  "marks": [
    {
      "trackId": "oras-battle-elite-four",
      "videoId": "7DbGoUJkNfU",
      "reason": "no-audio",
      "detail": "no playable audio stream",
      "markedAt": "2026-10-21T07:23:44.638Z",
      "catalogGeneratedAt": "2026-10-21T03:12:40Z"
    }
  ]
}
```

| Field | Type | Meaning |
|---|---|---|
| `version` | int | Format version, `1`. A change to the format bumps it. |
| `marks` | array | Oldest first (by `markedAt`, then `videoId`). Empty when nothing is marked. |
| `trackId` | string | The track that found the video broken. Other tracks may use the same video. |
| `videoId` | string | The marked video. |
| `reason` | string | `no-audio` or `unavailable` (see above). |
| `detail` | string, optional | What the app saw, such as YouTube's unavailable message. Free text. |
| `markedAt` | string | When the video was first found broken, ISO 8601 in UTC (`Instant.toString()`, with or without fractional seconds). |
| `catalogGeneratedAt` | string, optional | The `generatedAt` of the catalog the app played from; missing if no catalog had been imported yet. Tells a mark on an old catalog from one on the current build. |

## The review tool's Broken tab

`uv run python -m vgmixr_pipeline.catalog.review`, then **Broken**. It reads `pipeline/out/broken.json` on every load (pull again, then reload the page), and says so when nothing was pulled yet or the file is invalid.

**Re-check.** The tool's embedded player is a browser, so it plays videos YouTube refuses to the app. A re-check looks a video up the way the app does: yt-dlp's `visionos` player client sends the same client name, id and version as NewPipe's (`VISIONOS`, 101, 1.02), over IPv4 like the app (`vgmixr_pipeline.broken.recheck`). One metadata request gives:

| Verdict | What yt-dlp sees | Shown as |
|---|---|---|
| `refused` | the watch page knows the video (its title, public or unlisted), but the client gets no audio ("This video is not available") | refused to the app, plays in a browser |
| `gone` | YouTube doesn't know the video (no title, "Video unavailable", private) | gone for everyone |
| `plays` | the client gets audio formats | plays in the app now: the mark is stale, Try again on the phone clears it |

The bot check and network trouble are no verdict: nothing is cached, and a bot check pauses YouTube requests for 30 minutes as for searches. Results are cached in `pipeline/out/youtube/app-check/<videoId>.json` with when, the client and the yt-dlp version. Loading the tab or a track only reads that cache; YouTube is asked only on a click (Check on a report, source or candidate, or Check all for the unchecked reports), 5 seconds apart, and a click always asks again. With `--cached-only` the cached verdicts show and the buttons are off.

**Reports and tracks.** A report names the catalog id the phone has (`<family>-<slug of the final title>`), while the tool keys tracks by source key. The tab finds the track by catalog id, from the titles as last built and then today's (a rename since), else as the draft track whose sources use the reported video. A report with neither shows the id alone. Reports fall in two groups:

- **Needs a fix**: a draft track (not removed) still uses the video. Every such track is listed, since one album video can back many.
- **Fixed in the draft**: no draft track uses it now. Until Rebuild it is still in `catalog/manifest.json`; after, the phone clears the mark when it imports the next APK's catalog.

A report made on a catalog other than today's manifest is tagged "older catalog". Its mark still matters: the phone keeps it until a catalog drops the video.

**Fixing.** Open track shows the track with the report above its sources, the reported video marked, and the check on every source and candidate. Fix it with the usual tools (reject, accept another candidate, search, paste a replacement, add a playlist), checking a replacement first so one refused upload isn't swapped for another (uploads from one channel are often refused alike), then Rebuild.
