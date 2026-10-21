package io.github.pranavm716.vgmixr.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueEditingTest {
    private val rse = QueueContext.Family("rse")

    private fun context(vararg ids: String) = ids.map { QueueEntry(it, rse) }

    private fun queued(vararg ids: String) = ids.map { QueueEntry(it, QueueContext.Queue) }

    // [history h, current c, queued q1 q2 q3, context x y z]
    private val queue = PlayQueue(
        context("h", "c") + queued("q1", "q2", "q3") + context("x", "y", "z"),
        current = 1
    )

    private fun PlayQueue.ids() = entries.map { it.trackId }

    private fun PlayQueue.currentId() = entries[current].trackId

    @Test
    fun `jumping into the queue drops the queued tracks before it and plays it`() {
        val after = queue.applying(queue.jumpTo(4))

        assertEquals(listOf("h", "c", "q3", "x", "y", "z"), after.ids())
        assertEquals("q3", after.currentId())
    }

    @Test
    fun `jumping to the first queued track plays it and keeps the rest of the queue`() {
        val after = queue.applying(queue.jumpTo(2))

        assertEquals(listOf("h", "c", "q1", "q2", "q3", "x", "y", "z"), after.ids())
        assertEquals("q1", after.currentId())
        assertEquals(queued("q2", "q3"), after.upNext)
    }

    @Test
    fun `jumping ahead in the family keeps the waiting queue right after it`() {
        val after = queue.applying(queue.jumpTo(6))

        assertEquals(listOf("h", "c", "x", "y", "q1", "q2", "q3", "z"), after.ids())
        assertEquals("y", after.currentId())
        assertEquals(queued("q1", "q2", "q3"), after.upNext)
        assertEquals(context("z"), after.nextFromContext)
    }

    @Test
    fun `jumping to the family's last track still keeps the queue after it`() {
        val after = queue.applying(queue.jumpTo(7))

        assertEquals("z", after.currentId())
        assertEquals(queued("q1", "q2", "q3"), after.upNext)
    }

    @Test
    fun `jumping in the family with nothing queued just plays it`() {
        val plain = PlayQueue(context("a", "b", "c"), current = 0)

        val after = plain.applying(plain.jumpTo(2))

        assertEquals(listOf("a", "b", "c"), after.ids())
        assertEquals("c", after.currentId())
    }

    @Test
    fun `jumping to the current track does nothing`() {
        assertEquals(emptyList<QueueEdit>(), queue.jumpTo(1))
    }

    @Test
    fun `a queued track can be removed`() {
        val after = queue.applying(queue.remove(3))

        assertEquals(listOf("h", "c", "q1", "q3", "x", "y", "z"), after.ids())
        assertEquals("c", after.currentId())
    }

    @Test
    fun `a track from the rest of the family can be removed`() {
        val after = queue.applying(queue.remove(6))

        assertEquals(listOf("h", "c", "q1", "q2", "q3", "x", "z"), after.ids())
        assertEquals("c", after.currentId())
    }

    @Test
    fun `the current track and the ones already played are not removed`() {
        assertEquals(emptyList<QueueEdit>(), queue.remove(1))
        assertEquals(emptyList<QueueEdit>(), queue.remove(0))
    }

    @Test
    fun `clearing the queue removes every queued track and keeps playing`() {
        val after = queue.applying(queue.clearQueue())

        assertEquals(listOf("h", "c", "x", "y", "z"), after.ids())
        assertEquals("c", after.currentId())
    }

    @Test
    fun `clearing an empty queue does nothing`() {
        assertEquals(emptyList<QueueEdit>(), PlayQueue(context("a", "b"), 0).clearQueue())
    }

    @Test
    fun `adding plays along with applying, as the player does`() {
        val after = queue.applying(listOf(queue.add(listOf("q4"), hasEnded = false)))

        assertEquals(queued("q1", "q2", "q3", "q4"), after.upNext)
        assertEquals("c", after.currentId())
    }
}
