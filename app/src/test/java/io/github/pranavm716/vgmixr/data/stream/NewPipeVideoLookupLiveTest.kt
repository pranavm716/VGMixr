package io.github.pranavm716.vgmixr.data.stream

import io.github.pranavm716.vgmixr.domain.usertrack.VideoLookupResult
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
class NewPipeVideoLookupLiveTest {
    @Before
    fun onlyWhenAsked() = assumeTrue(System.getProperty("vgmixr.live") == "true")

    @Test
    fun `looks up a real video's title, channel and length`() = runBlocking {
        val result = NewPipeVideoLookup(Dispatchers.IO).lookUp("WMj9b4vYTXU")

        println(result)
        assertTrue(result.toString(), result is VideoLookupResult.Found)
        val details = (result as VideoLookupResult.Found).details
        assertTrue(details.title.isNotBlank() && details.channel.isNotBlank())
        assertTrue((details.durationMs ?: 0) > 0)
    }

    @Test
    fun `a video YouTube refuses to the extractor's client has no audio`() = runBlocking {
        // ORAS "Battle! Elite Four" upload, refused to token-free clients since PO tokens.
        assertEquals(
            VideoLookupResult.NoAudio,
            NewPipeVideoLookup(Dispatchers.IO).lookUp("7DbGoUJkNfU")
        )
    }
}
