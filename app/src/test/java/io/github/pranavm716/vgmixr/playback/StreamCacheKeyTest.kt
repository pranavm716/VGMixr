package io.github.pranavm716.vgmixr.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamCacheKeyTest {
    @Test
    fun `a stream is stored under its video id, as downloads are`() {
        assertEquals("AAAAAAAAAAA", streamCacheKey("vgmixr://stream/AAAAAAAAAAA"))
    }

    @Test
    fun `anything else keeps its uri as its key`() {
        assertEquals("file:///x.m4a", streamCacheKey("file:///x.m4a"))
    }
}
