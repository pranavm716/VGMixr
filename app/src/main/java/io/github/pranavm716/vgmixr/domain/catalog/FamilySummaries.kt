package io.github.pranavm716.vgmixr.domain.catalog

/** One row of the Games tab: a family's name and how many tracks it has. */
data class FamilySummary(val id: String, val name: String, val trackCount: Int)

/** Every family, in family order. A family whose tracks are all retired counts 0. */
fun Catalog.familySummaries(): List<FamilySummary> {
    val counts = tracks.groupingBy { it.familyId }.eachCount()
    return families.map { FamilySummary(it.id, it.name, counts[it.id] ?: 0) }
}
