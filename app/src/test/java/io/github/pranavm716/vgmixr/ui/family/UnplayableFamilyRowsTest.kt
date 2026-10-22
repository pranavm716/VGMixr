package io.github.pranavm716.vgmixr.ui.family

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UnplayableFamilyRowsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val or = game("or", familyId = "oras", shortName = "OR")

    private fun track(id: String, title: String, order: Int, unplayable: BrokenReason? = null) =
        CatalogTrack(id, "oras", listOf(or), title, order, emptyList(), unplayable)

    private val catalog = Catalog(
        games = listOf(or),
        families = listOf(Family("oras", "Omega Ruby", "OR")),
        tracks = listOf(
            track("oras-a", "The Elite Four Appear!", 1),
            track("oras-b", "Battle! (Elite Four)", 2, BrokenReason.NO_AUDIO),
            track("oras-c", "Champion Steven", 3)
        )
    )

    private val playback = FakePlaybackController()
    private val messages = UserMessages()
    private val viewModel by lazy {
        FamilyViewModel(
            SavedStateHandle(mapOf("familyId" to "oras")),
            FakeCatalogRepository(catalog),
            playback,
            messages,
            main.dispatcher
        )
    }

    private val eliteFour =
        UserMessage.TrackUnplayable("oras-b", "Battle! (Elite Four)", BrokenReason.NO_AUDIO)

    @Test
    fun `a track whose sources are all broken is greyed in the list`() = runTest {
        viewModel.uiState.test {
            val rows = (awaitItem() as FamilyUiState.Loaded).tracks

            assertEquals(listOf(false, true, false), rows.map { it.unplayable })
        }
    }

    @Test
    fun `tapping it says why it can't play, every time, and plays nothing`() = runTest {
        messages.messages.test {
            viewModel.onTrackClick("oras-b")
            assertEquals(eliteFour, awaitItem())
            viewModel.onTrackClick("oras-b")
            assertEquals(eliteFour, awaitItem())
        }
        assertEquals(emptyList<Any>(), playback.calls)
    }

    @Test
    fun `tapping another track plays the family list without the greyed tracks`() {
        viewModel.onTrackClick("oras-c")

        assertEquals(
            listOf(
                FakePlaybackController.PlayFrom(
                    listOf("oras-a", "oras-c"),
                    1,
                    QueueContext.Family("oras")
                )
            ),
            playback.calls
        )
    }

    @Test
    fun `a greyed track can't be queued`() {
        viewModel.onQueueTrack("oras-b")

        assertEquals(emptyList<Any>(), playback.calls)
    }
}
