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
 * eight. Network trouble keeps the default retries while [online]; with no connection they can't
 * succeed, so a track that isn't on the phone moves on at once instead of after about 3 seconds.
 */
@UnstableApi
class FailFastLoadErrorPolicy(private val online: () -> Boolean = { true }) :
    DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val hopeless = generateSequence<Throwable>(loadErrorInfo.exception) { it.cause }.any {
            it is SourceUnavailableException || it is HttpDataSource.InvalidResponseCodeException
        }
        return if (hopeless || !online()) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)
    }
}
