package io.github.pranavm716.vgmixr.domain.search

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack

/**
 * The catalog's tracks with their words split out once, so a keystroke only compares words.
 * Build one per catalog.
 */
class SearchIndex(catalog: Catalog) {
    internal val entries: List<IndexedTrack> = run {
        val familyPosition = catalog.families.withIndex().associate { (i, f) -> f.id to i }
        val familyWords = catalog.families.associate { family ->
            family.id to buildSet {
                add(family.id)
                add(joined(family.label))
                addAll(FAMILY_REGIONS[family.id].orEmpty())
            }
        }
        catalog.tracks.map { track ->
            IndexedTrack(
                track = track,
                title = searchWords(track.title),
                meta = (familyWords[track.familyId].orEmpty() + track.gameWords()).toList(),
                familyPosition = familyPosition[track.familyId] ?: Int.MAX_VALUE
            )
        }
    }
}

/**
 * [title] holds the title's words in order; [meta] its family, games, generations and region,
 * which match for less.
 */
internal class IndexedTrack(
    val track: CatalogTrack,
    val title: List<String>,
    val meta: List<String>,
    val familyPosition: Int
)

private fun CatalogTrack.gameWords(): Set<String> = buildSet {
    games.forEach { game ->
        addAll(searchWords(game.name))
        add(joined(game.shortName))
        game.generation?.let { add("gen$it") }
    }
}

// A label or short name is one word: "Z-A" is "za", "DPPt" is "dppt".
private fun joined(text: String) = searchWords(text).joinToString("")
