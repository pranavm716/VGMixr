package io.github.pranavm716.vgmixr.ui.games

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakeCatalogSync
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakeSortSettings
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QueueFamilySkipsUnplayableTest {
    private val sortSettings = FakeSortSettings()

    @get:Rule
    val main = MainDispatcherRule()

    private val or = game("or", familyId = "oras", shortName = "OR")

    private fun track(id: String, order: Int, unplayable: BrokenReason? = null) =
        CatalogTrack(id, "oras", listOf(or), "Title $id", order, emptyList(), unplayable)

    private fun catalog(vararg tracks: CatalogTrack) = Catalog(
        games = listOf(or),
        families = listOf(Family("oras", "Omega Ruby", "OR")),
        tracks = tracks.toList()
    )

    private val playback = FakePlaybackController()
    private val messages = UserMessages()

    private fun viewModel(catalog: Catalog) = GamesViewModel(
        FakeCatalogRepository(catalog),
        FakeCatalogSync(),
        playback,
        messages,
        sortSettings,
        main.dispatcher
    )

    @Test
    fun `queueing a family leaves out its greyed tracks and counts only the rest`() = runTest {
        val viewModel = viewModel(
            catalog(
                track("oras-a", 1),
                track("oras-b", 2, BrokenReason.NO_AUDIO),
                track("oras-c", 3)
            )
        )

        messages.messages.test {
            viewModel.onQueueFamily("oras")

            assertEquals(UserMessage.AddedToQueue(2), awaitItem())
        }
        assertEquals(
            listOf(FakePlaybackController.AddToQueue(listOf("oras-a", "oras-c"))),
            playback.calls
        )
    }

    @Test
    fun `a long press plays only the playable tracks, and nothing when none can play`() = runTest {
        viewModel(
            catalog(
                track("oras-a", 1),
                track("oras-b", 2, BrokenReason.UNAVAILABLE),
                track("oras-c", 3)
            )
        ).onPlayFamily("oras")
        viewModel(catalog(track("oras-b", 1, BrokenReason.UNAVAILABLE))).onPlayFamily("oras")

        assertEquals(
            listOf(
                FakePlaybackController.PlayAll(
                    listOf("oras-a", "oras-c"),
                    QueueContext.Family("oras")
                )
            ),
            playback.calls
        )
    }

    @Test
    fun `a family with nothing playable adds nothing`() = runTest {
        val viewModel = viewModel(catalog(track("oras-b", 1, BrokenReason.UNAVAILABLE)))

        messages.messages.test {
            viewModel.onQueueFamily("oras")

            expectNoEvents()
        }
        assertEquals(emptyList<Any>(), playback.calls)
    }
}
