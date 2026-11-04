package io.github.pranavm716.vgmixr.domain.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistShortcutsTest {
    private val favorites = PlaylistSummary(1, "Favorites", 93, isFavorites = true)
    private val hype = PlaylistSummary(2, "Hype", 85, isFavorites = false)
    private val atmosphere = PlaylistSummary(3, "Atmosphere", 51, isFavorites = false)
    private val nostalgia = PlaylistSummary(4, "Nostalgia", 64, isFavorites = false)
    private val chill = PlaylistSummary(6, "Chill", 64, isFavorites = false)

    @Test
    fun `favorites, hype, atmosphere and chill, in that order, whatever the tab's order`() {
        val shortcuts = playlistShortcuts(listOf(chill, nostalgia, atmosphere, favorites, hype))

        assertEquals(
            listOf(
                PlaylistShortcut(1, "Favorites", isFavorites = true),
                PlaylistShortcut(2, "Hype", isFavorites = false),
                PlaylistShortcut(3, "Atmosphere", isFavorites = false),
                PlaylistShortcut(6, "Chill", isFavorites = false)
            ),
            shortcuts
        )
    }

    @Test
    fun `a playlist is found whatever its case`() {
        val lower = PlaylistSummary(7, "hype", 1, isFavorites = false)

        assertEquals(listOf(7L), playlistShortcuts(listOf(lower)).map { it.playlistId })
    }

    @Test
    fun `a missing or renamed playlist is left out`() {
        val renamed = hype.copy(name = "Hype 2")

        assertEquals(
            listOf("Favorites", "Chill"),
            playlistShortcuts(listOf(favorites, renamed, chill)).map { it.label }
        )
    }
}
