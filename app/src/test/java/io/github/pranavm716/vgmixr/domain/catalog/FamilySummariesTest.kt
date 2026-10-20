package io.github.pranavm716.vgmixr.domain.catalog

import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class FamilySummariesTest {
    private val rse = Family("rse", "Ruby / Sapphire / Emerald", "RSE")
    private val pla = Family("pla", "Legends: Arceus", "PLA")
    private val botw = Family("botw", "Breath of the Wild", "BotW")

    private fun track(id: String, familyId: String) = CatalogTrack(
        id = id,
        familyId = familyId,
        games = emptyList(),
        title = id,
        category = Category.ROUTE,
        order = 0,
        sources = emptyList()
    )

    private val catalog = Catalog(
        games = listOf(
            game("ruby"),
            game("pla", familyId = "pla"),
            game("botw", familyId = "botw")
        ),
        families = listOf(rse, pla, botw),
        tracks = listOf(
            track("pla-village", "pla"),
            track("rse-route-101", "rse"),
            track("rse-route-102", "rse"),
            track("rse-littleroot", "rse")
        )
    )

    @Test
    fun `each family in family order with its name and track count`() {
        assertEquals(
            listOf(
                FamilySummary("rse", "Ruby / Sapphire / Emerald", 3),
                FamilySummary("pla", "Legends: Arceus", 1),
                FamilySummary("botw", "Breath of the Wild", 0)
            ),
            catalog.familySummaries()
        )
    }

    @Test
    fun `an empty catalog has no families`() {
        assertEquals(
            emptyList<FamilySummary>(),
            Catalog(emptyList(), emptyList(), emptyList()).familySummaries()
        )
    }
}
