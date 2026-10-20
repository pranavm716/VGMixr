package io.github.pranavm716.vgmixr.ui.games

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogImportResult
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.catalog.FamilySummary
import io.github.pranavm716.vgmixr.domain.catalog.ImportError
import io.github.pranavm716.vgmixr.domain.model.Category
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakeCatalogSync
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GamesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val rse = Family("rse", "Ruby / Sapphire / Emerald", "RSE")
    private val xy = Family("xy", "X / Y", "XY")

    private fun track(id: String, familyId: String) =
        CatalogTrack(id, familyId, emptyList(), id, Category.ROUTE, 0, emptyList())

    private val catalog = Catalog(
        games = emptyList(),
        families = listOf(rse, xy),
        tracks = listOf(track("rse-a", "rse"), track("rse-b", "rse"), track("xy-a", "xy"))
    )

    private val repository = FakeCatalogRepository(catalog)
    private val sync = FakeCatalogSync()
    private val playback = FakePlaybackController()
    private val messages = UserMessages()

    private fun viewModel() = GamesViewModel(repository, sync, playback, messages, main.dispatcher)

    private fun loaded(vararg families: FamilySummary, importFailed: Boolean = false) =
        GamesUiState.Loaded(families.toList(), importFailed)

    @Test
    fun `lists every family with its track count, in family order`() = runTest {
        viewModel().uiState.test {
            assertEquals(
                loaded(FamilySummary("rse", rse.name, 2), FamilySummary("xy", xy.name, 1)),
                awaitItem()
            )
        }
    }

    @Test
    fun `is loading until the catalog arrives`() = runTest {
        val pending = object : io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository {
            override val catalog = MutableSharedFlow<Catalog>()
        }
        GamesViewModel(pending, sync, playback, messages, main.dispatcher).uiState.test {
            assertEquals(GamesUiState.Loading, awaitItem())
            pending.catalog.emit(Catalog(emptyList(), emptyList(), emptyList()))
            assertEquals(loaded(), awaitItem())
        }
    }

    @Test
    fun `a catalog change shows up`() = runTest {
        viewModel().uiState.test {
            awaitItem()
            repository.catalog.value = catalog.copy(families = listOf(xy))

            assertEquals(loaded(FamilySummary("xy", xy.name, 1)), awaitItem())
        }
    }

    @Test
    fun `a failed import is flagged`() = runTest {
        sync.status.value = CatalogImportResult.Failed(listOf(ImportError.ManifestMissing))

        viewModel().uiState.test {
            assertEquals(true, (awaitItem() as GamesUiState.Loaded).importFailed)
        }
    }

    @Test
    fun `no import result yet, an unchanged and a successful import are not flagged`() = runTest {
        viewModel().uiState.test {
            assertEquals(false, (awaitItem() as GamesUiState.Loaded).importFailed)

            sync.status.value = CatalogImportResult.Unchanged
            expectNoEvents()

            sync.status.value = CatalogImportResult.Imported(Instant.EPOCH)
            expectNoEvents()
        }
    }

    @Test
    fun `swiping a family queues all its tracks in family order and says how many`() = runTest {
        messages.messages.test {
            viewModel().onQueueFamily("rse")

            assertEquals(
                listOf(FakePlaybackController.AddToQueue(listOf("rse-a", "rse-b"))),
                playback.calls
            )
            assertEquals(UserMessage.AddedToQueue(2), awaitItem())
        }
    }
}
