package io.github.pranavm716.vgmixr.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingSheetTest {
    // Velocity in px/s; negative is upward (toward expanded).
    private fun settle(expansion: Float, velocity: Float) =
        settleTarget(expansion, velocity, flingVelocity = 1500f)

    @Test
    fun `let go past halfway, it opens, and short of halfway, it closes`() {
        assertEquals(true, settle(0.6f, 0f))
        assertEquals(false, settle(0.4f, 0f))
        assertEquals(true, settle(0.5f, 0f))
    }

    @Test
    fun `a flick wins over where the sheet is`() {
        assertEquals(true, settle(0.1f, -2000f))
        assertEquals(false, settle(0.9f, 2000f))
    }

    @Test
    fun `a slow drag counts as a placement, not a flick`() {
        assertEquals(false, settle(0.3f, -800f))
        assertEquals(true, settle(0.7f, 800f))
    }
}
