package io.github.pranavm716.vgmixr.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.PlaybackEvent

/** The custom session command that carries a [PlaybackEvent.SourceFailed] to the app. */
const val SOURCE_FAILED = "io.github.pranavm716.vgmixr.SOURCE_FAILED"

private const val TRACK_ID = "trackId"
private const val TITLE = "title"
private const val REASON = "reason"
private const val TRACK_SKIPPED = "trackSkipped"

fun PlaybackEvent.SourceFailed.toBundle(): Bundle = Bundle().apply {
    putString(TRACK_ID, trackId)
    putString(TITLE, title)
    reason?.let { putString(REASON, it.key) }
    putBoolean(TRACK_SKIPPED, trackSkipped)
}

/** The event a custom command [action] with [args] carries, if it is one. */
fun playbackEvent(action: String, args: Bundle): PlaybackEvent? = when (action) {
    SOURCE_FAILED -> args.getString(TRACK_ID)?.let {
        PlaybackEvent.SourceFailed(
            trackId = it,
            title = args.getString(TITLE).orEmpty(),
            reason = args.getString(REASON)?.let(BrokenReason::fromKey),
            trackSkipped = args.getBoolean(TRACK_SKIPPED)
        )
    }

    else -> null
}

/** The custom command and its arguments that carry this event to the app. */
fun PlaybackEvent.toCommand(): Pair<SessionCommand, Bundle> = when (this) {
    is PlaybackEvent.SourceFailed -> SessionCommand(SOURCE_FAILED, Bundle.EMPTY) to toBundle()
}
