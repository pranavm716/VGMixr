# Manifest fixtures

Shared test data for the manifest validator, loaded by both the app's unit tests (Kotlin) and the pipeline's tests (Python), so the two sides accept and reject exactly the same manifests. The schema and its rules live in [`../manifest.md`](../manifest.md); this folder only illustrates them.

- `valid.json` must pass validation with no errors.
- Each file in `invalid/` must fail with exactly one error, whose code is listed in the table below. A fixture that breaks two rules lets a validator pass for the wrong reason.
- Invalid fixtures are minimal: a small base manifest plus one defect, not copies of `valid.json`.
- `invalid/malformed-json.json` is deliberately not valid JSON. Don't let an editor reformat it.

## `valid.json`

Besides the ordinary cases, it covers values a validator must accept:

| Case | Where |
|---|---|
| `local` source with `loop` | `rse-route-101`, `botw-hyrule-field` |
| `local` source without `loop` | `botw-hyrule-field`, `rb-pallet-town` |
| Track with only `local` sources | `botw-hyrule-field` |
| Unknown `category` (imports as `other`) | `rse-weather-institute` (`cutscene`) |
| `games` omitted, single game, and multi-game subset | `rse-route-101`, `rse-drought`, `rse-weather-institute` |
| Valid `replaces` | `rse-weather-institute` |
| Same `videoId` in two sources with different slices | `rse-weather-institute` |
| `startMs` omitted (defaults to 0) | `pla-example-theme` |
| `endMs: null` and `endMs` set | most streams; `rse-weather-institute`, `rb-pallet-town` |
| `confidence` omitted, and as a JSON integer | `pla-example-theme`; `rse-route-101` (`1`), `rse-drought` (`0`) |
| `videoId` containing `-` and `_` | `rse-weather-institute` |
| Lowercase hex `color` | `sapphire` |
| Standalone game (no `family`) with and without `generation` | `pla`, `botw` |
| Family where all games share a `generation` | `rse`, `rb` |
| Boundaries: `shortName` of 12 chars, `generation` 1, `order` 0, `startMs` 0, `confidence` 0 and 1, `introEnd` 0, `loopEnd = introEnd + 1`, `endMs = startMs + 1` | `botw`, `red`, `pla-example-theme`, `rse-route-101`, `rse-drought`, `botw-hyrule-field`, `rb-pallet-town` |
| Nested `local` path | `botw-hyrule-field` |

## Error codes

One code per validation error in [`../manifest.md`](../manifest.md#validation-errors-each-needs-a-test). Both validators report these exact strings.

| Code | Error |
|---|---|
| `malformed` | Invalid JSON, a required field missing, or a field of the wrong type (anywhere in the manifest) |
| `schema-version-unsupported` | Unsupported `schemaVersion` |
| `generated-at-invalid` | `generatedAt` not ISO-8601 UTC |
| `game-id-duplicate` | Duplicate game id |
| `game-slug-invalid` | Game id or game `family` not a valid slug |
| `game-field-empty` | `name` or `platform` empty |
| `game-generation-invalid` | `generation` present but < 1 |
| `game-short-name-invalid` | `shortName` empty or longer than 12 chars |
| `game-color-invalid` | `color` not matching `#RRGGBB` |
| `family-generation-mismatch` | Games in the same family with different `generation` values |
| `family-id-collision` | `family` slug equal to the id of a game outside that family |
| `track-id-duplicate` | Duplicate track id |
| `track-id-invalid` | Invalid track id slug |
| `track-title-empty` | `title` empty |
| `track-order-invalid` | `order` < 0 |
| `track-family-unknown` | `family` not matching any family |
| `track-games-invalid` | `games` empty, with duplicates, or with an id not in the track's family |
| `track-sources-empty` | Empty `sources` |
| `source-identity-duplicate` | Two sources in a track with the same identity |
| `track-replaces-invalid` | `replaces` entry not a slug, the track's own id, a track id in the manifest, or replaced by more than one track |
| `source-type-unknown` | Unknown source `type` |
| `local-path-invalid` | `local` path empty, absolute, containing `..`, or containing `\` |
| `stream-invalid` | `videoId` malformed, `startMs` < 0, or `endMs <= startMs` |
| `stream-confidence-invalid` | `confidence` outside 0-1 |
| `stream-loop-present` | `loop` on a `stream` source |
| `loop-invalid` | `sampleRate <= 0`, `introEnd < 0`, or `introEnd >= loopEnd` |

## Invalid fixtures

One file per condition, named `<code>-<variant>.json`.

| File | Code | Defect |
|---|---|---|
