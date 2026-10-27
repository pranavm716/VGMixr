package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class LeftCatalogTest {
    private fun track(id: String) = CatalogTrack(id, "rse", emptyList(), id, 0, emptyList())

    private val catalog = Catalog(
        games = emptyList(),
        families = emptyList(),
        tracks = listOf(track("a"), track("b")),
        userTracks = listOf(track("yt:kept")),
        retiredTracks = listOf(track("old").copy(retired = true))
    )

    private fun queue(vararg ids: String, current: Int) =
        PlayQueue(ids.map { QueueEntry(it, QueueContext.Queue) }, current)

    @Test
    fun `upcoming entries whose track left the catalog are found, the playing one never`() {
        val queue = queue("old", "a", "old", "yt:gone", "b", "yt:kept", current = 0)

        assertEquals(listOf(2, 3), catalog.leftCatalog(queue))
    }

    @Test
    fun `played entries are left alone`() {
        assertEquals(emptyList<Int>(), catalog.leftCatalog(queue("old", "a", current = 1)))
    }
}
