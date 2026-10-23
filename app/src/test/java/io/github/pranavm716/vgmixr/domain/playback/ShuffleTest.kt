package io.github.pranavm716.vgmixr.domain.playback

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

private const val SEED = 1

class ShuffleTest {
    private val rse = QueueContext.Family("rse")

    /** A track of the tapped list, at its position [index]. */
    private fun at(id: String, index: Int) = QueueEntry(id, rse, listIndex = index)

    private fun queued(vararg ids: String) = ids.map { QueueEntry(it, QueueContext.Queue) }

    private fun PlayQueue.ids() = entries.map { it.trackId }

    private fun PlayQueue.currentId() = entries[current].trackId

    // The list a..f, tapped at b, with q1 q2 waiting in the queue.
    private val inOrder = PlayQueue(
        listOf(at("a", 0), at("b", 1)) + queued("q1", "q2") +
            listOf(at("c", 2), at("d", 3), at("e", 4), at("f", 5)),
        current = 1
    )

    @Test
    fun `turning shuffle on shuffles only next from, leaving the queue and what played`() {
        val after = inOrder.applying(inOrder.shuffle(Random(SEED)))

        assertEquals(listOf("a", "b", "q1", "q2"), after.ids().take(4))
        assertEquals("b", after.currentId())
        assertEquals(queued("q1", "q2"), after.upNext)
        val expected = listOf("c", "d", "e", "f").shuffled(Random(SEED))
        assertNotEquals(listOf("c", "d", "e", "f"), expected)
        assertEquals(expected, after.nextFromContext.map { it.trackId })
    }

    @Test
    fun `shuffled tracks keep their place in the list`() {
        val after = inOrder.applying(inOrder.shuffle(Random(SEED)))

        after.nextFromContext.forEach {
            assertEquals(inOrder.entries.first { e -> e.trackId == it.trackId }, it)
        }
    }

    @Test
    fun `with fewer than two tracks to come there is nothing to shuffle`() {
        val queue = PlayQueue(listOf(at("a", 0)) + queued("q1") + listOf(at("b", 1)), current = 0)

        assertEquals(emptyList<QueueEdit>(), queue.shuffle(Random(SEED)))
        assertEquals(emptyList<QueueEdit>(), PlayQueue.EMPTY.shuffle(Random(SEED)))
    }

    @Test
    fun `turning shuffle off plays the rest of the list in order, played tracks again`() {
        // a and d played while shuffled; b plays now.
        val queue = PlayQueue(
            listOf(at("a", 0), at("d", 3), at("b", 1), at("f", 5), at("c", 2), at("e", 4)),
            current = 2
        )

        val after = queue.applying(queue.unshuffle())

        assertEquals(listOf("a", "d", "b", "c", "d", "e", "f"), after.ids())
        assertEquals(2, after.current)
        assertEquals(listOf(2, 3, 4, 5), after.nextFromContext.map { it.listIndex })
    }

    @Test
    fun `turning shuffle off leaves the queue waiting after the current track`() {
        val queue = PlayQueue(
            listOf(at("b", 1)) + queued("q1") + listOf(at("e", 4), at("c", 2), at("d", 3)),
            current = 0
        )

        val after = queue.applying(queue.unshuffle())

        assertEquals(listOf("b", "q1", "c", "d", "e"), after.ids())
    }

    @Test
    fun `shuffle off while a queued track plays goes on after the last list track played`() {
        val queue = PlayQueue(
            listOf(at("a", 0), at("d", 3)) + queued("q1", "q2") +
                listOf(at("f", 5), at("c", 2), at("e", 4), at("b", 1)),
            current = 2
        )

        val after = queue.applying(queue.unshuffle())

        assertEquals(listOf("a", "d", "q1", "q2", "e", "f"), after.ids())
        assertEquals("q1", after.currentId())
        assertEquals(queued("q2"), after.upNext)
    }

    @Test
    fun `tracks removed from next from stay removed when shuffle goes off`() {
        // e was removed from the shuffled list.
        val queue = PlayQueue(
            listOf(at("a", 0), at("d", 3), at("b", 1), at("f", 5), at("c", 2)),
            current = 2
        )

        val after = queue.applying(queue.unshuffle())

        assertEquals(listOf("a", "d", "b", "c", "d", "f"), after.ids())
    }

    @Test
    fun `queued tracks left in next from by going back are kept, first, when shuffle goes off`() {
        // q1 was queued while c played, then previous went back to b.
        val queue = PlayQueue(
            listOf(at("a", 0), at("b", 1), at("e", 4)) + queued("q1") +
                listOf(at("d", 3), at("c", 2)),
            current = 1
        )

        val after = queue.applying(queue.unshuffle())

        assertEquals(listOf("a", "b", "q1", "c", "d", "e"), after.ids())
    }

    @Test
    fun `a list already in order is left alone`() {
        assertEquals(emptyList<QueueEdit>(), inOrder.unshuffle())
    }

    @Test
    fun `turning shuffle off with no list track played changes nothing`() {
        val queueOnly = PlayQueue(queued("q1", "q2"), current = 0)

        assertEquals(emptyList<QueueEdit>(), queueOnly.unshuffle())
        assertEquals(emptyList<QueueEdit>(), PlayQueue.EMPTY.unshuffle())
    }
}
