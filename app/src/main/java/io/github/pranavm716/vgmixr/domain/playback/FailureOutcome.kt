package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.model.Source

/** Something the user hears about once, as it happens. */
sealed interface PlaybackEvent {
    /**
     * A source of [trackId] ([title]) failed just now, broken for [reason] (null when not marked,
     * such as a 403 after the retry); [trackSkipped] when no source was left, else another one
     * plays. Never sent for a source that was already marked.
     */
    data class SourceFailed(
        val trackId: String,
        val title: String,
        val reason: BrokenReason?,
        val trackSkipped: Boolean
    ) : PlaybackEvent
}

/** The source that failed: [videoId] is null for a track left without a source. */
data class FailedSource(
    val trackId: String,
    val title: String,
    val sourceIndex: Int,
    val videoId: String?,
    val retried: Boolean
)

data class NewMark(val videoId: String, val reason: BrokenReason, val detail: String?)

/**
 * What follows a failed source: the player's [action], the [mark] to write for a newly broken
 * video, and the [event] to tell the user about.
 */
data class FailureOutcome(val action: FailoverAction, val mark: NewMark?, val event: PlaybackEvent?)

/**
 * Decides what follows [failure] of [failed], one of [streams], with the videos in [marks] marked;
 * [offline] when the phone has no connection.
 */
fun onSourceFailure(
    failure: SourceFailure,
    failed: FailedSource,
    streams: List<Source.Stream>,
    marks: Map<String, BrokenMark>,
    offline: Boolean = false
): FailureOutcome {
    // With no connection, a track that isn't on the phone is passed over, as it is dimmed.
    if (offline && failure == SourceFailure.Everything(ResolveFailure.NETWORK)) {
        return FailureOutcome(FailoverAction.SkipTrack, null, null)
    }
    val videoId = failed.videoId
    val broken = failure as? SourceFailure.Broken
    val alreadyMarked = videoId != null && videoId in marks
    val mark = if (broken != null && videoId != null && !alreadyMarked) {
        NewMark(videoId, broken.reason, broken.detail)
    } else {
        null
    }
    val brokenIds = if (broken != null && videoId != null) marks.keys + videoId else marks.keys
    val action = failover(failure, failed.sourceIndex, streams, brokenIds, failed.retried)
    val movedOn = action is FailoverAction.TrySource || action == FailoverAction.SkipTrack
    // Told once: a source failing just now, never a marked one skipped again or a sourceless track.
    val event = if (movedOn && videoId != null && !alreadyMarked) {
        PlaybackEvent.SourceFailed(
            failed.trackId,
            failed.title,
            broken?.reason,
            trackSkipped = action == FailoverAction.SkipTrack
        )
    } else {
        null
    }
    return FailureOutcome(action, mark, event)
}
