package io.github.pranavm716.vgmixr.ui.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueDragTest {
    // Three queued rows, 100px tall, keyed by their queue entry index.
    private val rows = listOf(RowSpan(2, 0f, 100f), RowSpan(3, 100f, 200f), RowSpan(4, 200f, 300f))

    @Test
    fun `the dragged row's center over another queued row picks that row`() {
        assertEquals(3, hoveredRow(centerY = 150f, rows, dragged = 2))
        assertEquals(2, hoveredRow(centerY = 10f, rows, dragged = 4))
    }

    @Test
    fun `over its own place, or past the queued rows, nothing changes`() {
        assertNull(hoveredRow(centerY = 50f, rows, dragged = 2))
        assertNull(hoveredRow(centerY = 350f, rows, dragged = 2))
        assertNull(hoveredRow(centerY = -20f, rows, dragged = 4))
    }

    @Test
    fun `the list scrolls only near its edges, faster closer to them`() {
        fun speed(y: Float) = autoScrollSpeed(y, top = 0f, bottom = 1000f, edge = 100f, max = 20f)

        assertEquals(0f, speed(500f))
        assertTrue(speed(50f) < 0f)
        assertTrue(speed(950f) > 0f)
        assertTrue(speed(10f) < speed(50f))
        assertTrue(speed(990f) > speed(950f))
        assertEquals(-20f, speed(-30f))
        assertEquals(20f, speed(1030f))
    }

    @Test
    fun `the list scrolls only while the queue's end that way is out of view`() {
        // The viewport runs 0..1000; the queue's last row spans 900..1000, its first 100..200.
        assertEquals(false, canAutoScroll(speed = 5f, edgeRow = RowSpan(9, 900f, 1000f), 0f, 1000f))
        assertEquals(true, canAutoScroll(speed = 5f, edgeRow = RowSpan(9, 950f, 1050f), 0f, 1000f))
        assertEquals(true, canAutoScroll(speed = 5f, edgeRow = null, 0f, 1000f))
        assertEquals(false, canAutoScroll(speed = -5f, edgeRow = RowSpan(1, 100f, 200f), 0f, 1000f))
        assertEquals(true, canAutoScroll(speed = -5f, edgeRow = RowSpan(1, -40f, 60f), 0f, 1000f))
    }

    @Test
    fun `queued rows are keyed by track and copy, so a move doesn't re-key the rows it passes`() {
        assertEquals(
            listOf("upnext-a-0", "upnext-b-0", "upnext-a-1"),
            queuedKeys(listOf("a", "b", "a"))
        )
        // After a moves from the front to the back, b keeps its key.
        assertEquals("upnext-b-0", queuedKeys(listOf("b", "a", "a"))[0])
    }

    @Test
    fun `a dropped row partly out of view scrolls just enough to show it`() {
        // The viewport runs 0..1000.
        assertEquals(0f, scrollIntoView(RowSpan(1, 100f, 200f), 0f, 1000f))
        assertEquals(50f, scrollIntoView(RowSpan(1, 950f, 1050f), 0f, 1000f))
        assertEquals(-40f, scrollIntoView(RowSpan(1, -40f, 60f), 0f, 1000f))
    }

    @Test
    fun `a dragged row is drawn inside the viewport, pinned at its edges`() {
        // A 100px row laid out at 300, in a viewport running 0..1000.
        fun drawn(offset: Float) = drawnOffset(offset, top = 300f, size = 100f, 0f, 1000f)

        assertEquals(-50f, drawn(-50f))
        assertEquals(-300f, drawn(-450f))
        assertEquals(600f, drawn(800f))
    }
}
