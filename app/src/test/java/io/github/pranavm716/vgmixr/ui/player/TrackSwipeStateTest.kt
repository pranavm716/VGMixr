package io.github.pranavm716.vgmixr.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSwipeStateTest {
    private val width = 800f
    private val limit = 100f

    private fun swipe(hasPrevious: Boolean, hasNext: Boolean) = TrackSwipeState().apply {
        this.hasPrevious = hasPrevious
        this.hasNext = hasNext
        widthPx = width
    }

    @Test
    fun `toward a neighbour the content follows the finger`() {
        val next = swipe(hasPrevious = false, hasNext = true)
        next.draggable.dispatchRawDelta(-300f)
        assertEquals(-300f, next.offset)

        val previous = swipe(hasPrevious = true, hasNext = false)
        previous.draggable.dispatchRawDelta(300f)
        assertEquals(300f, previous.offset)
    }

    @Test
    fun `toward a neighbour nothing stretches`() {
        val swipe = swipe(hasPrevious = false, hasNext = true)
        swipe.draggable.dispatchRawDelta(-300f)
        assertEquals(0f, swipe.stretch)
    }

    @Test
    fun `past the last track the content stays put and stretches a little toward the finger`() {
        val swipe = swipe(hasPrevious = true, hasNext = false)
        swipe.draggable.dispatchRawDelta(-1000f)
        assertEquals(0f, swipe.offset)
        val stretched = swipe.stretch
        assertTrue(stretched < 0f && stretched > -MAX_STRETCH)

        swipe.draggable.dispatchRawDelta(-200f)
        assertTrue(swipe.stretch < stretched && swipe.stretch > -MAX_STRETCH)
        assertEquals(0f, swipe.offset)
    }

    @Test
    fun `before the first track the content stays put and stretches a little toward the finger`() {
        val swipe = swipe(hasPrevious = false, hasNext = true)
        swipe.draggable.dispatchRawDelta(1000f)
        assertEquals(0f, swipe.offset)
        assertTrue(swipe.stretch > 0f && swipe.stretch < MAX_STRETCH)
    }

    @Test
    fun `dragging back across the middle slides with the finger again`() {
        val swipe = swipe(hasPrevious = false, hasNext = true)
        swipe.draggable.dispatchRawDelta(200f)
        swipe.draggable.dispatchRawDelta(-500f)
        assertEquals(-300f, swipe.offset)
        assertEquals(0f, swipe.stretch)
    }

    @Test
    fun `the stretch starts gently, keeps its sign and never reaches the limit`() {
        assertEquals(0f, resisted(0f, limit))
        assertTrue(resisted(10f, limit) in 0f..10f)
        assertTrue(resisted(20f, limit) > resisted(10f, limit))
        assertTrue(resisted(1_000_000f, limit) < limit)
        assertEquals(-resisted(50f, limit), resisted(-50f, limit))
    }
}
