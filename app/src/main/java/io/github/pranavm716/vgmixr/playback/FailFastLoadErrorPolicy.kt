package io.github.pranavm716.vgmixr.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * The player's retries, minus the hopeless ones. A broken video or a track without a source fails
 * the same way every time, and a refused URL fails again as is (the app retries it once with a
 * fresh one), so these fail at once and the track moves on in about a second instead of about
 * eight. Network trouble keeps the default retries, except a failed lookup with no connection
 * ([online] false): a track that isn't on the phone then moves on at once instead of after about 3
 * seconds. A stream already playing keeps its retries, since a network handover looks offline
 * for a moment.
 */
@UnstableApi
class FailFastLoadErrorPolicy(private val online: () -> Boolean = { true }) :
    DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val causes = generateSequence<Throwable>(loadErrorInfo.exception) { it.cause }
        val hopeless = causes.any {
            it is SourceUnavailableException ||
                it is HttpDataSource.InvalidResponseCodeException ||
                (it is ResolveFailedException && !online())
        }
        return if (hopeless) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)
    }
}
