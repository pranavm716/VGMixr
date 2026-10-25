package io.github.pranavm716.vgmixr.domain.catalog

import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogLookupTest {
    // The user track is a remix of an RSE track, so it has the family too.
    private fun track(id: String) = CatalogTrack(
        id = id,
        familyId = "rse",
        games = emptyList(),
        title = id,
        order = 0,
        sources = emptyList()
    )

    private val route101 = track("rse-route-101")
    private val remix = track("yt:AAAAAAAAAAA")
    private val catalog = Catalog(
        games = listOf(game("ruby")),
        families = listOf(Family("rse", "Ruby", "R")),
        tracks = listOf(route101),
        userTracks = listOf(remix)
    )

    @Test
    fun `finds a catalog track by id`() {
        assertEquals(route101, catalog.track("rse-route-101"))
    }

    @Test
    fun `finds a user track by id`() {
        assertEquals(remix, catalog.track("yt:AAAAAAAAAAA"))
    }

    @Test
    fun `an unknown id finds nothing`() {
        assertNull(catalog.track("rse-route-999"))
    }

    @Test
    fun `user tracks stay out of family browsing`() {
        assertEquals(listOf(route101), catalog.filter(TrackFilter(family = "rse")))
        assertEquals(listOf(FamilySummary("rse", "Ruby", 1)), catalog.familySummaries())
    }
}
