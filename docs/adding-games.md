# Adding games and families

What to touch when a game or family joins the catalog. The app derives almost everything from the manifest (families, labels, chip colors, generations), so most of the work is pipeline data. The code that knows about Pokémon is listed below, by how far the new game is from mainline Pokémon.

## Any new game or family

Data, by hand:
- `pipeline/data/games.json`
  - `games`: id, name, shortName, color, optional generation (shared by the family), platform, family.
  - `tracklists`: a Bulbapedia album `page` and `disc`, or a hand-written TSV `file` in `pipeline/data/tracklists/`.
  - `youtube.playlists` / `youtube.albums`: where its videos are.
  - `youtube.aliases`: how video titles name it, if not by its name or the family label.
- `pipeline/data/durations.json` if the album page lists no lengths.
- Track order, renames, notes and stream decisions: in the review tool (writes `order.json` and `overrides.json`).
- Cover: `pipeline/covers-in/<family>.<ext>`, then `uv run python -m vgmixr_pipeline.covers`. Without one, the family shows a blank tile.
- Rebuild the catalog, check `family-report.md` and `match-report.md`, then build a new APK (the manifest is bundled in assets).
- Ids: the first build that ships a track (any build that writes the manifest, the review tool's Rebuild included, not only a deployed one) freezes its id in `pipeline/data/ids.json` as the slug of its title then. Rename before its videos match if the id should follow a fixed title; until an APK with the ids is installed, the new lines in ids.json can still be hand-edited. Commit ids.json with the manifest: without it the next build would give renamed tracks new ids.
- Later changes to a shipped game: a changed upstream title vanishes the old key ("Vanished ids" in `family-report.md`); give the new key the old id by hand in ids.json. A merged-away track's favorites follow only with a hand-written `replaces`. A split keeps the original key's id; the split part gets a new one.

Code:
- `app/.../domain/search/SearchAliases.kt`, `FAMILY_REGIONS`: the family's main region, as a search word ("hoenn"). A family missing here just has no region word.
- `pipeline/.../catalog/score.py`, `_VARIANTS`: a new region's name (it tells versions of one track apart), and `_NOISE` / `_DEMAKE` for a new platform's name.
- Family ids must stay unique across everything that will ever be added; short ids collide easily (`sm` is Sun/Moon, not Super Mario). Ids are frozen once shipped: favorites and history reference them, and every track id starts with its family id, so a family can't be renamed afterwards.

## A Pokémon spin-off (e.g. Mystery Dungeon)

- `score.py`, `_OTHER_GAMES` lists spin-offs as "never a mainline game's audio": a video naming "mystery dungeon" counts as another game entirely, so the family's own videos would be rejected. Drop the entry, or skip entries that name a family in the catalog.
- Generation is optional. Set it only if "gen N" in search should find the game.
- The roadmap's catalog scope (step 16: "mainline Gen 1-9 … no spin-offs") needs revising first.

## Another franchise (e.g. Zelda)

- Manifest: the deferred `franchise` field on games (`docs/manifest.md`), the top-level grouping on the Games tab, which today is one flat list of families.
- Track lists: Bulbapedia is Pokémon-only. Use TSV files, or add another source to the builder.
- `score.py` assumes Pokémon video titles:
  - `_NOISE` drops "pokemon"; the new franchise name ("zelda", "legend of zelda") would need the same.
  - `Names.find` special-cases a leading "pokemon" before game names.
  - `_ROLES` (gym, elite, champion…) and `_VARIANTS` (regions) are Pokémon vocabulary.
  - `_OTHER_GAMES` lists Pokémon spin-offs only.
- `jingles.py`, `_JINGLE_TITLES` names Pokémon event sounds. Other jingles are caught only by the 15 s length rule.
- App search, `SearchAliases.kt`: `SEARCH_ALIASES` is Pokémon vocabulary ("e4", "vs" → battle, "pc" → Pokémon Center) and applies to every family. It is harmless elsewhere but noisy; scope it by franchise once that field exists.

## Known gaps in search (not yet fixed)

- Letters outside a–z and 0–9 after removing accents are dropped (`searchWords` in `SearchText.kt`). A Japanese title would have no searchable words, and ø, ß or æ split words ("Ørsted" becomes "rsted"). Today's titles are all English.
- Family ids are indexed as they are, not split into words like labels and titles are (`SearchIndex`). Today's ids have no hyphens; a hyphenated id such as `pmd-rescue` would be found by "pmd" (as a prefix) but not by "rescue".
- Search is linear in the track count: 3-13 ms for 2,075 tracks on the phone. Re-measure if the catalog grows several times larger.
