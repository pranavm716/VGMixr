# Manifest schema

The manifest is the only boundary between the pipeline (`pipeline/`) and the app. The pipeline writes `catalog/manifest.json`; the app validates it and imports it into Room. It holds static catalog facts only. Runtime state (favorites, play history, cache, broken-source flags) lives in Room, never here.

## Terminology

- **Game**: one release (Ruby, Sapphire, Emerald, Platinum, Legends: Arceus). The atomic unit. Each game has its own short name and color.
- **Family**: a group of games that share most of their soundtrack (RSE, DPPt). A game with no `family` field is its own single-game family, and its family id is the game's id. Families are derived from games; the manifest has no `families` array.
- A **track** belongs to a family and may be restricted to a subset of that family's games.

## Example

Example data is illustrative, not verified against the real games.

```json
{
  "schemaVersion": 2,
  "generatedAt": "2026-10-13T08:00:00Z",
  "games": [
    { "id": "ruby", "name": "Ruby", "shortName": "R", "color": "#C8372D",
      "generation": 3, "platform": "GBA", "family": "rse" },
    { "id": "sapphire", "name": "Sapphire", "shortName": "S", "color": "#2D5BC8",
      "generation": 3, "platform": "GBA", "family": "rse" },
    { "id": "emerald", "name": "Emerald", "shortName": "E", "color": "#2DA84F",
      "generation": 3, "platform": "GBA", "family": "rse" },
    { "id": "pla", "name": "Legends: Arceus", "shortName": "PLA", "color": "#7A8FB5",
      "generation": 8, "platform": "Switch" }
  ],
  "tracks": [
    {
      "id": "rse-route-101",
      "family": "rse",
      "title": "Route 101",
      "order": 12,
      "sources": [
        { "type": "local", "path": "rse/012.flac",
          "loop": { "sampleRate": 32000, "introEnd": 96000, "loopEnd": 1824000 } },
        { "type": "stream", "videoId": "XXXXXXXXXXX", "startMs": 0, "endMs": null, "confidence": 0.92 }
      ]
    },
    {
      "id": "rse-drought",
      "family": "rse",
      "games": ["ruby"],
      "title": "Drought",
      "order": 90,
      "sources": [
        { "type": "stream", "videoId": "YYYYYYYYYYY", "startMs": 0, "endMs": null, "confidence": 0.88 }
      ]
    },
    {
      "id": "rse-heavy-rain",
      "family": "rse",
      "games": ["sapphire"],
      "title": "Heavy Rain",
      "order": 91,
      "sources": [
        { "type": "stream", "videoId": "ZZZZZZZZZZZ", "startMs": 0, "endMs": null, "confidence": 0.85 }
      ]
    },
    {
      "id": "pla-example-theme",
      "family": "pla",
      "title": "Example Theme",
      "order": 1,
      "sources": [
        { "type": "stream", "videoId": "WWWWWWWWWWW", "startMs": 0, "endMs": null }
      ]
    }
  ]
}
```

## Top level

| Field | Type | Rules |
|---|---|---|
| `schemaVersion` | int | Required. Currently `2` (version 1 tracks had a `category`). App rejects any version it doesn't support. |
| `generatedAt` | string | Required. ISO-8601 UTC. Also used by the app to decide whether to re-import (see Import rules). |
| `games` | Game[] | Required. |
| `tracks` | Track[] | Required. |

All fields marked required must be present with the right JSON type; a missing field or wrong type is a validation error.

## Game

| Field | Type | Rules |
|---|---|---|
| `id` | string | Slug `[a-z0-9-]+`. Unique across all games. Immutable once published. |
| `name` | string | Non-empty. Display name (e.g. "Platinum"). |
| `shortName` | string | Non-empty, 1-12 chars. Used in UI chips (e.g. "Pt"). Uniqueness is not enforced. |
| `color` | string | Required. `#RRGGBB` hex (case-insensitive), no alpha. This game's chip and accent color. |
| `generation` | int | Optional. >= 1 when present. No upper bound (Gen 10+ is coming). Omit for franchises without generations. |
| `platform` | string | Non-empty. Display only (e.g. "GBA", "3DS", "Switch"). |
| `family` | string | Optional. Slug `[a-z0-9-]+`. Games sharing a `family` value form one family. Omit for a standalone game. |

The app derives chip text color from `color` for contrast, so the manifest doesn't store a text color.

### Families

- A family's id is its `family` slug, or for a standalone game, the game's own id.
- All games in a family must share the same `generation` (all set to the same value, or all omitted).
- A `family` slug must not equal the id of a game outside that family, since ids would collide.
- Display name is derived: member game names joined with " / " in manifest order (e.g. "Ruby / Sapphire / Emerald"). Label is derived: member short names concatenated (e.g. "RSE"). Neither is stored.
- Retired games (missing from the current manifest) are left out of the family's name and label, as they are from track chips. The app stores each game's last manifest position in Room; positions of retired games are stale and order nothing.
- Family ids are immutable once published, because tracks reference them.

### Grouping guidance

- Put games in one family when they share most of their soundtrack (e.g. RSE, DPPt).
- Make separate families when the soundtracks are substantially different (e.g. GSC vs. HGSS).

## Track

| Field | Type | Rules |
|---|---|---|
| `id` | string | Slug `[a-z0-9-]+`, convention `<family>-<slug>`. Unique. **Immutable**: favorites and history reference it. The pipeline freezes it in `pipeline/data/ids.json` the first time the track ships (the slug of its title then), so a later title change never changes it. |
| `family` | string | Required. Must match a family id: a `family` slug used by at least one game, or the id of a standalone game. |
| `games` | string[] | Optional. Non-empty, no duplicates, each entry must be the id of a game in the track's family. Omitted means the track exists in all of the family's games. |
| `title` | string | Non-empty. |
| `order` | int | >= 0. Position within the family; the app sorts by `order`, then `title`. |
| `sources` | Source[] | Required. At least one. Array order is priority. No two sources with the same identity (see Source). |
| `replaces` | string[] | Optional. Old track ids this track supersedes (see below and Import rules). |

### `replaces`

- Each entry is a valid slug.
- A track must not list its own id.
- An entry must not be the id of a track present in the same manifest.
- No two tracks may replace the same id.

### One track per distinct piece of audio

- Audio shared across games in a family is **one** track with `games` omitted. Never duplicate shared tracks per game, or favorites and lists split across identical songs.
- A game-exclusive track sets `games` to the games that have it (e.g. `["ruby"]`, `["platinum"]`, or `["ruby", "emerald"]`). Any subset is valid.
- If a same-named track sounds different between games, create separate track entries with distinct ids, each restricted to its own games.

### Display

- A track's chip is the short names of its games in manifest order, each drawn in its own game's `color`. A shared track shows all of the family's games (e.g. "RSE"); a Ruby-only track shows "R".
- Filters (at most one value each, ANDed together; applied in memory, not in SQL):
  - **Game**: matches tracks whose chip games include the game, i.e. whose family contains the game and whose `games` is omitted or includes it (selecting Ruby shows RSE shared tracks plus Ruby exclusives, not Heavy Rain).
  - **Family**: uses the derived family.
  - **Generation**: matches tracks with a chip game of that `generation`. Games without a `generation` never match, and the UI shows this filter only when at least one game has a generation.
- Track lists sort by family (in the manifest order of its first game), then `order`, then `title`.

## Source

Each source has a `type`. Known types are `local` and `stream`; an unknown `type` is a validation error (see Versioning).

**`local`**
- `path`: non-empty relative POSIX path under the app's music folder (a fixed app-specific directory, `files/music/`, populated by hand). No leading `/`, no `..`, no `\`. Format is inferred from the extension. Identity: `path`.
- `loop`: optional, see Loop. Omitted means no known loop points.

The pipeline only writes a `local` source for a file it actually extracted.

**`stream`**
- `videoId`: YouTube video ID, 11 chars `[A-Za-z0-9_-]`. Never a URL, since URLs expire.
- `startMs`: int >= 0, default 0.
- `endMs`: int or null, default null (play to the end of the video). If set, `> startMs`. Omitted and `null` are the same value, also for identity. Use `startMs`/`endMs` for tracks inside whole-album videos.
- `confidence`: optional float, 0-1 inclusive. Pipeline match score; the app ignores it.
- Identity: `(videoId, startMs, endMs)`. A `stream` source must not have `loop`.

Pipeline convention: local sources first, then streams by descending confidence. The app does not re-sort.

### Playback order

The player tries, in order: `local` sources (a missing file is silently skipped), then a cached download of a `stream` source, then `stream` sources in manifest order. The cache is runtime state in Room, never a manifest source. A stream source is marked broken only when its video exists but is dead for the app (no playable audio, or unavailable), and is skipped from then on; what marks, what doesn't and what clears a mark is in [`broken.md`](broken.md).

## Loop

Optional object on `local` sources only. Loop points describe that exact file, so streams never carry them.

| Field | Type | Rules |
|---|---|---|
| `sampleRate` | int | > 0. |
| `introEnd` | int | Sample where the loop body starts. `>= 0`. |
| `loopEnd` | int | Sample where playback wraps back to `introEnd`. `> introEnd`. |

All three fields are required when `loop` is present. Unused until v2 looping, but parsed, validated and tested already. How many times a track loops is a single global app setting, not a manifest field.

## Import rules

1. **Import trigger.** On app start, the app imports the bundled manifest if its `generatedAt` differs from the last successfully imported value (stored in Room). A failed import keeps the existing catalog, logs the errors and shows an in-app warning; it never crashes.
2. **Validate fully before writing.** Any validation error rejects the whole manifest. No partial imports.
3. **Upsert by `id`.** Existing games and tracks update in place; new ones insert. Families are derived and have no stored identity of their own.
4. **Never delete user data.** Favorites and PlayHistory are untouched by an import, except for `replaces` migration.
5. **Missing tracks and games are retired, not deleted.** A track or game absent from a new manifest is marked retired: hidden from browse, still shown (as unavailable) in favorites and history. Games are kept so retired tracks can still render their chips. A retired track or game that reappears in a later manifest is un-retired and updated.
6. **`replaces` migrates ids.** Favorites and history pointing at an id listed in a track's `replaces` are re-pointed to the new track. If both the old and new ids are favorited, they merge into one favorite. History rows are all re-pointed.
7. **Runtime source state** is keyed by source identity, not by array index, so reordering doesn't lose it. The broken flag for a stream is keyed by `videoId` (a dead video is dead for every slice); its cached download is keyed by `(videoId, startMs, endMs)`. Local state is keyed by `path`. After an import, broken marks of videos no stream source of an active track uses any more are cleared, in the same transaction.

## Validation errors (each needs a test)

Manifest-level
- Malformed manifest: invalid JSON, a required field missing, or a field of the wrong type (one grouped error)
- Unsupported `schemaVersion`
- `generatedAt` not ISO-8601 UTC

Games
- Duplicate game id
- Invalid slug (game id or `family`)
- `name` or `platform` empty
- `generation` present but < 1
- `shortName` empty or longer than 12 chars
- `color` not matching `#RRGGBB`
- Games in the same family with different `generation` values (including some set and some omitted)
- `family` slug equal to the id of a game outside that family

Tracks
- Duplicate track id
- Invalid track id slug
- `title` empty
- `order` < 0
- `family` not matching any family
- `games` present but empty, containing duplicates, or containing an id not in the track's family
- Empty `sources`
- Two sources in a track with the same identity
- `replaces` entry not a valid slug, equal to the track's own id, equal to a track id in the manifest, or replaced by more than one track

Sources
- Unknown source `type`
- `local` path empty, absolute, containing `..`, or containing `\`
- `stream` `videoId` malformed, `startMs` < 0, or `endMs <= startMs`
- `stream` `confidence` outside 0-1
- `loop` on a `stream` source
- Invalid `loop` (`sampleRate <= 0`, `introEnd < 0`, or `introEnd >= loopEnd`)

## Planned, not in the schema yet

- `franchise`: optional slug on games, for when a second franchise (e.g. Zelda) is added. It would be the top-level UI grouping dimension, and games in a family would have to share it. Adding it is non-breaking, so don't build it yet.

## Versioning

Adding optional fields or enum values does not bump `schemaVersion`, with one exception: a new source `type` bumps it, since an app that can't play a source must reject the manifest rather than import it silently. Removing or changing a field's meaning also bumps it.

## Fixtures

Shared fixtures live in `docs/fixtures/`: one valid manifest and one minimal invalid manifest per error above (the grouped malformed-manifest error gets a couple). Pipeline and app tests both load them, so the two sides can't drift. The valid manifest includes a `local` source with `loop` so loop parsing is tested before v2.
