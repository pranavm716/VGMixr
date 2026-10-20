package io.github.pranavm716.vgmixr.data.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod

class AudioStreamPickerTest {
    private fun stream(
        id: String,
        format: MediaFormat = MediaFormat.M4A,
        bitrate: Int = 128,
        delivery: DeliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP,
        trackType: AudioTrackType? = null
    ): AudioStream = AudioStream.Builder()
        .setId(id)
        .setContent("https://example.com/$id", delivery == DeliveryMethod.PROGRESSIVE_HTTP)
        .setMediaFormat(format)
        .setDeliveryMethod(delivery)
        .setAverageBitrate(bitrate)
        .setAudioTrackType(trackType)
        .build()

    @Test
    fun `the highest bitrate wins`() {
        val streams = listOf(
            stream("m4a", MediaFormat.M4A, 128),
            stream("opus", MediaFormat.WEBMA_OPUS, 160),
            stream("low", MediaFormat.WEBMA_OPUS, 50)
        )

        assertEquals("opus", pickAudioStream(streams)?.id)
    }

    @Test
    fun `M4A wins a tie`() {
        val streams = listOf(
            stream("opus", MediaFormat.WEBMA_OPUS, 128),
            stream("m4a", MediaFormat.M4A, 128)
        )

        assertEquals("m4a", pickAudioStream(streams)?.id)
    }

    @Test
    fun `only progressive streams count, since they are plain URLs`() {
        val streams = listOf(
            stream("dash", bitrate = 256, delivery = DeliveryMethod.DASH),
            stream("plain", bitrate = 128)
        )

        assertEquals("plain", pickAudioStream(streams)?.id)
    }

    @Test
    fun `dubbed and descriptive tracks are skipped for the original`() {
        val streams = listOf(
            stream("dubbed", bitrate = 256, trackType = AudioTrackType.DUBBED),
            stream("descriptive", bitrate = 256, trackType = AudioTrackType.DESCRIPTIVE),
            stream("original", bitrate = 128, trackType = AudioTrackType.ORIGINAL)
        )

        assertEquals("original", pickAudioStream(streams)?.id)
    }

    @Test
    fun `formats ExoPlayer can't play are skipped`() {
        val streams = listOf(stream("3gp", MediaFormat.v3GPP, 256), stream("m4a"))

        assertEquals("m4a", pickAudioStream(streams)?.id)
    }

    @Test
    fun `no playable stream picks nothing`() {
        assertNull(pickAudioStream(listOf(stream("dash", delivery = DeliveryMethod.DASH))))
        assertNull(pickAudioStream(emptyList()))
    }
}
