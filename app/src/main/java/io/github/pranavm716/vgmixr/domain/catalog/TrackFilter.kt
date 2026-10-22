package io.github.pranavm716.vgmixr.domain.catalog

import io.github.pranavm716.vgmixr.domain.model.Game

/** Filters ANDed together; null means "any". */
data class TrackFilter(
    val game: String? = null,
    val family: String? = null,
    val generation: Int? = null
)

data class FilterOptions(
    val games: List<Game>,
    val families: List<Family>,
    val generations: List<Int>
)

/** Tracks matching [filter], sorted by family position, then order, then title. */
fun Catalog.filter(filter: TrackFilter): List<CatalogTrack> {
    val familyPosition = families.withIndex().associate { (index, family) -> family.id to index }
    return tracks
        .filter { it.matches(filter) }
        .sortedWith(
            compareBy<CatalogTrack>(
                { familyPosition[it.familyId] ?: Int.MAX_VALUE },
                { it.order },
                { it.title }
            )
        )
}

/**
 * What a tap on one of [familyId]'s tracks plays: the family's list without its greyed tracks,
 * which would only be skipped.
 */
fun Catalog.playableFamilyTrackIds(familyId: String): List<String> =
    filter(TrackFilter(family = familyId)).filter { it.unplayable == null }.map { it.id }

private fun CatalogTrack.matches(filter: TrackFilter): Boolean =
    (filter.game == null || games.any { it.id == filter.game }) &&
        (filter.family == null || familyId == filter.family) &&
        (filter.generation == null || games.any { it.generation == filter.generation })

/** Filter-sheet choices. An empty [FilterOptions.generations] means the UI hides that filter. */
fun Catalog.filterOptions(): FilterOptions = FilterOptions(
    games = games,
    families = families,
    generations = games.mapNotNull { it.generation }.distinct().sorted()
)
