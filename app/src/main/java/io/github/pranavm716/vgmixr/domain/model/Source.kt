package io.github.pranavm716.vgmixr.domain.model

data class Loop(val sampleRate: Int, val introEnd: Int, val loopEnd: Int)

sealed interface Source {
    data class Local(val path: String, val loop: Loop?) : Source

    data class Stream(val videoId: String, val startMs: Long, val endMs: Long?) : Source
}
