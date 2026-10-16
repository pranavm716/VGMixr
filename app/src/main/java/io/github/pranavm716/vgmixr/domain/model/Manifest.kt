package io.github.pranavm716.vgmixr.domain.model

import java.time.Instant

data class Manifest(val generatedAt: Instant, val games: List<Game>, val tracks: List<Track>)
