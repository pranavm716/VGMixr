package io.github.pranavm716.vgmixr.domain.playback

/** The shuffle setting, one for the whole app, kept across launches. */
interface ShuffleSetting {
    fun isOn(): Boolean

    fun set(on: Boolean)
}
