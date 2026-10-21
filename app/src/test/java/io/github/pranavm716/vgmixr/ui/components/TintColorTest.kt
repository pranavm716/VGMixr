package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TintColorTest {
    private fun Color.lightness() = (maxOf(red, green, blue) + minOf(red, green, blue)) / 2

    @Test
    fun `every game color tints at the same lightness, so a gradient has no light or dark bands`() {
        listOf("#444444", "#E1E1E1", "#303E51", "#EBC5C3", "#CD2236", "#FFD733").forEach {
            assertEquals(it, 0.5f, tintColor(it).lightness(), 0.01f)
        }
    }

    @Test
    fun `black and white tint alike`() {
        assertEquals(tintColor("#444444"), tintColor("#E1E1E1"))
    }

    @Test
    fun `a color keeps its hue`() {
        val sapphire = tintColor("#3D51A7")
        assertTrue(sapphire.blue > sapphire.red && sapphire.blue > sapphire.green)
        val ruby = tintColor("#CD2236")
        assertTrue(ruby.red > ruby.green && ruby.red > ruby.blue)
    }

    private fun Color.chroma() = maxOf(red, green, blue) - minOf(red, green, blue)

    @Test
    fun `a color keeps how colorful it is, so pale White 2 stays a dusty rose`() {
        listOf("#EBC5C3", "#303E51", "#CD2236", "#009652").forEach {
            assertEquals(it, chipColor(it).chroma(), tintColor(it).chroma(), 0.01f)
        }
    }
}
