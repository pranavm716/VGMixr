package io.github.pranavm716.vgmixr.domain.playback

/**
 * A dev-facing trail of what playback did (resolves, player errors, failover, track changes), kept
 * on the device to diagnose failures that can't be reproduced on demand.
 */
fun interface PlaybackDiagnostics {
    fun log(event: String)

    companion object {
        val NONE = PlaybackDiagnostics {}
    }
}
