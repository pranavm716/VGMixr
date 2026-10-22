package io.github.pranavm716.vgmixr.playback

import androidx.media3.common.PlaybackException
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.playback.SourceFailure
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackErrorsTest {
    private fun error(cause: Throwable?, code: Int = PlaybackException.ERROR_CODE_IO_UNSPECIFIED) =
        PlaybackException("failed", cause, code)

    @Test
    fun `an unavailable video fails this source`() {
        assertEquals(
            SourceFailure.ThisSource,
            error(SourceUnavailableException("removed")).sourceFailure()
        )
    }

    @Test
    fun `a failed resolve stops everything, even when wrapped`() {
        val wrapped = IOException(ResolveFailedException(ResolveFailure.BOT_CHECK))

        assertEquals(
            SourceFailure.Everything(ResolveFailure.BOT_CHECK),
            error(wrapped).sourceFailure()
        )
    }

    @Test
    fun `losing the connection while streaming is a network failure`() {
        listOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        ).forEach {
            assertEquals(
                SourceFailure.Everything(ResolveFailure.NETWORK),
                error(IOException(), it).sourceFailure()
            )
        }
    }

    @Test
    fun `undecodable audio fails this source`() {
        listOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_DECODING_FAILED
        ).forEach {
            assertEquals(SourceFailure.ThisSource, error(IOException(), it).sourceFailure())
        }
    }

    @Test
    fun `a stream the server refuses (an HTTP error such as 403) counts as refused`() {
        assertEquals(
            SourceFailure.Refused,
            error(IOException(), PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS).sourceFailure()
        )
    }
}
