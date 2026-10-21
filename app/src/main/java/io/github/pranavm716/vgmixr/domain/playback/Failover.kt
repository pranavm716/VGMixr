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

    SourceFailure.ThisSource, SourceFailure.Refused ->
        if (sourceIndex + 1 < sourceCount) {
            FailoverAction.TrySource(sourceIndex + 1)
        } else {
            FailoverAction.SkipTrack
        }
}
