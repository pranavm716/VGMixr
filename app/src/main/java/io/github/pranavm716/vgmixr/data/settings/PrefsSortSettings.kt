package io.github.pranavm716.vgmixr.data.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pranavm716.vgmixr.domain.settings.SortSettings
import io.github.pranavm716.vgmixr.domain.sort.SortKey
import io.github.pranavm716.vgmixr.domain.sort.SortKeys
import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import io.github.pranavm716.vgmixr.domain.sort.sortSettingOf
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SortSettings] in SharedPreferences next to the accent, by key and direction names. One instance,
 * so a change reaches every screen at once.
 */
@Singleton
class PrefsSortSettings @Inject constructor(@ApplicationContext context: Context) : SortSettings {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val gamesSort = MutableStateFlow(read(GAMES, SortKeys.family, SortKeys.familyDefault))
    private val remixesSort =
        MutableStateFlow(read(REMIXES, SortKeys.remixes, SortKeys.remixesDefault))

    override val games: StateFlow<SortSetting> = gamesSort.asStateFlow()
    override val remixes: StateFlow<SortSetting> = remixesSort.asStateFlow()

    override fun setGames(sort: SortSetting) {
        write(GAMES, sort)
        gamesSort.value = sort
    }

    override fun setRemixes(sort: SortSetting) {
        write(REMIXES, sort)
        remixesSort.value = sort
    }

    private fun read(name: String, keys: List<SortKey>, default: SortSetting) = sortSettingOf(
        prefs.getString("${name}SortKey", null),
        prefs.getString("${name}SortDirection", null),
        keys,
        default
    )

    private fun write(name: String, sort: SortSetting) = prefs.edit {
        putString("${name}SortKey", sort.key.name)
        putString("${name}SortDirection", sort.direction.name)
    }

    private companion object {
        const val PREFS = "settings"
        const val GAMES = "games"
        const val REMIXES = "remixes"
    }
}
