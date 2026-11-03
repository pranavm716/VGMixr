package io.github.pranavm716.vgmixr.domain.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What a long press on the Games and Playlists tabs plays, chosen in Settings and kept across
 * launches. Families are kept as the ones left out, so a new family plays from the start;
 * playlists as the ones chosen, where none (or only deleted ones) means Favorites.
 */
interface HoldSettings {
    val excludedFamilies: StateFlow<Set<String>>
    val playlists: StateFlow<Set<Long>>

    fun setExcludedFamilies(ids: Set<String>)

    fun setPlaylists(ids: Set<Long>)

    companion object {
        /** Always the defaults: for screens and tests that don't care. */
        val NONE: HoldSettings = object : HoldSettings {
            override val excludedFamilies: StateFlow<Set<String>> = MutableStateFlow(emptySet())
            override val playlists: StateFlow<Set<Long>> = MutableStateFlow(emptySet())

            override fun setExcludedFamilies(ids: Set<String>) = Unit

            override fun setPlaylists(ids: Set<Long>) = Unit
        }
    }
}
