package io.github.pranavm716.vgmixr.ui.family

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.model.Category
import io.github.pranavm716.vgmixr.domain.model.Game
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FamilyViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val ruby = game("ruby", shortName = "R")
    private val sapphire = game("sapphire", shortName = "S")
    private val emerald = game("emerald", shortName = "E")
    private val x = game("x", familyId = "xy", shortName = "X")
    private val rse = listOf(ruby, sapphire, emerald)

    private fun track(id: String, games: List<Game>, order: Int, familyId: String = "rse") =
        CatalogTrack(id, familyId, games, "Title $id", Category.ROUTE, order, emptyList())

    private val catalog = Catalog(
        games = rse + x,
        families = listOf(
            Family("rse", "Ruby / Sapphire / Emerald", "RSE"),
            Family("xy", "X / Y", "XY")
        ),
        tracks = listOf(
            track("rse-c", listOf(emerald), order = 3),
            track("rse-a", rse, order = 1),
            track("rse-b", listOf(ruby, sapphire), order = 2),
            track("xy-a", listOf(x), order = 1, familyId = "xy")
        )
    )

    private val repository = FakeCatalogRepository(catalog)
    private val playback = FakePlaybackController()

    private fun viewModel(familyId: String) = FamilyViewModel(
        SavedStateHandle(mapOf("familyId" to familyId)),
        repository,
        playback,
        main.dispatcher
    )

    private fun chip(game: Game) = ChipUi(game.shortName, game.color)

    @Test
    fun `the header has the family's name and track count`() = runTest {
        viewModel("rse").uiState.test {
            val state = awaitItem() as FamilyUiState.Loaded
            assertEquals("Ruby / Sapphire / Emerald", state.name)
            assertEquals(3, state.trackCount)
        }
    }

    @Test
    fun `tracks are numbered from 1 in family order`() = runTest {
        viewModel("rse").uiState.test {
            val tracks = (awaitItem() as FamilyUiState.Loaded).tracks
            assertEquals(listOf("rse-a", "rse-b", "rse-c"), tracks.map { it.id })
            assertEquals(listOf(1, 2, 3), tracks.map { it.number })
            assertEquals("Title rse-a", tracks.first().title)
        }
    }

    @Test
    fun `a track in every game of the family has no chips`() = runTest {
        viewModel("rse").uiState.test {
            val shared = (awaitItem() as FamilyUiState.Loaded).tracks.first { it.id == "rse-a" }
            assertEquals(emptyList<ChipUi>(), shared.chips)
        }
    }

    @Test
    fun `a track in some of the games shows their chips in manifest order`() = runTest {
        viewModel("rse").uiState.test {
            val tracks = (awaitItem() as FamilyUiState.Loaded).tracks.associateBy { it.id }
            assertEquals(listOf(chip(ruby), chip(sapphire)), tracks.getValue("rse-b").chips)
            assertEquals(listOf(chip(emerald)), tracks.getValue("rse-c").chips)
        }
    }

    @Test
    fun `a single-game family shows no chips`() = runTest {
        viewModel("xy").uiState.test {
            assertEquals(
                emptyList<ChipUi>(),
                (awaitItem() as FamilyUiState.Loaded).tracks.single().chips
            )
        }
    }

    @Test
    fun `a family that is not in the catalog is not found`() = runTest {
        viewModel("dppt").uiState.test {
            assertEquals(FamilyUiState.NotFound, awaitItem())
        }
    }

    @Test
    fun `a catalog change shows up`() = runTest {
        viewModel("rse").uiState.test {
            awaitItem()
            repository.catalog.value = catalog.copy(tracks = catalog.tracks.drop(1))

            assertEquals(2, (awaitItem() as FamilyUiState.Loaded).trackCount)
        }
    }

    @Test
    fun `tapping a track plays it`() {
        viewModel("rse").onTrackClick("rse-b")

        assertEquals(listOf(FakePlaybackController.Play(listOf("rse-b"), 0)), playback.calls)
    }
}
