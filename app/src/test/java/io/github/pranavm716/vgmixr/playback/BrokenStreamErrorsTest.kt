package io.github.pranavm716.vgmixr.playback

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.domain.playback.BrokenMark
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.SourceFailure
import io.github.pranavm716.vgmixr.domain.playback.StreamResolution
import io.github.pranavm716.vgmixr.domain.playback.StreamResolver
import java.io.IOException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrokenStreamErrorsTest {
    private var result: StreamResolution = StreamResolution.NoAudio
    private val asked = mutableListOf<String>()
    private val marks = mutableMapOf<String, BrokenMark>()
    private val resolver = StreamUriResolver(
        object : StreamResolver {
            override suspend fun resolve(videoId: String): StreamResolution {
                asked += videoId
                return result
            }
        },
        brokenMark = { marks[it] }
    )

    private fun open(videoId: String) = resolver.resolveDataSpec(
        DataSpec.Builder().setUri(Uri.parse("vgmixr://stream/$videoId")).build()
    )

    private fun error(cause: Throwable) =
        PlaybackException("failed", cause, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)

    @Test
    fun `a video without playable audio is broken for that reason`() {
        val e = assertThrows(BrokenStreamException::class.java) { open("7DbGoUJkNfU") }

        assertEquals(BrokenReason.NO_AUDIO, e.reason)
    }

    @Test
    fun `an unavailable video is broken, keeping YouTube's message`() {
        result = StreamResolution.Unavailable("This video has been removed by the uploader")

        val e = assertThrows(BrokenStreamException::class.java) { open("gonegonegon") }

        assertEquals(BrokenReason.UNAVAILABLE, e.reason)
        assertEquals("This video has been removed by the uploader", e.detail)
    }

    @Test
    fun `a video already marked fails at once, without asking YouTube`() {
        marks["7DbGoUJkNfU"] = BrokenMark(
            "7DbGoUJkNfU",
            "oras-battle-elite-four",
            BrokenReason.NO_AUDIO,
            "no playable audio stream",
            Instant.EPOCH,
            null
        )

        val e = assertThrows(BrokenStreamException::class.java) { open("7DbGoUJkNfU") }

        assertEquals(BrokenReason.NO_AUDIO, e.reason)
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `a broken stream is a Broken failure, even when wrapped`() {
        val wrapped = IOException(BrokenStreamException(BrokenReason.UNAVAILABLE, "private"))

        assertEquals(
            SourceFailure.Broken(BrokenReason.UNAVAILABLE, "private"),
            error(wrapped).sourceFailure()
        )
    }

    @Test
    fun `a track without any source is not a broken video`() {
        assertEquals(
            SourceFailure.ThisSource,
            error(SourceUnavailableException("no playable source")).sourceFailure()
        )
    }
}
