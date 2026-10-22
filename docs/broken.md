# Broken sources

The app marks a `stream` source's video broken when it is dead for the app, keeps the marks in Room (table `broken_streams`, keyed by `videoId`) and mirrors them to `broken.json` for the PC. The review tool's Broken tab (roadmap step 10b) reads the pulled copy.

## What marks a video

| What happens | Reason | Marked? |
|---|---|---|
| The video resolves but has no audio stream the player can use. Since YouTube's PO-token enforcement, this is how YouTube refusing a video to NewPipe's client shows: a browser still plays it. The app can't tell a refusal from a video that has no audio at all; the review tool's re-check can. | `no-audio` | yes |
| NewPipe reports the video unavailable: removed, private, age- or region-restricted, paid, live. YouTube's message is kept as `detail`. | `unavailable` | yes |
| The stream server refuses a freshly resolved URL (HTTP 403), also after the one retry with a new URL. Such refusals come and go, and one album video can back a whole family (SV, SM/USUM), so a mark would silence too much. | | no: the track moves on this time |
| Any other player error (undecodable audio, a broken container). | | no: the track moves on this time |
| No network, YouTube's bot check, or the extractor broken by a YouTube change. Nothing can play, not just this video. | | never: playback stops with an error |

A mark is per video, so it covers every slice of it. A marked source is skipped from then on, without asking YouTube, and a track whose sources are all marked is skipped at once. Marking a video that is already marked keeps the first mark.

The app tells the user with a snackbar when a source fails for the first time (not when a marked one is skipped again). A track whose sources are all marked is greyed in the family list and the queue; tapping it plays nothing and shows the reason, with Try again.

Neither marked reason is retried by the player's load-error policy, so a dead track moves on in about a second. A refused URL isn't retried as is either: the app's own retry looks the video up again first.

## What clears a mark

- An import of a newer catalog in which no stream source of an active (not retired) track uses the video any more.
- Try again, on the snackbar a tap on a greyed track shows. It clears the marks of that track's videos (for every track sharing them); the next tap tries YouTube again.

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
