package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakeHoldSettings
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakePlaylistRepository
import io.github.pranavm716.vgmixr.testing.FakeSortSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TabPlayerTest {
    private fun track(id: String, family: String?) =
        CatalogTrack(id, family, emptyList(), id, 0, emptyList())

    private val catalog = FakeCatalogRepository(
        Catalog(
            games = emptyList(),
            families = listOf(Family("rse", "RSE", "RSE"), Family("dp", "DPPt", "DPPt")),
            tracks = listOf(track("rse-a", "rse"), track("dp-a", "dp")),
            userTracks = listOf(track("yt:AAAAAAAAAAA", null))
        )
    )
    private val playlists = FakePlaylistRepository(
        initialPlaylists = listOf(
            PlaylistSummary(1, "Favorites", 1, isFavorites = true),
            PlaylistSummary(2, "Hype", 1, isFavorites = false)
        )
    ).apply {
        contents.value = mapOf(
            1L to Playlist(1, "Favorites", true, listOf("dp-a")),
            2L to Playlist(2, "Hype", false, listOf("rse-a"))
        )
    }
    private val playback = FakePlaybackController()

    private fun player(hold: FakeHoldSettings = FakeHoldSettings()) =
        TabPlayer(catalog, playlists, FakeSortSettings(), hold, playback)

    @Test
    fun `games plays the chosen families`() = runTest {
        player(FakeHoldSettings(excluded = setOf("rse"))).play(PlayableTab.GAMES)

        assertEquals(
            listOf(FakePlaybackController.PlayAll(listOf("dp-a"), QueueContext.Family("dp"))),
            playback.calls
        )
    }

    @Test
    fun `playlists plays the chosen playlists`() = runTest {
        player(FakeHoldSettings(playlists = setOf(1, 2))).play(PlayableTab.PLAYLISTS)

        assertEquals(
            listOf(FakePlaybackController.PlayAll(listOf("dp-a", "rse-a"), QueueContext.Playlists)),
            playback.calls
        )
    }

    @Test
    fun `remixes plays every remix`() = runTest {
        player().play(PlayableTab.REMIXES)

        assertEquals(
            listOf(FakePlaybackController.PlayAll(listOf("yt:AAAAAAAAAAA"), QueueContext.Remixes)),
            playback.calls
        )
    }
}
