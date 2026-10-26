package io.github.pranavm716.vgmixr.domain.offline

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineCatalogTest {
    private fun track(id: String, unplayable: BrokenReason? = null) =
        CatalogTrack(id, "fam", emptyList(), id, 0, emptyList(), unplayable = unplayable)

    @Test
    fun `offline, only tracks on the phone play, and broken ones stay broken`() {
        val catalog = Catalog(
            games = emptyList(),
            families = emptyList(),
            tracks = listOf(track("kept"), track("streamed"), track("gone", BrokenReason.NO_AUDIO)),
            userTracks = listOf(track("yt:a"), track("yt:b"))
        )

        val offline = catalog.offline(playable = setOf("kept", "yt:a"))

        assertEquals(
            mapOf(
                "kept" to false,
                "streamed" to true,
                "gone" to false,
                "yt:a" to false,
                "yt:b" to true
            ),
            (offline.tracks + offline.userTracks).associate { it.id to it.offline }
        )
        assertEquals(BrokenReason.NO_AUDIO, offline.track("gone")?.unplayable)
    }
}
