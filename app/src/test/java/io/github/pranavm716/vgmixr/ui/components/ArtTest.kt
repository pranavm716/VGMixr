package io.github.pranavm716.vgmixr.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtTest {
    @Test
    fun `a playlist's art is named after the playlist in lowercase`() {
        assertEquals("playlist-art/hype.webp", playlistArtAsset("Hype"))
        assertEquals("playlist-art/road trip.webp", playlistArtAsset("Road Trip"))
    }

    @Test
    fun `the average color averages each channel`() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        assertEquals(0xFF7F007F.toInt(), averageColor(intArrayOf(red, blue)))
    }

    @Test
    fun `the average color is opaque whatever the pixels' alpha`() {
        val clearGreen = 0x0000FF00
        assertEquals(0xFF00FF00.toInt(), averageColor(intArrayOf(clearGreen)))
    }
}
