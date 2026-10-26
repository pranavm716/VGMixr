package io.github.pranavm716.vgmixr.ui.family

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.model.Game
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.search.BrowsedFamily
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakeSortSettings
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FamilyViewModelTest {
    private val sortSettings = FakeSortSettings()

    @get:Rule
    val main = MainDispatcherRule()

    private val ruby = game("ruby", shortName = "R")
    private val sapphire = game("sapphire", shortName = "S")
    private val emerald = game("emerald", shortName = "E")
    private val x = game("x", familyId = "xy", shortName = "X")
    private val rse = listOf(ruby, sapphire, emerald)

    private fun track(id: String, games: List<Game>, order: Int, familyId: String = "rse") =
        CatalogTrack(id, familyId, games, "Title $id", order, emptyList())

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
    private val messages = UserMessages()
    private val browsed = BrowsedFamily()

    private fun viewModel(familyId: String) = FamilyViewModel(
        SavedStateHandle(mapOf("familyId" to familyId)),
        repository,
        playback,
        messages,
        browsed,
        sortSettings,
        main.dispatcher
    )

    private fun chip(game: Game) = ChipUi(game.shortName, game.color)

    @Test
    fun `opening a family's page makes it the last browsed family`() {
        viewModel("rse")

        assertEquals("rse", browsed.familyId.value)
    }

    @Test
    fun `the header has the family's name and track count`() = runTest {
        viewModel("rse").uiState.test {
            val state = awaitItem() as FamilyUiState.Loaded
            assertEquals("Ruby / Sapphire / Emerald", state.name)
            assertEquals(3, state.trackCount)
        }
    }

    @Test
    fun `the header carries the family's id for the cover`() = runTest {
        viewModel("rse").uiState.test {
            assertEquals("rse", (awaitItem() as FamilyUiState.Loaded).familyId)
        }
    }

    @Test
    fun `the header is tinted with the family's games in manifest order`() = runTest {
        viewModel("rse").uiState.test {
            assertEquals(
                listOf(chip(ruby), chip(sapphire), chip(emerald)),
                (awaitItem() as FamilyUiState.Loaded).games
            )
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
    fun `tapping a track plays the family's list from it`() {
        viewModel("rse").onTrackClick("rse-b")

        assertEquals(
            listOf(
                FakePlaybackController.PlayFrom(
                    listOf("rse-a", "rse-b", "rse-c"),
                    1,
                    QueueContext.Family("rse")
                )
            ),
            playback.calls
        )
    }

    @Test
    fun `the playing track of this family is marked, so the list can show and scroll to it`() =
        runTest {
            playback.state.value = PlaybackState(trackId = "rse-b", isPlaying = true)

            viewModel("rse").uiState.test {
                assertEquals(
                    PlayingRowUi("rse-b", index = 1, loading = false),
                    (awaitItem() as FamilyUiState.Loaded).playing
                )
            }
        }

    @Test
    fun `a playing track that is still loading says so`() = runTest {
        playback.state.value = PlaybackState(trackId = "rse-c", isLoading = true)

        viewModel("rse").uiState.test {
            assertEquals(
                PlayingRowUi("rse-c", index = 2, loading = true),
                (awaitItem() as FamilyUiState.Loaded).playing
            )
        }
    }

    @Test
    fun `a track of another family marks nothing here`() = runTest {
        playback.state.value = PlaybackState(trackId = "xy-a", isPlaying = true)

        viewModel("rse").uiState.test {
            assertEquals(null, (awaitItem() as FamilyUiState.Loaded).playing)
        }
    }

    @Test
    fun `the mark follows playback`() = runTest {
        viewModel("rse").uiState.test {
            assertEquals(null, (awaitItem() as FamilyUiState.Loaded).playing)
            playback.state.value = PlaybackState(trackId = "rse-a", isLoading = true)

            assertEquals(
                PlayingRowUi("rse-a", index = 0, loading = true),
                (awaitItem() as FamilyUiState.Loaded).playing
            )
        }
    }

    @Test
    fun `swiping a track queues it and says so`() = runTest {
        messages.messages.test {
            viewModel("rse").onQueueTrack("rse-c")

            assertEquals(listOf(FakePlaybackController.AddToQueue(listOf("rse-c"))), playback.calls)
            assertEquals(UserMessage.AddedToQueue(1), awaitItem())
        }
    }
}
