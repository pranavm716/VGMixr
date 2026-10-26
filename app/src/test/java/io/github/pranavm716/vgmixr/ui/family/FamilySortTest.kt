package io.github.pranavm716.vgmixr.ui.family

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playback.PlayQueue
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.QueueEntry
import io.github.pranavm716.vgmixr.domain.search.BrowsedFamily
import io.github.pranavm716.vgmixr.domain.sort.SortDirection
import io.github.pranavm716.vgmixr.domain.sort.SortKey
import io.github.pranavm716.vgmixr.domain.sort.SortKeys
import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakeCatalogSync
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakePlaybackController.PlayAll
import io.github.pranavm716.vgmixr.testing.FakePlaybackController.PlayFrom
import io.github.pranavm716.vgmixr.testing.FakePlaybackController.Resort
import io.github.pranavm716.vgmixr.testing.FakeSortSettings
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.components.SortUi
import io.github.pranavm716.vgmixr.ui.games.GamesViewModel
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FamilySortTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val ruby = game("ruby", shortName = "R")

    private fun track(id: String, title: String, order: Int) =
        CatalogTrack(id, "rse", listOf(ruby), title, order, emptyList())

    private val catalog = Catalog(
        games = listOf(ruby),
        families = listOf(Family("rse", "Ruby", "R")),
        tracks = listOf(
            track("rse-c", "Abandoned Ship", 3),
            track("rse-a", "Littleroot Town", 1),
            track("rse-b", "Battle! (Wild)", 2)
        )
    )
    private val repository = FakeCatalogRepository(catalog)
    private val playback = FakePlaybackController()
    private val sortSettings = FakeSortSettings()
    private val byTitle = SortSetting(SortKey.Title, SortDirection.Ascending)

    private fun viewModel() = FamilyViewModel(
        SavedStateHandle(mapOf("familyId" to "rse")),
        repository,
        playback,
        UserMessages(),
        BrowsedFamily(),
        sortSettings,
        main.dispatcher
    )

    private suspend fun FamilyViewModel.loaded(): FamilyUiState.Loaded {
        var state: FamilyUiState.Loaded? = null
        uiState.test { state = awaitItem() as FamilyUiState.Loaded }
        return checkNotNull(state)
    }

    @Test
    fun `the page starts in track order and offers order and title`() = runTest {
        val state = viewModel().loaded()

        assertEquals(listOf("rse-a", "rse-b", "rse-c"), state.tracks.map { it.id })
        assertEquals(SortUi(SortKeys.familyDefault, SortKeys.family), state.sort)
    }

    @Test
    fun `picking title sorts the page ascending and keeps each track's number`() = runTest {
        val viewModel = viewModel()

        viewModel.onSortKey(SortKey.Title)

        val state = viewModel.loaded()
        assertEquals(byTitle, sortSettings.games.value)
        assertEquals(listOf("rse-c", "rse-b", "rse-a"), state.tracks.map { it.id })
        assertEquals(listOf(3, 2, 1), state.tracks.map { it.number })
    }

    @Test
    fun `the arrow reverses the direction`() = runTest {
        val viewModel = viewModel()

        viewModel.onReverseSort()

        assertEquals(
            SortSetting(SortKey.Order, SortDirection.Descending),
            sortSettings.games.value
        )
        assertEquals(listOf("rse-c", "rse-b", "rse-a"), viewModel.loaded().tracks.map { it.id })
    }

    @Test
    fun `a sort change tells the player the family's new order`() = runTest {
        viewModel().onSortKey(SortKey.Title)

        assertEquals(
            listOf(Resort(listOf("rse-c", "rse-b", "rse-a"), QueueContext.Family("rse"))),
            playback.calls
        )
    }

    @Test
    fun `the shared sort re-sorts whichever family is playing`() = runTest {
        val xy = CatalogTrack("xy-a", "xy", listOf(ruby), "Zz", 1, emptyList())
        val xyB = CatalogTrack("xy-b", "xy", listOf(ruby), "Aa", 2, emptyList())
        repository.catalog.emit(
            catalog.copy(
                families = catalog.families + Family("xy", "X", "X"),
                tracks = catalog.tracks + xy + xyB
            )
        )
        playback.state.value = PlaybackState(
            queue = PlayQueue(listOf(QueueEntry("xy-a", QueueContext.Family("xy"), 0)), 0),
            trackId = "xy-a"
        )

        viewModel().onSortKey(SortKey.Title)

        assertEquals(
            listOf(Resort(listOf("xy-b", "xy-a"), QueueContext.Family("xy"))),
            playback.calls
        )
    }

    @Test
    fun `picking the key already selected changes nothing`() = runTest {
        viewModel().onSortKey(SortKey.Order)

        assertEquals(SortKeys.familyDefault, sortSettings.games.value)
        assertEquals(emptyList<Any>(), playback.calls)
    }

    @Test
    fun `a tap plays the family in its sort`() = runTest {
        sortSettings.setGames(byTitle)

        viewModel().onTrackClick("rse-b")

        assertEquals(
            listOf(PlayFrom(listOf("rse-c", "rse-b", "rse-a"), 1, QueueContext.Family("rse"))),
            playback.calls
        )
    }

    @Test
    fun `a long press on a family plays it in the family pages' sort`() = runTest {
        sortSettings.setGames(byTitle)

        GamesViewModel(
            repository,
            FakeCatalogSync(),
            playback,
            UserMessages(),
            sortSettings,
            main.dispatcher
        )
            .onPlayFamily("rse")

        assertEquals(
            listOf(PlayAll(listOf("rse-c", "rse-b", "rse-a"), QueueContext.Family("rse"))),
            playback.calls
        )
    }
}
