package io.github.pranavm716.vgmixr.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSwipeStateTest {
    private val width = 800f
    private val limit = width / 8

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
    fun `past the last track the content only stretches a little`() {
        val swipe = swipe(hasPrevious = true, hasNext = false)
        swipe.draggable.dispatchRawDelta(-1000f)
        val stretched = swipe.offset
        assertTrue(stretched < 0f && stretched > -limit)

        swipe.draggable.dispatchRawDelta(-200f)
        assertTrue(swipe.offset < stretched && swipe.offset > stretched - 200f)
        assertTrue(swipe.offset > -limit)
    }

    @Test
    fun `before the first track the content only stretches a little`() {
        val swipe = swipe(hasPrevious = false, hasNext = true)
        swipe.draggable.dispatchRawDelta(1000f)
        assertTrue(swipe.offset > 0f && swipe.offset < limit)
    }

    @Test
    fun `dragging back across the middle follows the finger again`() {
        val swipe = swipe(hasPrevious = false, hasNext = true)
        swipe.draggable.dispatchRawDelta(200f)
        swipe.draggable.dispatchRawDelta(-500f)
        assertEquals(-300f, swipe.offset)
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
