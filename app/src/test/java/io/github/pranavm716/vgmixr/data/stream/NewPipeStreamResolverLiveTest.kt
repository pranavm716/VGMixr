package io.github.pranavm716.vgmixr.data.stream

import io.github.pranavm716.vgmixr.domain.playback.StreamResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Asks YouTube for real, so it is skipped unless run with `-Pvgmixr.live`:
 * `./gradlew testDebugUnitTest --tests "*LiveTest*" -Pvgmixr.live`
 */
class NewPipeStreamResolverLiveTest {
    @Before
    fun onlyWhenAsked() = assumeTrue(System.getProperty("vgmixr.live") == "true")

    @Test
    fun `resolves a real video to an audio URL`() = runBlocking {
        val result = NewPipeStreamResolver(Dispatchers.IO).resolve("WMj9b4vYTXU")

        println(result)
        assertTrue(result.toString(), result is StreamResolution.Resolved)
    }

    @Test
    fun `a video YouTube refuses to the extractor's client has no playable audio`() = runBlocking {
        // ORAS "Battle! Elite Four" upload, refused to token-free clients since PO tokens.
        val result = NewPipeStreamResolver(Dispatchers.IO).resolve("7DbGoUJkNfU")

        println(result)
        assertEquals(StreamResolution.NoAudio, result)
    }
}
