package io.github.pranavm716.vgmixr.ui.player

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playback.PlayQueue
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.QueueEntry
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakePlaylistRepository
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AddToPlaylistViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val ruby = game("ruby")
    private val catalog = Catalog(
        games = listOf(ruby),
        families = listOf(Family("rse", "Ruby / Sapphire", "RS")),
        tracks = listOf(
            CatalogTrack("rse-a", "rse", listOf(ruby), "Route 101", 1, emptyList()),
            CatalogTrack("rse-b", "rse", listOf(ruby), "Route 102", 2, emptyList())
        )
    )

    private val playlists = FakePlaylistRepository(
        initialPlaylists = listOf(
            PlaylistSummary(1, "Favorites", 0, isFavorites = true),
            PlaylistSummary(7, "Hype", 1, isFavorites = false)
        )
    ).apply {
        contents.value = mapOf(
            1L to Playlist(1, "Favorites", true, emptyList()),
            7L to Playlist(7, "Hype", false, listOf("rse-a"))
        )
    }
    private val playback = FakePlaybackController()

    private val viewModel by lazy {
        AddToPlaylistViewModel(FakeCatalogRepository(catalog), playlists, playback)
    }

    private fun play(trackId: String) {
        playback.state.value = PlaybackState(
            queue = PlayQueue(listOf(QueueEntry(trackId, QueueContext.Family("rse"))), 0),
            trackId = trackId,
            isPlaying = true
        )
    }

    private val favorites = AddToPlaylistRowUi(1, "Favorites", isFavorites = true, added = false)
    private val hype = AddToPlaylistRowUi(7, "Hype", isFavorites = false, added = true)

    @Test
    fun `lists every playlist, Favorites first, checked where the playing track is`() = runTest {
        play("rse-a")
        viewModel.onOpen()

        viewModel.uiState.test {
            assertEquals(
                AddToPlaylistUiState("Route 101", listOf(favorites, hype)),
                expectMostRecentItem()
            )
        }
    }

    @Test
    fun `the plus adds the track to a playlist, and the check takes it out`() = runTest {
        play("rse-a")
        viewModel.onOpen()

        viewModel.uiState.test {
            expectMostRecentItem()

            viewModel.onToggle(1)
            assertEquals(listOf(true, true), awaitItem().rows.map { it.added })
            assertEquals(listOf("rse-a"), playlists.contents.value[1]?.trackIds)

            viewModel.onToggle(7)
            assertEquals(listOf(true, false), awaitItem().rows.map { it.added })
            assertEquals(emptyList<String>(), playlists.contents.value[7]?.trackIds)
        }
    }

    @Test
    fun `the track stays the one it was opened for when the next one starts`() = runTest {
        play("rse-a")
        viewModel.onOpen()
        viewModel.uiState.test {
            expectMostRecentItem()
            play("rse-b")

            viewModel.onToggle(1)

            assertEquals("Route 101", expectMostRecentItem().trackTitle)
        }
        assertEquals(listOf("rse-a"), playlists.contents.value[1]?.trackIds)
    }

    @Test
    fun `opened for a track, it adds that track rather than the playing one`() = runTest {
        play("rse-a")
        viewModel.onOpen("rse-b")

        viewModel.uiState.test {
            assertEquals(
                AddToPlaylistUiState("Route 102", listOf(favorites, hype.copy(added = false))),
                expectMostRecentItem()
            )
            viewModel.onToggle(1)
            assertEquals(listOf(true, false), awaitItem().rows.map { it.added })
        }
        assertEquals(listOf("rse-b"), playlists.contents.value[1]?.trackIds)
    }

    @Test
    fun `nothing playing lists nothing`() = runTest {
        viewModel.onOpen()

        viewModel.uiState.test {
            assertEquals(AddToPlaylistUiState(), expectMostRecentItem())
        }
    }
}
