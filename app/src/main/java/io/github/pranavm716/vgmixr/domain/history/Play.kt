package io.github.pranavm716.vgmixr.domain.history

import java.time.Instant

/** One play of a track, as `PlayHistory` keeps it. */
data class Play(val trackId: String, val playedAt: Instant)
