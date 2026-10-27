package io.github.pranavm716.vgmixr.domain.history

import io.github.pranavm716.vgmixr.domain.playback.recentTrackIds
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * The tracks heard in the last hour, for smart shuffle. Keeps the latest history in memory, so the
 * player's listeners (which can't wait on the database) can ask at once.
 */
class RecentPlays(history: PlayHistory, private val clock: Clock, scope: CoroutineScope) {
    private val plays = history.plays.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun trackIds(): Set<String> = recentTrackIds(plays.value, clock.instant())
}
