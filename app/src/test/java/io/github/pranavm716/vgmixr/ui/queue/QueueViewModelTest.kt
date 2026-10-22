package io.github.pranavm716.vgmixr.ui.queue

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.model.Category
import io.github.pranavm716.vgmixr.domain.playback.PlayQueue
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.QueueEntry
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QueueViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val ruby = game("ruby", shortName = "R")

    private fun track(id: String) =
        CatalogTrack(id, "rse", listOf(ruby), "Title $id", Category.ROUTE, 0, emptyList())

    private val catalog = Catalog(
        games = listOf(ruby),
        families = listOf(Family("rse", "Ruby / Sapphire / Emerald", "RSE")),
        tracks = listOf("h", "c", "q1", "q2", "x", "y").map(::track)
    )

    private val rse = QueueContext.Family("rse")
    private val playback = FakePlaybackController()
    private val messages = UserMessages()
    private val viewModel by lazy {
        QueueViewModel(FakeCatalogRepository(catalog), playback, messages)
    }

    private fun row(index: Int, id: String) =
        QueueRowUi(index, id, "Title $id", listOf(ChipUi("R", "#336699")), Category.ROUTE)

    private fun state(entries: List<QueueEntry>, current: Int) = PlaybackState(
        queue = PlayQueue(entries, current),
        trackId = entries[current].trackId,
        isPlaying = true
    )

    @Test
    fun `now playing, then the queue, then the rest of the family, with played tracks hidden`() =
        runTest {
            playback.state.value = state(
                listOf(
                    QueueEntry("h", rse),
                    QueueEntry("c", rse),
                    QueueEntry("q1", QueueContext.Queue),
                    QueueEntry("q2", QueueContext.Queue),
                    QueueEntry("x", rse),
                    QueueEntry("y", rse)
                ),
                current = 1
            )

            viewModel.uiState.test {
                assertEquals(
                    QueueUiState(
                        nowPlaying = row(1, "c"),
                        upNext = listOf(row(2, "q1"), row(3, "q2")),
                        nextFromName = "Ruby / Sapphire / Emerald",
                        nextFrom = listOf(row(4, "x"), row(5, "y"))
                    ),
                    awaitItem()
                )
            }
        }

    @Test
    fun `with nothing queued, the queue section is empty`() = runTest {
        playback.state.value = state(listOf(QueueEntry("c", rse), QueueEntry("x", rse)), 0)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList<QueueRowUi>(), state.upNext)
            assertEquals(listOf(row(1, "x")), state.nextFrom)
        }
    }

    @Test
    fun `nothing playing, nothing listed`() = runTest {
        viewModel.uiState.test {
            assertEquals(QueueUiState.EMPTY, awaitItem())
        }
    }

    @Test
    fun `taps, swipes and the clear button reach the player`() {
        viewModel.onJump(4)
        viewModel.onRemove(2)
        viewModel.onClear()

        assertEquals(
            listOf(
                FakePlaybackController.JumpTo(4),
                FakePlaybackController.RemoveFromQueue(2),
                FakePlaybackController.ClearQueue
            ),
            playback.calls
        )
    }

    @Test
    fun `swiping a track of the family right adds it to the queue and says so`() = runTest {
        playback.state.value = state(listOf(QueueEntry("c", rse), QueueEntry("x", rse)), 0)

        viewModel.uiState.test {
            awaitItem()
            messages.messages.test {
                viewModel.onQueue(1)

                assertEquals(UserMessage.AddedToQueue(1), awaitItem())
            }
            assertEquals(listOf(FakePlaybackController.AddToQueue(listOf("x"))), playback.calls)
        }
    }
}
