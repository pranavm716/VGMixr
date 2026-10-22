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

    private val catalog = Catalog(
        games = listOf(or),
        families = listOf(Family("oras", "Omega Ruby", "OR")),
        tracks = listOf(
            CatalogTrack(
                "oras-a",
                "oras",
                listOf(or),
                "Battle! (Elite Four)",
                1,
                emptyList(),
                BrokenReason.NO_AUDIO
            ),
            CatalogTrack("oras-b", "oras", listOf(or), "Champion Steven", 2, emptyList())
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

    @Test
    fun `a track whose sources are all broken is greyed in the list`() = runTest {
        viewModel.uiState.test {
            val rows = (awaitItem() as FamilyUiState.Loaded).tracks

            assertEquals(listOf(true, false), rows.map { it.unplayable })
        }
    }

    @Test
    fun `tapping it says why it can't play, every time, and plays nothing`() = runTest {
        messages.messages.test {
            viewModel.onTrackClick("oras-a")
            assertEquals(
                UserMessage.TrackUnplayable(
                    "oras-a",
                    "Battle! (Elite Four)",
                    BrokenReason.NO_AUDIO
                ),
                awaitItem()
            )
            viewModel.onTrackClick("oras-a")
            assertEquals(
                UserMessage.TrackUnplayable(
                    "oras-a",
                    "Battle! (Elite Four)",
                    BrokenReason.NO_AUDIO
                ),
                awaitItem()
            )
        }
        assertEquals(emptyList<Any>(), playback.calls)
    }

    @Test
    fun `the tracks around it still play from the family list, which keeps it`() {
        viewModel.onTrackClick("oras-b")

        assertEquals(
            listOf(
                FakePlaybackController.PlayFrom(
                    listOf("oras-a", "oras-b"),
                    1,
                    QueueContext.Family("oras")
                )
            ),
            playback.calls
        )
    }
}
