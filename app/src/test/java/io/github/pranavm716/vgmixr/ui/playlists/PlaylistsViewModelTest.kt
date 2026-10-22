package io.github.pranavm716.vgmixr.ui.playlists

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaylistsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = object : PlaylistRepository {
        override val playlists = MutableSharedFlow<List<PlaylistSummary>>(replay = 1)
    }

    private val favorites = PlaylistSummary(1, "Favorites", 3, isFavorites = true)
    private val mix = PlaylistSummary(2, "Mix", 0, isFavorites = false)

    @Test
    fun `is loading until the playlists arrive`() = runTest {
        PlaylistsViewModel(repository).uiState.test {
            assertEquals(PlaylistsUiState.Loading, awaitItem())
            repository.playlists.emit(listOf(favorites))
            assertEquals(PlaylistsUiState.Loaded(listOf(favorites)), awaitItem())
        }
    }

    @Test
    fun `lists the playlists in the repository's order`() = runTest {
        repository.playlists.emit(listOf(favorites, mix))

        PlaylistsViewModel(repository).uiState.test {
            assertEquals(PlaylistsUiState.Loaded(listOf(favorites, mix)), awaitItem())
        }
    }
}
