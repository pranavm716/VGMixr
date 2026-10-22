package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.model.Source
import java.time.Instant

/**
 * What happened to the current track's stream sources: [playing] is the one it plays (null when
 * none is left), [skipped] why each earlier one was passed over. Indexes are those of
 * [playableStreams].
 */
data class SourceReport(val playing: Int?, val skipped: Map<Int, SkipReason>)

sealed interface SkipReason {
    /** Its video is marked broken, since [since]. */
    data class Broken(val reason: BrokenReason, val since: Instant) : SkipReason

    /** The stream server refused it twice (HTTP 403 after a fresh retry); not marked. */
    data object Refused : SkipReason

    /** Another player error, by its Media3 error code name; not marked. */
    data class Failed(val error: String) : SkipReason
}

/** Something the user hears about once, as it happens. */
sealed interface PlaybackEvent {
    /**
     * A source of [trackId] failed just now, broken for [reason] (null when not marked);
     * [trackSkipped] when no source was left, else another one plays. Never sent for a source
     * that was already marked.
     */
    data class SourceFailed(
        val trackId: String,
        val reason: BrokenReason?,
        val trackSkipped: Boolean
    ) : PlaybackEvent
}

/** The report a track starts with: the marked sources before its first unmarked one. */
fun startReport(streams: List<Source.Stream>, marks: Map<String, BrokenMark>): SourceReport {
    val playing = nextSource(streams, 0, marks.keys)
    return SourceReport(playing, markedBetween(streams, 0, playing ?: streams.size, marks))
}

/** The source that failed: [videoId] is null for a track left without a source. */
data class FailedSource(
    val trackId: String,
    val sourceIndex: Int,
    val videoId: String?,
    val retried: Boolean,
    val report: SourceReport
)

data class NewMark(val videoId: String, val reason: BrokenReason, val detail: String?)

/**
 * What follows a failed source: the player's [action], the [mark] to write for a newly broken
 * video, the [report] of the track's next item, and the [event] to tell the user about.
 */
data class FailureOutcome(
    val action: FailoverAction,
    val mark: NewMark?,
    val report: SourceReport,
    val event: PlaybackEvent?
)

/**
 * Decides what follows [failure] of [failed], one of [streams], with the videos in [marks]
 * marked broken. [errorName] names the player error, [now] dates a new mark.
 */
fun onSourceFailure(
    failure: SourceFailure,
    failed: FailedSource,
    streams: List<Source.Stream>,
    marks: Map<String, BrokenMark>,
    now: Instant,
    errorName: String
): FailureOutcome {
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
    val skip = when {
        videoId == null -> null
        broken != null -> SkipReason.Broken(broken.reason, marks[videoId]?.markedAt ?: now)
        failure == SourceFailure.Refused -> SkipReason.Refused
        failure == SourceFailure.ThisSource -> SkipReason.Failed(errorName)
        else -> null
    }
    val next = when (action) {
        is FailoverAction.TrySource -> action.index

        FailoverAction.SkipTrack -> streams.size

        FailoverAction.RetrySource, is FailoverAction.Stop ->
            return FailureOutcome(action, mark, failed.report, null)
    }
    val skipped = failed.report.skipped +
        listOfNotNull(skip?.let { failed.sourceIndex to it }) +
        markedBetween(streams, failed.sourceIndex + 1, next, marks)
    val event = if (skip != null && !alreadyMarked) {
        PlaybackEvent.SourceFailed(
            failed.trackId,
            broken?.reason,
            trackSkipped = action == FailoverAction.SkipTrack
        )
    } else {
        null
    }
    return FailureOutcome(
        action,
        mark,
        SourceReport((action as? FailoverAction.TrySource)?.index, skipped),
        event
    )
}

/** The marked sources from [from] up to [until], as broken skips. */
private fun markedBetween(
    streams: List<Source.Stream>,
    from: Int,
    until: Int,
    marks: Map<String, BrokenMark>
): Map<Int, SkipReason> = (from until until).mapNotNull { index ->
    marks[streams[index].videoId]?.let { index to SkipReason.Broken(it.reason, it.markedAt) }
}.toMap()
