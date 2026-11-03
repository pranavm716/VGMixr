package io.github.pranavm716.vgmixr.ui.settings

import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class HoldChoicesTest {
    private val families =
        listOf(Family("rse", "RSE", "RSE"), Family("dp", "DPPt", "DPPt"), Family("bw", "BW", "BW"))
    private val favorites = PlaylistSummary(1, "Favorites", 9, isFavorites = true)
    private val hype = PlaylistSummary(2, "Hype", 9, isFavorites = false)
    private val chill = PlaylistSummary(3, "Chill", 9, isFavorites = false)

    @Test
    fun `games list every family, checked unless excluded, with a summary`() {
        assertEquals(
            HoldChoices(
                listOf(
                    HoldChoice("rse", "RSE", true),
                    HoldChoice("dp", "DPPt", false),
                    HoldChoice("bw", "BW", true)
                ),
                summary = "2 of 3"
            ),
            gameChoices(families, excluded = setOf("dp"))
        )
        assertEquals("All", gameChoices(families, excluded = emptySet()).summary)
        assertEquals("None", gameChoices(families, excluded = setOf("rse", "dp", "bw")).summary)
    }

    @Test
    fun `playlists default to favorites, and a summary names up to two`() {
        val playlists = listOf(favorites, hype, chill)

        assertEquals(
            HoldChoices(
                listOf(
                    HoldChoice("1", "Favorites", true),
                    HoldChoice("2", "Hype", false),
                    HoldChoice("3", "Chill", false)
                ),
                summary = "Favorites"
            ),
            playlistChoices(playlists, selected = emptySet())
        )
        assertEquals("Favorites", playlistChoices(playlists, selected = setOf(99)).summary)
        assertEquals("Hype, Chill", playlistChoices(playlists, selected = setOf(2, 3)).summary)
        assertEquals("All", playlistChoices(playlists, selected = setOf(1, 2, 3)).summary)
    }

    @Test
    fun `with four playlists, three picked are counted`() {
        val four = listOf(favorites, hype, chill, PlaylistSummary(4, "Atmosphere", 9, false))

        assertEquals("3 of 4", playlistChoices(four, selected = setOf(1, 2, 3)).summary)
    }
}
