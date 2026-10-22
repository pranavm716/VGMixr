package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** What Try again found. */
sealed interface TryAgainResult {
    /** A marked video resolves again: its mark is gone and the track can play. */
    data object Back : TryAgainResult

    /** Every marked video is still dead; [reason] is the first one's, as just seen. */
    data class StillBroken(val reason: BrokenReason) : TryAgainResult

    /** YouTube couldn't be asked (no connection, bot check, extractor broken): nothing changed. */
    data object Unchecked : TryAgainResult
}

/**
 * Try again on a greyed track: looks its marked videos up the way the player does and clears the
 * mark of each one that resolves again. A track only un-greys once a video has been seen to work.
 */
class BrokenSourceCheck @Inject constructor(
    private val catalog: CatalogRepository,
    private val broken: BrokenSources,
    private val resolver: StreamResolver
) {
    suspend fun tryAgain(trackId: String): TryAgainResult {
        val track = catalog.catalog.first().tracks.firstOrNull { it.id == trackId }
            ?: return TryAgainResult.Unchecked
        val marks = broken.marks.first()
        val marked = playableStreams(track.sources).map { it.videoId }.distinct()
            .filter { it in marks }
        var back = false
        var stillBroken: BrokenReason? = null
        for (videoId in marked) {
            when (val result = resolver.resolve(videoId)) {
                is StreamResolution.Resolved -> {
                    broken.clear(videoId)
                    back = true
                }

                is StreamResolution.Unavailable ->
                    stillBroken = stillBroken ?: BrokenReason.UNAVAILABLE

                StreamResolution.NoAudio -> stillBroken = stillBroken ?: BrokenReason.NO_AUDIO

                // Nothing can be checked right now; stop asking.
                is StreamResolution.Failed ->
                    return if (back) TryAgainResult.Back else TryAgainResult.Unchecked
            }
        }
        return when {
            back -> TryAgainResult.Back
            stillBroken != null -> TryAgainResult.StillBroken(stillBroken)
            else -> TryAgainResult.Unchecked
        }
    }
}
