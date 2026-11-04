package io.github.pranavm716.vgmixr.domain.history

import io.github.pranavm716.vgmixr.domain.offline.recentTrackIds
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * When each track was last heard, for smart shuffle. Keeps the latest history in memory, so the
 * player's listeners (which can't wait on the database) can ask at once.
 */
class RecentPlays(history: PlayHistory, scope: CoroutineScope) {
    private val plays = history.plays.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Every track ever played -> its rank by last play, 0 = heard most recently. */
    fun lastPlayed(): Map<String, Int> = recentTrackIds(plays.value, since = Instant.MIN)
        .withIndex()
        .associate { (rank, id) -> id to rank }
}
