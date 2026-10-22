package io.github.pranavm716.vgmixr.ui.player

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.model.Category
import io.github.pranavm716.vgmixr.domain.playback.PlayQueue
import io.github.pranavm716.vgmixr.domain.playback.PlaybackProgress
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.QueueEntry
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MiniPlayerViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val ruby = game("ruby", shortName = "R")
    private val sapphire = game("sapphire", shortName = "S")

    private val catalog = Catalog(
        games = listOf(ruby, sapphire),
        families = listOf(Family("rse", "Ruby / Sapphire", "RS")),
        tracks = listOf(
            CatalogTrack(
                "rse-a",
                "rse",
                listOf(ruby, sapphire),
                "Route 101",
                Category.ROUTE,
                0,
                emptyList()
            ),
            CatalogTrack(
                "rse-b",
                "rse",
                listOf(ruby),
                "Battle! (Magma)",
                Category.BATTLE,
                1,
                emptyList()
            )
        )
    )

    private val playback = FakePlaybackController()
    private val viewModel by lazy { MiniPlayerViewModel(FakeCatalogRepository(catalog), playback) }

    private fun queue(vararg ids: String, current: Int) =
        PlayQueue(ids.map { QueueEntry(it, QueueContext.Family("rse")) }, current)

    private fun chip(shortName: String) = ChipUi(shortName, "#336699")

    private val route101 =
        MiniTrackUi("rse-a", "rse", "Route 101", listOf(chip("R"), chip("S")), Category.ROUTE)
    private val magma =
        MiniTrackUi("rse-b", "rse", "Battle! (Magma)", listOf(chip("R")), Category.BATTLE)

    @Test
    fun `hidden while nothing is loaded`() = runTest {
        viewModel.uiState.test {
            assertEquals(MiniPlayerUiState.Hidden, awaitItem())
        }
    }

    @Test
    fun `shows the current track with all its games' chips and its category, and the next one`() =
        runTest {
            playback.state.value = PlaybackState(
                queue = queue("rse-a", "rse-b", current = 0),
                trackId = "rse-a",
                isPlaying = true
            )

            viewModel.uiState.test {
                assertEquals(
                    MiniPlayerUiState.Visible(
                        track = route101,
                        previous = null,
                        next = magma,
                        isPlaying = true,
                        isLoading = false
                    ),
                    awaitItem()
                )
            }
        }

    @Test
    fun `a loading track says so, and the last one has a previous but no next`() = runTest {
        playback.state.value = PlaybackState(
            queue = queue("rse-a", "rse-b", current = 1),
            trackId = "rse-b",
            isLoading = true
        )

        viewModel.uiState.test {
            val state = awaitItem() as MiniPlayerUiState.Visible
            assertEquals(true, state.isLoading)
            assertEquals(route101, state.previous)
            assertEquals(null, state.next)
            assertEquals(magma, state.track)
        }
    }

    @Test
    fun `a track gone from the catalog hides the mini player`() = runTest {
        playback.state.value = PlaybackState(queue = queue("gone", current = 0), trackId = "gone")

        viewModel.uiState.test {
            assertEquals(MiniPlayerUiState.Hidden, awaitItem())
        }
    }

    @Test
    fun `progress is the share of the track played`() = runTest {
        viewModel.progress.test {
            assertEquals(0f, awaitItem())
            playback.progress.value = PlaybackProgress(positionMs = 30_000, durationMs = 120_000)
            assertEquals(0.25f, awaitItem())
            playback.progress.value = PlaybackProgress(positionMs = 5_000, durationMs = null)
            assertEquals(0f, awaitItem())
        }
    }

    @Test
    fun `the buttons and swipes reach the player`() {
        viewModel.onPlayPause()
        viewModel.onNext()
        viewModel.onPrevious()

        assertEquals(
            listOf(
                FakePlaybackController.TogglePlayPause,
                FakePlaybackController.SkipToNext,
                FakePlaybackController.SkipToPrevious
            ),
            playback.calls
        )
    }
}
