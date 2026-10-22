package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.model.Source

/**
 * The sources a track can play from, in manifest order. Only streams for now: local files count as
 * missing (the skip rule) until local playback comes with the first real local files.
 */
fun playableStreams(sources: List<Source>): List<Source.Stream> =
    sources.filterIsInstance<Source.Stream>()

/** Why the playing source failed. */
sealed interface SourceFailure {
    /** This source can't play (video gone, bad response, undecodable): others may. */
    data object ThisSource : SourceFailure

    /**
     * The stream server refused a URL that resolved (an HTTP error such as 403). YouTube does this
     * now and then to a URL that works when looked up again.
     */
    data object Refused : SourceFailure

    /**
     * The source's video is dead (unavailable, or no audio the app can play): mark it broken and
     * move on.
     */
    data class Broken(val reason: BrokenReason, val detail: String?) : SourceFailure

    /** Nothing can play right now (no network, bot check, extractor broken). */
    data class Everything(val failure: ResolveFailure) : SourceFailure
}

sealed interface FailoverAction {
    /** Look the same source up again and play it once more. */
    data object RetrySource : FailoverAction

    data class TrySource(val index: Int) : FailoverAction

    data object SkipTrack : FailoverAction

    data class Stop(val failure: ResolveFailure) : FailoverAction
}

/**
 * What to do when source [sourceIndex] of a track with [sourceCount] playable sources fails;
 * [retried] says whether this was already the retry of a refused source.
 */
fun failover(
    failure: SourceFailure,
    sourceIndex: Int,
    sourceCount: Int,
    retried: Boolean = false
): FailoverAction = when (failure) {
    is SourceFailure.Everything -> FailoverAction.Stop(failure.failure)

    SourceFailure.Refused if !retried -> FailoverAction.RetrySource

    SourceFailure.ThisSource, SourceFailure.Refused, is SourceFailure.Broken ->
        if (sourceIndex + 1 < sourceCount) {
            FailoverAction.TrySource(sourceIndex + 1)
        } else {
            FailoverAction.SkipTrack
        }
}

/**
 * [failover] for a track playing source [sourceIndex] of [streams], where the videos in [broken]
 * are marked: moving on skips the marked sources.
 */
fun failover(
    failure: SourceFailure,
    sourceIndex: Int,
    streams: List<Source.Stream>,
    broken: Set<String>,
    retried: Boolean = false
): FailoverAction = when (val action = failover(failure, sourceIndex, streams.size, retried)) {
    is FailoverAction.TrySource ->
        nextSource(streams, action.index, broken)?.let(FailoverAction::TrySource)
            ?: FailoverAction.SkipTrack

    else -> action
}

/**
 * The first of [streams], from [from] on, whose video isn't in [broken]; null when none is left.
 * Indexes stay those of the full list, so a mark made mid-track never shifts them.
 */
fun nextSource(streams: List<Source.Stream>, from: Int, broken: Set<String>): Int? =
    (from until streams.size).firstOrNull { streams[it].videoId !in broken }
