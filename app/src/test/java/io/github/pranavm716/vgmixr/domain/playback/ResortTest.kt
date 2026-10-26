package io.github.pranavm716.vgmixr.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ResortTest {
    private val rse = QueueContext.Family("rse")

    private fun at(id: String, index: Int, context: QueueContext = rse) =
        QueueEntry(id, context, listIndex = index)

    private fun queued(vararg ids: String) = ids.map { QueueEntry(it, QueueContext.Queue) }

    private fun PlayQueue.ids() = entries.map { it.trackId }

    // The list a..e in order, b playing, q1 waiting in the queue.
    private val inOrder = PlayQueue(
        listOf(at("a", 0), at("b", 1)) + queued("q1") +
            listOf(at("c", 2), at("d", 3), at("e", 4)),
        current = 1
    )
    private val reversed = listOf("e", "d", "c", "b", "a")

    @Test
    fun `with shuffle off next from is the tracks after the current one in the new order`() {
        val after = inOrder.applying(inOrder.resort(rse, reversed, shuffle = false))

        assertEquals(listOf("a", "b", "q1", "a"), after.ids())
        assertEquals(1, after.current)
        assertEquals(queued("q1"), after.upNext)
        assertEquals(listOf(at("a", 4)), after.nextFromContext)
    }

    @Test
    fun `the tracks played keep their place and take their new positions`() {
        val after = inOrder.applying(inOrder.resort(rse, reversed, shuffle = false))

        assertEquals(listOf(at("a", 4), at("b", 3)), after.entries.take(2))
    }

    @Test
    fun `with shuffle on the order stays and only the positions change`() {
        val shuffled = PlayQueue(
            listOf(at("b", 1)) + queued("q1") + listOf(at("e", 4), at("a", 0), at("d", 3)),
            current = 0
        )

        val after = shuffled.applying(shuffled.resort(rse, reversed, shuffle = true))

        assertEquals(shuffled.ids(), after.ids())
        assertEquals(listOf(3, null, 0, 4, 1), after.entries.map { it.listIndex })
    }

    @Test
    fun `turning shuffle off after a re-sort follows the new order`() {
        val shuffled = PlayQueue(
            listOf(at("c", 2), at("e", 4), at("a", 0), at("d", 3), at("b", 1)),
            current = 0
        )
        val resorted = shuffled.applying(shuffled.resort(rse, reversed, shuffle = true))

        val after = resorted.applying(resorted.unshuffle())

        assertEquals(listOf("c", "b", "a"), after.ids())
    }

    @Test
    fun `re-sorting another collection leaves the queue alone`() {
        val other = QueueContext.Playlist(7)

        assertEquals(emptyList<QueueEdit>(), inOrder.resort(other, reversed, shuffle = false))
    }

    @Test
    fun `while a queued track plays, the list goes on after the last list track played`() {
        val queue = inOrder.copy(current = 2)

        val after = queue.applying(queue.resort(rse, reversed, shuffle = false))

        assertEquals(listOf("a", "b", "q1", "a"), after.ids())
        assertEquals(2, after.current)
    }

    @Test
    fun `the same order changes nothing`() {
        val ids = listOf("a", "b", "c", "d", "e")

        assertEquals(emptyList<QueueEdit>(), inOrder.resort(rse, ids, shuffle = false))
    }
}
