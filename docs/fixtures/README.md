# Manifest fixtures

Shared test data for the manifest validator, loaded by both the app's unit tests (Kotlin) and the pipeline's tests (Python), so the two sides accept and reject exactly the same manifests. The schema and its rules live in [`../manifest.md`](../manifest.md); this folder only illustrates them.

- `valid.json` must pass validation with no errors.
- Each file in `invalid/` must fail with exactly one error, whose code is listed in the table below. A fixture that breaks two rules lets a validator pass for the wrong reason.
- Invalid fixtures are minimal: a small base manifest plus one defect, not copies of `valid.json`. The base is a two-game family (`ruby`, `sapphire`, family `rse`), a standalone game (`pla`), and one track (`rse-route-101`) with one `stream` source. A fixture adds a second track or source only when its rule needs one.
- `invalid/syntax-error.json` is deliberately not valid JSON. Don't let an editor reformat it.

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
| `startMs` and `endMs` omitted (default to 0 and null) | `pla-example-theme` |
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

One file per condition, named `<field>-<variant>.json`, where `<field>` is the path of the offending field in dot notation (`games.color`, `tracks.sources.videoId`). Rules that span several objects are filed under the field they compare, and the table is ordered by field as it appears in a manifest, so it can be read top to bottom alongside `valid.json`.

| File | Code | Defect |
|---|---|---|
| `syntax-error.json` | `malformed` | Not valid JSON (unclosed brace) |
| `schemaVersion-string.json` | `malformed` | `schemaVersion` is `"1"`, a string |
| `schemaVersion-unsupported.json` | `schema-version-unsupported` | `schemaVersion` is `2` |
| `generatedAt-offset.json` | `generated-at-invalid` | `generatedAt` is `2026-10-14T10:00:00+02:00`: ISO-8601, but not UTC |
| `generatedAt-not-iso.json` | `generated-at-invalid` | `generatedAt` is `14/10/2026` |
| `games.id-duplicate.json` | `game-id-duplicate` | Two games with id `ruby` |
| `games.id-slug.json` | `game-slug-invalid` | Game id is `Ruby` |
| `games.name-empty.json` | `game-field-empty` | `ruby` name is `""` |
| `games.shortName-empty.json` | `game-short-name-invalid` | `ruby` shortName is `""` |
| `games.shortName-too-long.json` | `game-short-name-invalid` | `ruby` shortName is 13 chars |
| `games.color-missing.json` | `malformed` | `ruby` has no `color` |
| `games.color-alpha.json` | `game-color-invalid` | `ruby` color is `#C8372DFF`; catches a pattern not anchored at the end |
| `games.generation-zero.json` | `game-generation-invalid` | `pla` generation is `0` |
| `games.generation-mismatch.json` | `family-generation-mismatch` | `ruby` is generation 3, `sapphire` is 4 |
| `games.generation-partial.json` | `family-generation-mismatch` | `ruby` is generation 3, `sapphire` omits it |
| `games.platform-empty.json` | `game-field-empty` | `ruby` platform is `""` |
| `games.family-slug.json` | `game-slug-invalid` | `pla` family is `Legends` (no track references it) |
| `games.family-collision.json` | `family-id-collision` | `pla` family is `ruby`, the id of a game in `rse` |
| `tracks-missing.json` | `malformed` | Top-level `tracks` is missing |
| `tracks.id-duplicate.json` | `track-id-duplicate` | Two tracks with id `rse-route-101` |
| `tracks.id-slug.json` | `track-id-invalid` | Track id is `RSE-Route-101` |
| `tracks.family-unknown.json` | `track-family-unknown` | Track family is `dppt` |
| `tracks.games-empty.json` | `track-games-invalid` | `games` is `[]` |
| `tracks.games-duplicate.json` | `track-games-invalid` | `games` is `["ruby", "ruby"]` |
| `tracks.games-outside-family.json` | `track-games-invalid` | `games` is `["pla"]`, a game outside `rse` |
| `tracks.title-empty.json` | `track-title-empty` | `title` is `""` |
| `tracks.order-negative.json` | `track-order-invalid` | `order` is `-1` |
| `tracks.sources-empty.json` | `track-sources-empty` | `sources` is `[]` |
| `tracks.sources-duplicate-local.json` | `source-identity-duplicate` | Two `local` sources with path `rse/012.flac` |
| `tracks.sources-duplicate-stream.json` | `source-identity-duplicate` | Two streams of one video, one with `startMs: 0` and one omitting it (defaults to 0) |
| `tracks.sources.type-unknown.json` | `source-type-unknown` | Source type is `midi` |
| `tracks.sources.path-empty.json` | `local-path-invalid` | `path` is `""` |
| `tracks.sources.path-absolute.json` | `local-path-invalid` | `path` is `/rse/012.flac` |
| `tracks.sources.path-dotdot.json` | `local-path-invalid` | `path` is `rse/../012.flac` |
| `tracks.sources.path-backslash.json` | `local-path-invalid` | `path` is `rse\012.flac` |
| `tracks.sources.loop-on-stream.json` | `stream-loop-present` | A `stream` source has a valid `loop` |
| `tracks.sources.loop.sampleRate-zero.json` | `loop-invalid` | `sampleRate` is `0` |
| `tracks.sources.loop.introEnd-negative.json` | `loop-invalid` | `introEnd` is `-1` |
| `tracks.sources.loop.loopEnd-equals-introEnd.json` | `loop-invalid` | `loopEnd` equals `introEnd` |
| `tracks.sources.loop.loopEnd-missing.json` | `malformed` | `loop` has no `loopEnd` |
| `tracks.sources.videoId-length.json` | `stream-invalid` | `videoId` is 10 chars |
| `tracks.sources.videoId-charset.json` | `stream-invalid` | `videoId` is 11 chars including `!` |
| `tracks.sources.startMs-negative.json` | `stream-invalid` | `startMs` is `-1` |
| `tracks.sources.endMs-equals-startMs.json` | `stream-invalid` | `endMs` equals `startMs` |
| `tracks.sources.confidence-above-one.json` | `stream-confidence-invalid` | `confidence` is `1.5` |
| `tracks.sources.confidence-negative.json` | `stream-confidence-invalid` | `confidence` is `-0.1` |
| `tracks.replaces-slug.json` | `track-replaces-invalid` | Entry is `Old Route` |
| `tracks.replaces-self.json` | `track-replaces-invalid` | Track replaces its own id |
| `tracks.replaces-live.json` | `track-replaces-invalid` | Track replaces another track in the manifest |
| `tracks.replaces-twice.json` | `track-replaces-invalid` | Two tracks replace `rse-old-route` |
