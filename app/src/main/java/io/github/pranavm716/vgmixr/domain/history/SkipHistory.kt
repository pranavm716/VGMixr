package io.github.pranavm716.vgmixr.domain.history

import io.github.pranavm716.vgmixr.domain.playback.QueueContext

/**
 * Tracks left before they counted as a play ([PlayCounter]), written by the playback service and
 * kept for discovery later (roadmap step 22). Nothing reads them yet.
 */
interface SkipHistory {
    /** Records [skip], now, played from [context] (null when unknown). */
    suspend fun record(skip: Skip, context: QueueContext?)
}
