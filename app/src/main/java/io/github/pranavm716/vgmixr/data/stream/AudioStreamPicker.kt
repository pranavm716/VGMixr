package io.github.pranavm716.vgmixr.data.stream

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod

/** Formats ExoPlayer plays from a plain URL, in tie-break order. */
private val PLAYABLE_FORMATS = listOf(
    MediaFormat.M4A,
    MediaFormat.WEBMA_OPUS,
    MediaFormat.WEBMA,
    MediaFormat.OPUS,
    MediaFormat.MP3
)

/**
 * The best audio-only stream: a progressive URL in a playable format, in the video's original
 * language, at the highest bitrate.
 */
fun pickAudioStream(streams: List<AudioStream>): AudioStream? = streams
    .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
    .filter { it.format in PLAYABLE_FORMATS }
    .filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
    .sortedWith(
        compareByDescending<AudioStream> { it.averageBitrate }
            .thenBy { PLAYABLE_FORMATS.indexOf(it.format) }
    )
    .firstOrNull()
