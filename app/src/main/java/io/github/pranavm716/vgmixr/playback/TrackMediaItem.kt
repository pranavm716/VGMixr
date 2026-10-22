package io.github.pranavm716.vgmixr.playback

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.QueueEntry
import io.github.pranavm716.vgmixr.domain.playback.SkipReason
import io.github.pranavm716.vgmixr.domain.playback.SourceReport
import io.github.pranavm716.vgmixr.domain.playback.playableStreams
import java.time.Instant

/** A stream's URI in the player: resolved to a YouTube URL only when the player opens it. */
const val STREAM_URI_PREFIX = "vgmixr://stream/"

/** The URI of a track with no playable source: opening it fails, so the track is skipped. */
const val NO_SOURCE_URI = "vgmixr://none"

private const val SOURCE_INDEX = "sourceIndex"
private const val CONTEXT = "context"
private const val RETRIED = "retried"
private const val PLAYING = "playing"
private const val SKIPPED = "skipped"

/**
 * The player item for [track], playing its playable source number [sourceIndex]; [context] is where
 * it plays from, by default the track's own family. [report] says why earlier sources were skipped
 * (by default none were); a [sourceIndex] past the last source plays nothing, so the track is
 * skipped.
 */
fun trackMediaItem(
    track: CatalogTrack,
    familyName: String,
    sourceIndex: Int = 0,
    context: QueueContext = QueueContext.Family(track.familyId),
    retried: Boolean = false,
    report: SourceReport = SourceReport(sourceIndex, emptyMap())
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
        .setRequestMetadata(contextRequest(context))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(familyName)
                .setExtras(
                    Bundle().apply {
                        putInt(SOURCE_INDEX, sourceIndex)
                        putBoolean(RETRIED, retried)
                        putInt(PLAYING, report.playing ?: NONE)
                        putStringArrayList(SKIPPED, ArrayList(report.skipped.map(::encodeSkip)))
                    }
                )
                .build()
        )
        .build()
}

/** Which of its track's playable sources this item plays. */
val MediaItem.sourceIndex: Int get() = mediaMetadata.extras?.getInt(SOURCE_INDEX) ?: 0

/** Which source this item plays and why the earlier ones were skipped. */
val MediaItem.sourceReport: SourceReport
    get() {
        val extras = mediaMetadata.extras ?: return SourceReport(0, emptyMap())
        return SourceReport(
            playing = extras.getInt(PLAYING, NONE).takeIf { it != NONE },
            skipped = extras.getStringArrayList(SKIPPED).orEmpty()
                .mapNotNull(::decodeSkip)
                .toMap()
        )
    }

private const val NONE = -1

/** One skip as `index|kind|…`, since extras cross processes as plain values. */
private fun encodeSkip(skip: Map.Entry<Int, SkipReason>): String {
    val (index, reason) = skip
    return when (reason) {
        is SkipReason.Broken -> "$index|broken|${reason.reason.key}|${reason.since.toEpochMilli()}"
        SkipReason.Refused -> "$index|refused"
        is SkipReason.Failed -> "$index|failed|${reason.error}"
    }
}

private fun decodeSkip(text: String): Pair<Int, SkipReason>? {
    val parts = text.split("|")
    val index = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val reason = when (parts.getOrNull(1)) {
        "broken" -> {
            val broken = parts.getOrNull(2)?.let(BrokenReason::fromKey) ?: return null
            val since = parts.getOrNull(3)?.toLongOrNull() ?: return null
            SkipReason.Broken(broken, Instant.ofEpochMilli(since))
        }

        "refused" -> SkipReason.Refused

        "failed" -> SkipReason.Failed(parts.drop(2).joinToString("|"))

        else -> return null
    }
    return index to reason
}

/** Whether this item is the retry of a source the stream server refused. */
val MediaItem.retried: Boolean get() = mediaMetadata.extras?.getBoolean(RETRIED) ?: false

/**
 * What the app sends the player for a queue entry: only the track id and where it plays from. The
 * service builds the playable item from the catalog. The context rides in the request metadata,
 * which crosses from controller to session (a URI does not).
 */
fun queueRequest(entry: QueueEntry): MediaItem = MediaItem.Builder()
    .setMediaId(entry.trackId)
    .setRequestMetadata(contextRequest(entry.context))
    .build()

private fun contextRequest(context: QueueContext) = MediaItem.RequestMetadata.Builder()
    .setExtras(Bundle().apply { putString(CONTEXT, context.key) })
    .build()

/** Where this item plays from; an item without one counts as added to the queue. */
val MediaItem.context: QueueContext
    get() = requestMetadata.extras?.getString(CONTEXT)?.let(QueueContext::fromKey)
        ?: QueueContext.Queue

/** Whether the user added this item to the queue. */
val MediaItem.isQueued: Boolean get() = context == QueueContext.Queue

fun MediaItem.queueEntry(): QueueEntry = QueueEntry(mediaId, context)
