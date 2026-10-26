package io.github.pranavm716.vgmixr.domain.offline

import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.history.Play
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineProgressTest {
    private fun track(id: String, unplayable: BrokenReason? = null) =
        CatalogTrack(id, "fam", emptyList(), id, 0, emptyList(), unplayable = unplayable)

    @Test
    fun `progress counts the tracks on the phone, leaving out broken ones that never will be`() {
        val tracks = listOf(track("a"), track("b"), track("c", BrokenReason.NO_AUDIO))

        assertEquals(OfflineProgress(1, 2), offlineProgress(tracks, setOf("a")))
        assertFalse(offlineProgress(tracks, setOf("a")).complete)
        assertTrue(offlineProgress(tracks, setOf("a", "b")).complete)
        // A broken track downloaded before it broke still counts.
        assertEquals(OfflineProgress(3, 3), offlineProgress(tracks, setOf("a", "b", "c")))
    }

    @Test
    fun `recent plays are newest first, once each, and only since the last clear`() {
        val at = { s: Long -> Instant.ofEpochSecond(s) }
        val plays = listOf(Play("a", at(1)), Play("b", at(2)), Play("c", at(3)), Play("b", at(4)))

        assertEquals(listOf("b", "c", "a"), recentTrackIds(plays, since = Instant.EPOCH))
        assertEquals(listOf("b", "c"), recentTrackIds(plays, since = at(2)))
    }
}
