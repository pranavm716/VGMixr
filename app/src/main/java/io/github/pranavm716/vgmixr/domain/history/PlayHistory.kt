package io.github.pranavm716.vgmixr.domain.history

import kotlinx.coroutines.flow.Flow

/** Every play of a track, oldest first, written by the playback service (see [PlayCounter]). */
interface PlayHistory {
    /** All plays, oldest first, re-emitted after each new one. */
    val plays: Flow<List<Play>>

    /** Records a play of [trackId], now. */
    suspend fun record(trackId: String)
}
