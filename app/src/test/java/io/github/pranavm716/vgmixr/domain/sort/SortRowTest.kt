package io.github.pranavm716.vgmixr.domain.sort

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SortRowTest {
    private fun track(id: String, familyId: String?, order: Int = 0, remixOf: String? = null) =
        CatalogTrack(
            id = id,
            familyId = familyId,
            games = emptyList(),
            title = "Title $id",
            order = order,
            sources = emptyList(),
            channel = if (id.startsWith("yt:")) "Channel" else null,
            remixOf = remixOf
        )

    private val original = track("dp-route-201", "dp", order = 7)
    private val linked = track("yt:AAAAAAAAAAA", "dp", remixOf = original.id)
    private val unlinked = track("yt:BBBBBBBBBBB", null)
    private val catalog = Catalog(
        games = emptyList(),
        families = listOf(Family("rse", "RSE", "RSE"), Family("dp", "DPPt", "DPPt")),
        tracks = listOf(original),
        userTracks = listOf(linked, unlinked)
    )
    private val added = Instant.ofEpochSecond(5)

    @Test
    fun `a catalog track has its family's position and its order`() {
        assertEquals(
            SortRow(original.id, original.title, familyPosition = 1, order = 7, addedAt = added),
            catalog.sortRow(original, added)
        )
    }

    @Test
    fun `a linked remix takes its original's place`() {
        val row = catalog.sortRow(linked, added)
        assertEquals(1, row.familyPosition)
        assertEquals(7, row.order)
        assertEquals(true, row.isUserTrack)
        assertEquals("Channel", row.channel)
    }

    @Test
    fun `a credited remix sorts by its credit`() {
        val credited = linked.copy(credit = "Super Smash Bros. Brawl")
        assertEquals("Super Smash Bros. Brawl", catalog.sortRow(credited, added).channel)
    }

    @Test
    fun `an unlinked remix has no family`() {
        assertEquals(null, catalog.sortRow(unlinked, null).familyPosition)
    }
}
