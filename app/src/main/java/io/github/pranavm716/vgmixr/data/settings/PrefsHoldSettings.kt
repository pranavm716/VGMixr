package io.github.pranavm716.vgmixr.data.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pranavm716.vgmixr.domain.settings.HoldSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [HoldSettings] in SharedPreferences, as string sets. */
@Singleton
class PrefsHoldSettings @Inject constructor(@ApplicationContext context: Context) : HoldSettings {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val excluded = MutableStateFlow(prefs.getStringSet(EXCLUDED_FAMILIES, null).orEmpty())
    private val chosen = MutableStateFlow(
        prefs.getStringSet(PLAYLISTS, null).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    )

    override val excludedFamilies: StateFlow<Set<String>> = excluded.asStateFlow()
    override val playlists: StateFlow<Set<Long>> = chosen.asStateFlow()

    override fun setExcludedFamilies(ids: Set<String>) {
        prefs.edit { putStringSet(EXCLUDED_FAMILIES, ids.toSet()) }
        excluded.value = ids.toSet()
    }

    override fun setPlaylists(ids: Set<Long>) {
        prefs.edit { putStringSet(PLAYLISTS, ids.map(Long::toString).toSet()) }
        chosen.value = ids.toSet()
    }

    private companion object {
        const val PREFS = "settings"
        const val EXCLUDED_FAMILIES = "hold_excluded_families"
        const val PLAYLISTS = "hold_playlists"
    }
}
