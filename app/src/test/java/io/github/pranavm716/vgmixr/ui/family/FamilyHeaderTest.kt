package io.github.pranavm716.vgmixr.ui.family

import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyHeaderTest {
    @Test
    fun `the header collapses as far as its spacer row has scrolled`() {
        assertEquals(0, headerScroll(firstIndex = 0, firstOffset = 0, rangePx = 300))
        assertEquals(120, headerScroll(firstIndex = 0, firstOffset = 120, rangePx = 300))
    }

    @Test
    fun `the header stops collapsing at the bar`() {
        assertEquals(300, headerScroll(firstIndex = 0, firstOffset = 450, rangePx = 300))
    }

    @Test
    fun `the header is collapsed once its spacer row is gone`() {
        assertEquals(300, headerScroll(firstIndex = 3, firstOffset = 0, rangePx = 300))
    }

    // A name 15 px wide per sp of font size.
    private val widthAt = { size: Float -> size * 15f }

    @Test
    fun `a name that fits keeps the largest size`() {
        assertEquals(22f, fitFontSize(widthAt, maxWidth = 400f, largest = 22f, smallest = 18f), 0f)
    }

    @Test
    fun `a long name shrinks in half steps until it fits on one line`() {
        // 20 sp is 300 px wide; 20.5 sp would be 307.5.
        assertEquals(20f, fitFontSize(widthAt, maxWidth = 300f, largest = 22f, smallest = 18f), 0f)
    }

    @Test
    fun `a name too long even at the smallest size keeps the smallest and wraps`() {
        assertEquals(18f, fitFontSize(widthAt, maxWidth = 100f, largest = 22f, smallest = 18f), 0f)
    }
}
