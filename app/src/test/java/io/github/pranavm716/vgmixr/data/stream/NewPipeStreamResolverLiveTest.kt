package io.github.pranavm716.vgmixr.data.stream

import io.github.pranavm716.vgmixr.domain.playback.StreamResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
}
