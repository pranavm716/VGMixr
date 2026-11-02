package io.github.pranavm716.vgmixr.domain.offline

import io.github.pranavm716.vgmixr.domain.playback.PlayQueue
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.QueueEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrefetchTest {
    private val family = QueueContext.Family("rse")
    private fun queue(current: Int) =
        PlayQueue(listOf("a", "b", "c").mapIndexed { i, id -> QueueEntry(id, family, i) }, current)

    @Test
    fun `nothing is prefetched before the current track has played`() {
        assertNull(prefetchTarget(queue(current = 0), played = null))
    }

    @Test
    fun `the next track is prefetched once the current one has played`() {
        assertEquals("b", prefetchTarget(queue(current = 0), played = "a"))
    }

    @Test
    fun `a play of another track doesn't count for the current one`() {
        assertNull(prefetchTarget(queue(current = 1), played = "a"))
    }

    @Test
    fun `the last track has nothing to prefetch`() {
        assertNull(prefetchTarget(queue(current = 2), played = "c"))
    }
}
