package io.github.pranavm716.vgmixr.data.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pranavm716.vgmixr.domain.playback.ShuffleSetting
import javax.inject.Inject

/** [ShuffleSetting] in SharedPreferences, which caches it in memory after the first read. */
class PrefsShuffleSetting @Inject constructor(@ApplicationContext context: Context) :
    ShuffleSetting {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun isOn(): Boolean = prefs.getBoolean(SHUFFLE, false)

    override fun set(on: Boolean) = prefs.edit { putBoolean(SHUFFLE, on) }

    private companion object {
        const val PREFS = "settings"
        const val SHUFFLE = "shuffle"
    }
}
