package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
import io.github.pranavm716.vgmixr.domain.sort.SortDirection
import io.github.pranavm716.vgmixr.domain.sort.SortKey
import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabPlayTest {
    private fun track(id: String, title: String, family: String, broken: Boolean = false) =
        CatalogTrack(
            id = id,
            familyId = family,
            games = emptyList(),
            title = title,
            order = 0,
            sources = emptyList(),
            unplayable = if (broken) BrokenReason.UNAVAILABLE else null
        )

    private val catalog = Catalog(
        games = emptyList(),
        families = listOf(Family("rse", "RSE", "RSE"), Family("dp", "DPPt", "DPPt")),
        tracks = listOf(
            track("dp-a", "A", "dp"),
            track("rse-b", "B", "rse"),
            track("rse-a", "A", "rse"),
            track("rse-x", "X", "rse", broken = true)
        )
    )
    private val byTitle = SortSetting(SortKey.Title, SortDirection.Ascending)

    @Test
    fun `games play every family in tab order, each in the sort, without greyed tracks`() {
        val mix = catalog.gamesMix(excluded = emptySet(), sort = byTitle)

        assertEquals(TabMix(listOf("rse-a", "rse-b", "dp-a"), QueueContext.Games), mix)
    }

    @Test
    fun `an excluded family is left out, and one family left plays as that family`() {
        val mix = catalog.gamesMix(excluded = setOf("dp"), sort = byTitle)

        assertEquals(TabMix(listOf("rse-a", "rse-b"), QueueContext.Family("rse")), mix)
    }

    @Test
    fun `nothing to play gives no mix`() {
        assertNull(catalog.gamesMix(excluded = setOf("rse", "dp"), sort = byTitle))
    }

    private val favorites = Playlist(1, "Favorites", true, listOf("rse-a", "dp-a"))
    private val hype = Playlist(2, "Hype", false, listOf("dp-a", "rse-b"))
    private val chill = Playlist(3, "Chill", false, listOf("rse-x", "rse-b"))
    private val all = listOf(favorites, hype, chill)

    @Test
    fun `playlists play in tab order, each track once at its first place`() {
        val mix = catalog.playlistsMix(selected = setOf(3, 1, 2), playlists = all)

        assertEquals(TabMix(listOf("rse-a", "dp-a", "rse-b"), QueueContext.Playlists), mix)
    }

    @Test
    fun `one playlist plays as that playlist`() {
        val mix = catalog.playlistsMix(selected = setOf(2), playlists = all)

        assertEquals(TabMix(listOf("dp-a", "rse-b"), QueueContext.Playlist(2)), mix)
    }

    @Test
    fun `no selection, or only deleted playlists, plays favorites`() {
        val expected = TabMix(listOf("rse-a", "dp-a"), QueueContext.Playlist(1))

        assertEquals(expected, catalog.playlistsMix(selected = emptySet(), playlists = all))
        assertEquals(expected, catalog.playlistsMix(selected = setOf(99), playlists = all))
    }

    @Test
    fun `mix contexts survive the round trip through their key`() {
        assertEquals(QueueContext.Games, QueueContext.fromKey(QueueContext.Games.key))
        assertEquals(QueueContext.Playlists, QueueContext.fromKey(QueueContext.Playlists.key))
    }
}
