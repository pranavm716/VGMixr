package io.github.pranavm716.vgmixr.playback

import androidx.media3.common.PlaybackException
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.playback.SourceFailure

/** Whether a player error is this source's problem, or one that stops every source. */
fun PlaybackException.sourceFailure(): SourceFailure {
    generateSequence(cause) { it.cause }.forEach {
        when (it) {
            is ResolveFailedException -> return SourceFailure.Everything(it.failure)
            is SourceUnavailableException -> return SourceFailure.ThisSource
        }
    }
    return when (errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> SourceFailure.Refused

        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            SourceFailure.Everything(ResolveFailure.NETWORK)

        else -> SourceFailure.ThisSource
    }
}
