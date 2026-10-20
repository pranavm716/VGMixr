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

    /** Nothing can play right now (no network, bot check, extractor broken). */
    data class Everything(val failure: ResolveFailure) : SourceFailure
}

sealed interface FailoverAction {
    data class TrySource(val index: Int) : FailoverAction

    data object SkipTrack : FailoverAction

    data class Stop(val failure: ResolveFailure) : FailoverAction
}

/** What to do when source [sourceIndex] of a track with [sourceCount] playable sources fails. */
fun failover(failure: SourceFailure, sourceIndex: Int, sourceCount: Int): FailoverAction =
    when (failure) {
        is SourceFailure.Everything -> FailoverAction.Stop(failure.failure)

        SourceFailure.ThisSource ->
            if (sourceIndex + 1 < sourceCount) {
                FailoverAction.TrySource(sourceIndex + 1)
            } else {
                FailoverAction.SkipTrack
            }
    }
