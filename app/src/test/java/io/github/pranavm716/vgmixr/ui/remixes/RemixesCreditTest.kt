package io.github.pranavm716.vgmixr.ui.remixes

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrack
import io.github.pranavm716.vgmixr.domain.usertrack.toCatalogTrack
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakeSortSettings
import io.github.pranavm716.vgmixr.testing.FakeUserTrackRepository
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Credit mode on Remixes: the pencil rows, the dialog, and saving a credit. */
class RemixesCreditTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun track(videoId: String, title: String, channel: String, credit: String? = null) =
        UserTrack(
            videoId = videoId,
            title = title,
            channel = channel,
            durationMs = null,
            addedAt = Instant.ofEpochSecond(videoId.first().code.toLong()),
            credit = credit
        )

    private val brawlRip =
        track("AAAAAAAAAAA", "Galactic Battle", "pkmnrips4u", credit = "Super Smash Bros. Brawl")
    private val lofi = track("BBBBBBBBBBB", "Route 209 Lofi", "GlitchxCity")
    private val orchestral = track("CCCCCCCCCCC", "Vs. N Orchestral", "Braxton Burks")
    private val userTracks = FakeUserTrackRepository(listOf(brawlRip, lofi, orchestral))

    private val empty = Catalog(games = emptyList(), families = emptyList(), tracks = emptyList())
    private val catalog = empty.copy(
        userTracks = listOf(orchestral, lofi, brawlRip).map {
            it.toCatalogTrack(empty, unplayable = null)
        }
    )

    private fun viewModel() = RemixesViewModel(
        FakeCatalogRepository(catalog),
        FakePlaybackController(),
        UserMessages(),
        userTracks,
        FakeSortSettings(),
        main.dispatcher
    )

    private suspend fun stored(id: String) = userTracks.userTracks.first().single { it.id == id }

    @Test
    fun `a credited row shows its credit`() = runTest {
        viewModel().uiState.test {
            val rows = (awaitItem() as RemixesUiState.Loaded).rows
            assertEquals("Super Smash Bros. Brawl", rows.single { it.id == brawlRip.id }.channel)
        }
    }

    @Test
    fun `the menu's Credit turns credit mode on, and Done turns it off`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            assertEquals(RemixesMode.NORMAL, (awaitItem() as RemixesUiState.Loaded).mode)
            viewModel.onStartCrediting()
            assertEquals(RemixesMode.CREDIT, (awaitItem() as RemixesUiState.Loaded).mode)
            viewModel.onDone()
            assertEquals(RemixesMode.NORMAL, (awaitItem() as RemixesUiState.Loaded).mode)
        }
    }

    @Test
    fun `tapping a row opens its dialog with its channel, the other names, and what shows now`() =
        runTest {
            val viewModel = viewModel()
            viewModel.creditDialog.test {
                assertEquals(null, awaitItem())
                viewModel.onOpenCredit(lofi.id)
                assertEquals(
                    CreditDialogUi(
                        trackId = lofi.id,
                        title = "Route 209 Lofi",
                        channel = "GlitchxCity",
                        choices = listOf("Braxton Burks", "Super Smash Bros. Brawl"),
                        selected = null
                    ),
                    awaitItem()
                )
            }
        }

    @Test
    fun `a credited track's dialog marks its credit`() = runTest {
        val viewModel = viewModel()
        viewModel.creditDialog.test {
            awaitItem()
            viewModel.onOpenCredit(brawlRip.id)
            assertEquals("Super Smash Bros. Brawl", awaitItem()?.selected)
        }
    }

    @Test
    fun `picking a name saves it and closes the dialog, staying in credit mode`() = runTest {
        val viewModel = viewModel()
        viewModel.onStartCrediting()
        viewModel.creditDialog.test {
            awaitItem()
            viewModel.onOpenCredit(lofi.id)
            awaitItem()
            viewModel.onPickCredit("Braxton Burks")
            assertEquals(null, expectMostRecentItem())
        }
        assertEquals("Braxton Burks", stored(lofi.id).credit)
        val loaded = viewModel.uiState.first {
            it is RemixesUiState.Loaded
        } as RemixesUiState.Loaded
        assertEquals(RemixesMode.CREDIT, loaded.mode)
    }

    @Test
    fun `picking the channel clears the credit`() = runTest {
        val viewModel = viewModel()
        viewModel.creditDialog.test {
            awaitItem()
            viewModel.onOpenCredit(brawlRip.id)
            awaitItem()
            viewModel.onPickCredit(null)
            assertEquals(null, expectMostRecentItem())
        }
        assertEquals(null, stored(brawlRip.id).credit)
    }

    @Test
    fun `a new name in another case takes the existing spelling`() = runTest {
        val viewModel = viewModel()
        viewModel.creditDialog.test {
            awaitItem()
            viewModel.onOpenCredit(lofi.id)
            awaitItem()
            viewModel.onAddCredit("  super smash bros. brawl ")
            assertEquals(null, expectMostRecentItem())
        }
        assertEquals("Super Smash Bros. Brawl", stored(lofi.id).credit)
    }

    @Test
    fun `a blank new name saves nothing and keeps the dialog open`() = runTest {
        val viewModel = viewModel()
        viewModel.creditDialog.test {
            awaitItem()
            viewModel.onOpenCredit(brawlRip.id)
            val open = awaitItem()
            viewModel.onAddCredit("   ")
            advanceUntilIdle()
            expectNoEvents()
            assertEquals(open, viewModel.creditDialog.value)
        }
        assertEquals("Super Smash Bros. Brawl", stored(brawlRip.id).credit)
    }

    @Test
    fun `dismissing closes the dialog and saves nothing`() = runTest {
        val viewModel = viewModel()
        viewModel.creditDialog.test {
            awaitItem()
            viewModel.onOpenCredit(brawlRip.id)
            awaitItem()
            viewModel.onDismissCredit()
            assertEquals(null, awaitItem())
        }
        assertEquals("Super Smash Bros. Brawl", stored(brawlRip.id).credit)
    }
}
