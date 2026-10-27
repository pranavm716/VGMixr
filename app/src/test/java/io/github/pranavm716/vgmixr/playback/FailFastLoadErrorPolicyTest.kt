package io.github.pranavm716.vgmixr.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FailFastLoadErrorPolicyTest {
    private val policy = FailFastLoadErrorPolicy()
    private val spec = DataSpec(Uri.parse("vgmixr://stream/7DbGoUJkNfU"))

    private fun retryDelay(e: IOException): Long = policy.getRetryDelayMsFor(
        LoadErrorHandlingPolicy.LoadErrorInfo(
            LoadEventInfo(LoadEventInfo.getNewId(), spec, 0),
            MediaLoadData(C.DATA_TYPE_MEDIA),
            e,
            2
        )
    )

    @Test
    fun `a broken video is not retried, so its track moves on in about a second`() {
        listOf(
            BrokenStreamException(BrokenReason.NO_AUDIO, "no playable audio stream"),
            BrokenStreamException(BrokenReason.UNAVAILABLE, "removed"),
            IOException(BrokenStreamException(BrokenReason.UNAVAILABLE, "private"))
        ).forEach { assertEquals(it.toString(), C.TIME_UNSET, retryDelay(it)) }
    }

    @Test
    fun `a track without a source is not retried`() {
        assertEquals(C.TIME_UNSET, retryDelay(SourceUnavailableException("no playable source")))
    }

    @Test
    fun `a refused URL is not retried as is, since the app retries it once with a fresh URL`() {
        val refused = HttpDataSource.InvalidResponseCodeException(
            403,
            "Forbidden",
            null,
            emptyMap(),
            spec,
            ByteArray(0)
        )

        assertEquals(C.TIME_UNSET, retryDelay(refused))
    }

    @Test
    fun `network trouble keeps the player's retries`() {
        assertTrue(retryDelay(IOException("connection reset")) > 0)
        assertTrue(retryDelay(ResolveFailedException(ResolveFailure.NETWORK)) > 0)
    }

    @Test
    fun `offline, network trouble is not retried, so a track not on the phone moves on`() {
        val offline = FailFastLoadErrorPolicy(online = { false })
        fun delay(e: IOException) = offline.getRetryDelayMsFor(
            LoadErrorHandlingPolicy.LoadErrorInfo(
                LoadEventInfo(LoadEventInfo.getNewId(), spec, 0),
                MediaLoadData(C.DATA_TYPE_MEDIA),
                e,
                1
            )
        )

        assertEquals(C.TIME_UNSET, delay(ResolveFailedException(ResolveFailure.NETWORK)))
        assertEquals(C.TIME_UNSET, delay(IOException("connection reset")))
    }
}
