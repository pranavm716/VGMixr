package io.github.pranavm716.vgmixr.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationArtworkTest {
    private fun channels(argb: Int) = listOf(
        argb shr 16 and 0xFF,
        argb shr 8 and 0xFF,
        argb and 0xFF
    )

    private fun assertColor(expected: Int, actual: Int) {
        val pairs = channels(expected).zip(channels(actual))
        assertTrue(
            "expected #%06X, was #%06X".format(expected and 0xFFFFFF, actual and 0xFFFFFF),
            pairs.all { (e, a) -> kotlin.math.abs(e - a) <= 1 }
        )
        assertEquals(0xFF, actual ushr 24)
    }

    @Test
    fun `a game's color is the app's tint, a quarter strength over the background`() {
        // Ruby #CD2236 tints to about #D52A3E at half lightness; a quarter of it over #111111.
        val colors = artworkColors(listOf("#CD2236"))

        assertEquals(1, colors.size)
        assertColor(0x42171C, colors.single())
    }

    @Test
    fun `several games give one stop each, in game order`() {
        val colors = artworkColors(listOf("#CD2236", "#3D51A7", "#009652"))

        assertEquals(3, colors.size)
        assertEquals(artworkColors(listOf("#CD2236")).single(), colors[0])
        assertEquals(artworkColors(listOf("#009652")).single(), colors[2])
    }

    @Test
    fun `a track without games is the plain background`() {
        assertEquals(listOf(0xFF111111.toInt()), artworkColors(emptyList()))
    }
}
