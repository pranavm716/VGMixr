package io.github.pranavm716.vgmixr.playback

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.playback.playableStreams

/** A stream's URI in the player: resolved to a YouTube URL only when the player opens it. */
const val STREAM_URI_PREFIX = "vgmixr://stream/"

/** The URI of a track with no playable source: opening it fails, so the track is skipped. */
const val NO_SOURCE_URI = "vgmixr://none"

private const val SOURCE_INDEX = "sourceIndex"

/** The player item for [track], playing its playable source number [sourceIndex]. */
fun trackMediaItem(track: CatalogTrack, familyName: String, sourceIndex: Int = 0): MediaItem {
    val source = playableStreams(track.sources).getOrNull(sourceIndex)
    val clipping = MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(source?.startMs ?: 0)
        .setEndPositionMs(source?.endMs ?: C.TIME_END_OF_SOURCE)
        .build()
    return MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(source?.let { STREAM_URI_PREFIX + it.videoId } ?: NO_SOURCE_URI)
        .setClippingConfiguration(clipping)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(familyName)
                .setExtras(Bundle().apply { putInt(SOURCE_INDEX, sourceIndex) })
                .build()
        )
        .build()
}

/** Which of its track's playable sources this item plays. */
val MediaItem.sourceIndex: Int get() = mediaMetadata.extras?.getInt(SOURCE_INDEX) ?: 0
