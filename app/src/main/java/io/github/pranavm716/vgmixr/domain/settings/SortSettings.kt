package io.github.pranavm716.vgmixr.domain.settings

import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import kotlinx.coroutines.flow.StateFlow

/**
 * The sorts kept outside the database, across launches: one shared by every family page
 * ([games]) and one for the Remixes tab. Each playlist keeps its own on its row instead.
 */
interface SortSettings {
    val games: StateFlow<SortSetting>
    val remixes: StateFlow<SortSetting>

    fun setGames(sort: SortSetting)

    fun setRemixes(sort: SortSetting)
}
