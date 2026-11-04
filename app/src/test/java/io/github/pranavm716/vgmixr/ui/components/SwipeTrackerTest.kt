package io.github.pranavm716.vgmixr.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeTrackerTest {
    // Queue side stops at the threshold (100); remove side exits, following the finger to 500.
    private val queueOnly = SwipeTracker(threshold = 100f, margin = 20f)
    private val queueLimits = SwipeLimits(start = SwipeLimit.STOPS, end = SwipeLimit.NONE)
    private val bothLimits = SwipeLimits(start = SwipeLimit.STOPS, end = SwipeLimit.EXITS)

    private fun SwipeTracker.dragAll(limits: SwipeLimits, width: Float, vararg deltas: Float) =
        deltas.count { drag(it, limits, width) }

    @Test
    fun `reaching the threshold arms once, with one tick`() {
        val ticks = queueOnly.dragAll(queueLimits, 500f, 60f, 60f, 60f)

        assertEquals(1, ticks)
        assertTrue(queueOnly.armed)
        assertEquals(100f, queueOnly.offset)
    }

    @Test
    fun `a stopping row never moves past the threshold, however far the finger goes`() {
        queueOnly.dragAll(queueLimits, 500f, 300f)

        assertEquals(100f, queueOnly.offset)
    }

    @Test
    fun `rolling back a little on lift keeps it armed`() {
        queueOnly.dragAll(queueLimits, 500f, 100f, -5f, -10f)

        assertTrue(queueOnly.armed)
    }

    @Test
    fun `coming back moves the row at once, however far the finger went past the stop`() {
        queueOnly.dragAll(queueLimits, 500f, 200f, -10f)

        assertTrue(queueOnly.armed)
        assertEquals(90f, queueOnly.offset)
    }

    @Test
    fun `wiggling at the threshold ticks only once`() {
        val ticks = queueOnly.dragAll(queueLimits, 500f, 100f, -3f, 3f, -3f, 3f, -15f, 15f)

        assertEquals(1, ticks)
        assertTrue(queueOnly.armed)
    }

    @Test
    fun `dragging back past the margin disarms, and crossing again ticks again`() {
        var ticks = queueOnly.dragAll(queueLimits, 500f, 100f, -21f)
        assertFalse(queueOnly.armed)

        ticks += queueOnly.dragAll(queueLimits, 500f, 21f)

        assertEquals(2, ticks)
        assertTrue(queueOnly.armed)
    }

    @Test
    fun `a side with no action doesn't move, and doesn't store travel against the other side`() {
        queueOnly.dragAll(queueLimits, 500f, -200f)
        assertEquals(0f, queueOnly.offset)

        queueOnly.dragAll(queueLimits, 500f, 100f)
        assertTrue(queueOnly.armed)
    }

    @Test
    fun `an exiting row follows the finger to the row's width and arms past the threshold`() {
        val tracker = SwipeTracker(threshold = 100f, margin = 20f)

        val ticks = tracker.dragAll(bothLimits, 500f, -150f, -400f)

        assertEquals(1, ticks)
        assertTrue(tracker.armed)
        assertEquals(-500f, tracker.offset)
        tracker.dragAll(bothLimits, 500f, 50f)
        assertEquals(-450f, tracker.offset)
    }

    @Test
    fun `an exiting row hovering at the threshold ticks once`() {
        val tracker = SwipeTracker(threshold = 100f, margin = 20f)

        val ticks = tracker.dragAll(bothLimits, 500f, -100f, 5f, -5f, 5f, -5f)

        assertEquals(1, ticks)
        assertTrue(tracker.armed)
    }

    @Test
    fun `reset forgets the drag`() {
        queueOnly.dragAll(queueLimits, 500f, 300f)

        queueOnly.reset()

        assertFalse(queueOnly.armed)
        assertEquals(0f, queueOnly.offset)
        queueOnly.dragAll(queueLimits, 500f, 50f)
        assertEquals(50f, queueOnly.offset)
    }
}
