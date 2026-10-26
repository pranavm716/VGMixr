package io.github.pranavm716.vgmixr.ui.playlist

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
import io.github.pranavm716.vgmixr.domain.sort.SortDirection
import io.github.pranavm716.vgmixr.domain.sort.SortKey
import io.github.pranavm716.vgmixr.domain.sort.SortKeys
import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakePlaybackController.PlayAll
import io.github.pranavm716.vgmixr.testing.FakePlaybackController.PlayFrom
import io.github.pranavm716.vgmixr.testing.FakePlaybackController.Resort
import io.github.pranavm716.vgmixr.testing.FakePlaylistRepository
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.components.SortUi
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import io.github.pranavm716.vgmixr.ui.playlists.PlaylistsViewModel
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaylistSortTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val ruby = game("ruby", shortName = "R")
    private val catalog = Catalog(
        games = listOf(ruby),
        families = listOf(Family("rse", "Ruby", "R")),
        tracks = listOf(
            CatalogTrack("rse-a", "rse", listOf(ruby), "Littleroot Town", 1, emptyList()),
            CatalogTrack("rse-b", "rse", listOf(ruby), "Battle! (Wild)", 2, emptyList()),
            CatalogTrack("rse-c", "rse", listOf(ruby), "Abandoned Ship", 3, emptyList())
        )
    )
    private val playlists = FakePlaylistRepository().apply {
        contents.value = mapOf(
            7L to Playlist(
                id = 7,
                name = "Mix",
                isFavorites = false,
                trackIds = listOf("rse-b", "rse-c", "rse-a"),
                addedAt = mapOf(
                    "rse-b" to Instant.ofEpochSecond(3),
                    "rse-c" to Instant.ofEpochSecond(2),
                    "rse-a" to Instant.ofEpochSecond(1)
                )
            ),
            8L to Playlist(8, "Other", false, listOf("rse-a"))
        )
    }
    private val playback = FakePlaybackController()
    private val byGame = SortSetting(SortKey.Game, SortDirection.Ascending)

    private fun viewModel() = PlaylistViewModel(
        SavedStateHandle(mapOf("playlistId" to 7L)),
        FakeCatalogRepository(catalog),
        playlists,
        playback,
        UserMessages(),
        main.dispatcher
    )

    private suspend fun PlaylistViewModel.loaded(): PlaylistUiState.Loaded {
        var state: PlaylistUiState.Loaded? = null
        uiState.test { state = awaitItem() as PlaylistUiState.Loaded }
        return checkNotNull(state)
    }

    private suspend fun sortOf(id: Long) = playlists.playlist(id).first()?.sort

    @Test
    fun `a playlist starts newest first and offers title, game and recently added`() = runTest {
        val state = viewModel().loaded()

        assertEquals(listOf("rse-b", "rse-c", "rse-a"), state.rows.map { it.id })
        assertEquals(SortUi(SortKeys.playlistDefault, SortKeys.playlist), state.sort)
    }

    @Test
    fun `picking a key saves it on this playlist only and sorts the page`() = runTest {
        val viewModel = viewModel()

        viewModel.onSortKey(SortKey.Game)

        assertEquals(byGame, sortOf(7))
        assertEquals(SortKeys.playlistDefault, sortOf(8))
        assertEquals(listOf("rse-a", "rse-b", "rse-c"), viewModel.loaded().rows.map { it.id })
    }

    @Test
    fun `the arrow reverses the direction`() = runTest {
        viewModel().onReverseSort()

        assertEquals(SortSetting(SortKey.RecentlyAdded, SortDirection.Ascending), sortOf(7))
    }

    @Test
    fun `a sort change tells the player the playlist's new order`() = runTest {
        viewModel().onSortKey(SortKey.Title)

        assertEquals(
            listOf(Resort(listOf("rse-c", "rse-b", "rse-a"), QueueContext.Playlist(7))),
            playback.calls
        )
    }

    @Test
    fun `a tap and a long press play the playlist in its sort`() = runTest {
        playlists.setSort(7, byGame)

        viewModel().onTrackClick("rse-b")
        PlaylistsViewModel(playlists, FakeCatalogRepository(catalog), playback, UserMessages())
            .onPlayPlaylist(7)

        val ids = listOf("rse-a", "rse-b", "rse-c")
        assertEquals(
            listOf(
                PlayFrom(ids, 1, QueueContext.Playlist(7)),
                PlayAll(ids, QueueContext.Playlist(7))
            ),
            playback.calls
        )
    }
}
