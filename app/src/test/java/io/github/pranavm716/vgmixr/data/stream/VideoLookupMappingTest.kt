package io.github.pranavm716.vgmixr.data.stream

import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.usertrack.VideoDetails
import io.github.pranavm716.vgmixr.domain.usertrack.VideoLookupResult
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

class VideoLookupMappingTest {
    private val audio: AudioStream = AudioStream.Builder()
        .setId("m4a")
        .setContent("https://example.com/m4a", true)
        .setMediaFormat(MediaFormat.M4A)
        .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
        .setAverageBitrate(128)
        .build()

    private fun info(durationSeconds: Long = 185, audioStreams: List<AudioStream> = listOf(audio)) =
        StreamInfo(
            0,
            "https://www.youtube.com/watch?v=AAAAAAAAAAA",
            "https://www.youtube.com/watch?v=AAAAAAAAAAA",
            StreamType.VIDEO_STREAM,
            "AAAAAAAAAAA",
            "Route 101 [Remix]",
            0
        ).apply {
            uploaderName = "GlitchxCity"
            duration = durationSeconds
            this.audioStreams = audioStreams
        }

    @Test
    fun `a video with playable audio gives its title, channel and length`() {
        assertEquals(
            VideoLookupResult.Found(VideoDetails("Route 101 [Remix]", "GlitchxCity", 185_000)),
            lookupResult(info())
        )
    }

    @Test
    fun `an unknown length is null`() {
        assertEquals(
            VideoLookupResult.Found(VideoDetails("Route 101 [Remix]", "GlitchxCity", null)),
            lookupResult(info(durationSeconds = 0))
        )
    }

    @Test
    fun `a video without audio the player can use has no audio`() {
        assertEquals(VideoLookupResult.NoAudio, lookupResult(info(audioStreams = emptyList())))
    }

    @Test
    fun `failures map as they do for playback`() {
        assertEquals(
            VideoLookupResult.Unavailable("Private video"),
            lookupFailure(ContentNotAvailableException("Private video"))
        )
        assertEquals(
            VideoLookupResult.Failed(ResolveFailure.NETWORK),
            lookupFailure(IOException("timeout"))
        )
    }
}
