package io.github.pranavm716.vgmixr.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.QueueEntry
import io.github.pranavm716.vgmixr.domain.playback.playableStreams

/** A stream's URI in the player: resolved to a YouTube URL only when the player opens it. */
const val STREAM_URI_PREFIX = "vgmixr://stream/"

/** The URI of a track with no playable source: opening it fails, so the track is skipped. */
const val NO_SOURCE_URI = "vgmixr://none"

private const val SOURCE_INDEX = "sourceIndex"
private const val CONTEXT = "context"
private const val RETRIED = "retried"
private const val LIST_INDEX = "listIndex"

/**
 * The player item for [track], playing its playable source number [sourceIndex]; [context] is where
 * it plays from, by default the track's own family, and [listIndex] its place in the tapped list.
 * [artworkUri] is the media notification's art.
 */
fun trackMediaItem(
    track: CatalogTrack,
    familyName: String,
    sourceIndex: Int = 0,
    context: QueueContext = QueueContext.Family(track.familyId),
    retried: Boolean = false,
    artworkUri: Uri? = null,
    listIndex: Int? = null
): MediaItem {
    val source = playableStreams(track.sources).getOrNull(sourceIndex)
    val clipping = MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(source?.startMs ?: 0)
        .setEndPositionMs(source?.endMs ?: C.TIME_END_OF_SOURCE)
        .build()
    return MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(source?.let { STREAM_URI_PREFIX + it.videoId } ?: NO_SOURCE_URI)
        .setClippingConfiguration(clipping)
        .setRequestMetadata(contextRequest(context, listIndex))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(familyName)
                .setArtworkUri(artworkUri)
                .setExtras(
                    Bundle().apply {
                        putInt(SOURCE_INDEX, sourceIndex)
                        putBoolean(RETRIED, retried)
                    }
                )
                .build()
        )
        .build()
}

/** Which of its track's playable sources this item plays. */
val MediaItem.sourceIndex: Int get() = mediaMetadata.extras?.getInt(SOURCE_INDEX) ?: 0

/** Whether this item is the retry of a source the stream server refused. */
val MediaItem.retried: Boolean get() = mediaMetadata.extras?.getBoolean(RETRIED) ?: false

/**
 * What the app sends the player for a queue entry: only the track id and where it plays from. The
 * service builds the playable item from the catalog. The context rides in the request metadata,
 * which crosses from controller to session (a URI does not).
 */
fun queueRequest(entry: QueueEntry): MediaItem = MediaItem.Builder()
    .setMediaId(entry.trackId)
    .setRequestMetadata(contextRequest(entry.context, entry.listIndex))
    .build()

private fun contextRequest(context: QueueContext, listIndex: Int?) =
    MediaItem.RequestMetadata.Builder()
        .setExtras(
            Bundle().apply {
                putString(CONTEXT, context.key)
                listIndex?.let { putInt(LIST_INDEX, it) }
            }
        )
        .build()

/** Where this item plays from; an item without one counts as added to the queue. */
val MediaItem.context: QueueContext
    get() = requestMetadata.extras?.getString(CONTEXT)?.let(QueueContext::fromKey)
        ?: QueueContext.Queue

/** Whether the user added this item to the queue. */
val MediaItem.isQueued: Boolean get() = context == QueueContext.Queue

/** A tapped item's place in the list it was tapped in; null for a queued one. */
val MediaItem.listIndex: Int?
    get() = requestMetadata.extras?.takeIf { it.containsKey(LIST_INDEX) }?.getInt(LIST_INDEX)

fun MediaItem.queueEntry(): QueueEntry = QueueEntry(mediaId, context, listIndex)
