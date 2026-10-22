package io.github.pranavm716.vgmixr.ui.player

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
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

class NowPlayingViewModelTest {
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
                0,
                emptyList()
            )
        )
    )

    private val playback = FakePlaybackController()
    private val viewModel by lazy { NowPlayingViewModel(FakeCatalogRepository(catalog), playback) }

    private fun playing(context: QueueContext, isLoading: Boolean = false) = PlaybackState(
        queue = PlayQueue(listOf(QueueEntry("rse-a", context)), current = 0),
        trackId = "rse-a",
        isPlaying = !isLoading,
        isLoading = isLoading
    )

    @Test
    fun `shows the track, its games, and the family it plays from`() = runTest {
        playback.state.value = playing(QueueContext.Family("rse"))

        viewModel.uiState.test {
            assertEquals(
                NowPlayingUiState.Visible(
                    title = "Route 101",
                    chips = listOf(ChipUi("R", "#336699"), ChipUi("S", "#336699")),
                    playingFrom = PlayingFromUi.Family("rse", "Ruby / Sapphire"),
                    isPlaying = true,
                    isLoading = false
                ),
                awaitItem()
            )
        }
    }

    @Test
    fun `a swiped-in track plays from the queue, and loading shows`() = runTest {
        playback.state.value = playing(QueueContext.Queue, isLoading = true)

        viewModel.uiState.test {
            val state = awaitItem() as NowPlayingUiState.Visible
            assertEquals(PlayingFromUi.Queue, state.playingFrom)
            assertEquals(true, state.isLoading)
        }
    }

    @Test
    fun `hidden while nothing plays`() = runTest {
        viewModel.uiState.test {
            assertEquals(NowPlayingUiState.Hidden, awaitItem())
        }
    }

    @Test
    fun `the seek bar follows playback`() = runTest {
        viewModel.progress.test {
            assertEquals(PlaybackProgress(0, null), awaitItem())
            playback.progress.value = PlaybackProgress(30_000, 76_000)
            assertEquals(PlaybackProgress(30_000, 76_000), awaitItem())
        }
    }

    @Test
    fun `the controls and the seek bar reach the player`() {
        viewModel.onPlayPause()
        viewModel.onPrevious()
        viewModel.onNext()
        viewModel.onSeek(42_000)

        assertEquals(
            listOf(
                FakePlaybackController.TogglePlayPause,
                FakePlaybackController.RestartOrSkipToPrevious,
                FakePlaybackController.SkipToNext,
                FakePlaybackController.SeekTo(42_000)
            ),
            playback.calls
        )
    }

    @Test
    fun `previous restarts a track that has played a while, as the notification's button does`() {
        viewModel.onPrevious()

        assertEquals(listOf(FakePlaybackController.RestartOrSkipToPrevious), playback.calls)
    }

    @Test
    fun `times read as minutes and seconds, with hours only when needed`() {
        assertEquals("0:07", formatTime(7_400))
        assertEquals("1:49", formatTime(109_000))
        assertEquals("1:02:03", formatTime(3_723_000))
        assertEquals("0:00", formatTime(-5))
    }
}
