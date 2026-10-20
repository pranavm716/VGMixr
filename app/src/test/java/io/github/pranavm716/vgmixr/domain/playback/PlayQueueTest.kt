package io.github.pranavm716.vgmixr.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayQueueTest {
    private fun context(vararg ids: String) = ids.map { QueueEntry(it, queued = false) }

    private fun queued(vararg ids: String) = ids.map { QueueEntry(it, queued = true) }

    private val family = listOf("a", "b", "c", "d")

    @Test
    fun `tapping a track plays it, with the family before it for previous and after it for next`() {
        val edit = PlayQueue.EMPTY.playFrom(family, index = 2)

        assertEquals(QueueEdit.Replace(context("a", "b", "c", "d"), startIndex = 2), edit)
    }

    @Test
    fun `tapping keeps the tracks waiting in the queue, right after the tapped one`() {
        val queue = PlayQueue(context("x") + queued("q1", "q2") + context("y"), current = 0)

        val edit = queue.playFrom(family, index = 1)

        assertEquals(
            QueueEdit.Replace(context("a", "b") + queued("q1", "q2") + context("c", "d"), 1),
            edit
        )
    }

    @Test
    fun `queued tracks already played, or playing, are dropped by a tap`() {
        val queue = PlayQueue(queued("done") + queued("now") + queued("next"), current = 1)

        val edit = queue.playFrom(family, index = 0)

        assertEquals(
            QueueEdit.Replace(context("a") + queued("next") + context("b", "c", "d"), 0),
            edit
        )
    }

    @Test
    fun `adding goes after the current track, ahead of the rest of the family`() {
        val queue = PlayQueue(context("a", "b", "c"), current = 1)

        assertEquals(
            QueueEdit.Insert(index = 2, queued("q1")),
            queue.add(listOf("q1"), hasEnded = false)
        )
    }

    @Test
    fun `adding again goes after the tracks added before, so the queue keeps its order`() {
        val queue = PlayQueue(context("a", "b") + queued("q1", "q2") + context("c"), current = 1)

        assertEquals(
            QueueEdit.Insert(index = 4, queued("q3", "q4")),
            queue.add(listOf("q3", "q4"), hasEnded = false)
        )
    }

    @Test
    fun `adding while a queued track plays goes after the rest of the queue`() {
        val queue = PlayQueue(context("a") + queued("q1", "q2") + context("b"), current = 1)

        assertEquals(QueueEdit.Insert(3, queued("q3")), queue.add(listOf("q3"), hasEnded = false))
    }

    @Test
    fun `adding to an empty queue starts playing`() {
        assertEquals(
            QueueEdit.Replace(queued("q1", "q2"), startIndex = 0),
            PlayQueue.EMPTY.add(listOf("q1", "q2"), hasEnded = false)
        )
    }

    @Test
    fun `adding after playback ended starts playing`() {
        val queue = PlayQueue(context("a", "b"), current = 1)

        assertEquals(QueueEdit.Replace(queued("q1"), 0), queue.add(listOf("q1"), hasEnded = true))
    }

    @Test
    fun `the queue tells what is next in queue and what is next from the family`() {
        val queue =
            PlayQueue(context("a", "b") + queued("q1", "q2") + context("c", "d"), current = 1)

        assertEquals(queued("q1", "q2"), queue.upNext)
        assertEquals(context("c", "d"), queue.nextFromContext)
    }
}
