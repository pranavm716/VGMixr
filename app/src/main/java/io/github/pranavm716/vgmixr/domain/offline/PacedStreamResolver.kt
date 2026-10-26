package io.github.pranavm716.vgmixr.domain.offline

import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.playback.StreamResolution
import io.github.pranavm716.vgmixr.domain.playback.StreamResolver
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The resolver downloads use: each lookup waits its turn under [pacer], and a bot check pauses
 * the ones after it. Playback keeps the unpaced resolver, so a tap never waits on a fill.
 */
class PacedStreamResolver(
    private val upstream: StreamResolver,
    private val pacer: LookupPacer,
    private val now: () -> Instant
) : StreamResolver {
    private val turn = Mutex()

    override suspend fun resolve(videoId: String): StreamResolution = turn.withLock {
        delay(pacer.waitBefore(now()).toMillis())
        pacer.onLookup(now())
        upstream.resolve(videoId).also {
            if (it is StreamResolution.Failed && it.failure == ResolveFailure.BOT_CHECK) {
                pacer.onBotCheck(now())
            }
        }
    }
}
