package io.github.pranavm716.vgmixr.ui.playlists

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakePlaylistRepository
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaylistsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakePlaylistRepository(initialPlaylists = null)

    private val favorites = PlaylistSummary(1, "Favorites", 3, isFavorites = true)
    private val mix = PlaylistSummary(2, "Mix", 0, isFavorites = false)

    private val ruby = game("ruby")
    private val catalog = Catalog(
        games = listOf(ruby),
        families = listOf(Family("rse", "Ruby / Sapphire", "RS")),
        tracks = listOf(
            CatalogTrack("rse-a", "rse", listOf(ruby), "A", 1, emptyList()),
            CatalogTrack("rse-b", "rse", listOf(ruby), "B", 2, emptyList()),
            CatalogTrack("rse-c", "rse", listOf(ruby), "C", 3, emptyList(), BrokenReason.NO_AUDIO)
        )
    )
    private val playback = FakePlaybackController()
    private val messages = UserMessages()

    private fun viewModel() =
        PlaylistsViewModel(repository, FakeCatalogRepository(catalog), playback, messages)

    @Test
    fun `is loading until the playlists arrive`() = runTest {
        viewModel().uiState.test {
            assertEquals(PlaylistsUiState.Loading, awaitItem())
            repository.playlists.emit(listOf(favorites))
            assertEquals(PlaylistsUiState.Loaded(listOf(favorites)), awaitItem())
        }
    }

    @Test
    fun `lists the playlists in the repository's order`() = runTest {
        repository.playlists.emit(listOf(favorites, mix))

        viewModel().uiState.test {
            assertEquals(PlaylistsUiState.Loaded(listOf(favorites, mix)), awaitItem())
        }
    }

    @Test
    fun `swiping a playlist queues its playable tracks, newest first, and says so`() = runTest {
        repository.contents.value = mapOf(
            1L to Playlist(1, "Favorites", true, listOf("rse-b", "rse-c", "rse-gone", "rse-a"))
        )

        messages.messages.test {
            viewModel().onQueuePlaylist(1)

            assertEquals(
                listOf(FakePlaybackController.AddToQueue(listOf("rse-b", "rse-a"))),
                playback.calls
            )
            assertEquals(UserMessage.AddedToQueue(2), awaitItem())
        }
    }

    @Test
    fun `creating a playlist makes it and opens it`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onCreatePlaylist("Route themes")

            assertEquals(PlaylistsEvent.Open(100), awaitItem())
        }
        assertEquals(listOf("Route themes"), repository.created)
    }

    @Test
    fun `a blank name makes no playlist`() = runTest {
        viewModel().onCreatePlaylist("   ")

        assertEquals(emptyList<String>(), repository.created)
    }

    @Test
    fun `a long press plays the playable tracks, newest first, from the playlist`() = runTest {
        repository.contents.value = mapOf(
            1L to Playlist(1, "Favorites", true, listOf("rse-b", "rse-c", "rse-gone", "rse-a"))
        )

        viewModel().onPlayPlaylist(1)

        assertEquals(
            listOf(
                FakePlaybackController.PlayAll(listOf("rse-b", "rse-a"), QueueContext.Playlist(1))
            ),
            playback.calls
        )
    }

    @Test
    fun `a long press on a playlist with nothing playable plays nothing`() = runTest {
        repository.contents.value = mapOf(2L to Playlist(2, "Mix", false, listOf("rse-c")))

        viewModel().onPlayPlaylist(2)

        assertEquals(emptyList<Any>(), playback.calls)
    }

    @Test
    fun `swiping a playlist with nothing playable queues nothing`() = runTest {
        repository.contents.value = mapOf(2L to Playlist(2, "Mix", false, listOf("rse-c")))

        viewModel().onQueuePlaylist(2)

        assertEquals(emptyList<Any>(), playback.calls)
    }
}
