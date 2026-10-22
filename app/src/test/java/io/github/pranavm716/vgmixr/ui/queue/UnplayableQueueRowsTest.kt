package io.github.pranavm716.vgmixr.ui.queue

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.PlayQueue
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.QueueEntry
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UnplayableQueueRowsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val or = game("or", familyId = "oras", shortName = "OR")
    private val oras = QueueContext.Family("oras")

    private val catalog = Catalog(
        games = listOf(or),
        families = listOf(Family("oras", "Omega Ruby", "OR")),
        tracks = listOf(
            CatalogTrack("oras-a", "oras", listOf(or), "Hall of Fame", 1, emptyList()),
            CatalogTrack(
                "oras-b",
                "oras",
                listOf(or),
                "Battle! (Steven)",
                2,
                emptyList(),
                BrokenReason.UNAVAILABLE
            )
        )
    )

    private val playback = FakePlaybackController().apply {
        state.value = PlaybackState(
            queue = PlayQueue(listOf(QueueEntry("oras-a", oras), QueueEntry("oras-b", oras)), 0),
            trackId = "oras-a"
        )
    }
    private val messages = UserMessages()
    private val viewModel by lazy {
        QueueViewModel(FakeCatalogRepository(catalog), playback, messages)
    }

    @Test
    fun `a queued track whose sources are all broken is greyed`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem().takeIf { it.nowPlaying != null } ?: awaitItem()

            assertEquals(false, state.nowPlaying?.unplayable)
            assertEquals(listOf(true), state.nextFrom.map { it.unplayable })
        }
    }

    @Test
    fun `a greyed track in the queue can't be queued again`() {
        viewModel.onQueue(1)

        assertEquals(emptyList<Any>(), playback.calls)
    }

    @Test
    fun `tapping it says why it can't play instead of jumping to it`() = runTest {
        messages.messages.test {
            viewModel.onJump(1)

            assertEquals(
                UserMessage.TrackUnplayable("oras-b", "Battle! (Steven)", BrokenReason.UNAVAILABLE),
                awaitItem()
            )
        }
        assertEquals(emptyList<Any>(), playback.calls)
    }
}
