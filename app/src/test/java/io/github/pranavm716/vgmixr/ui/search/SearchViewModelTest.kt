package io.github.pranavm716.vgmixr.ui.search

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.history.Play
import io.github.pranavm716.vgmixr.domain.model.Game
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.search.BrowsedFamily
import io.github.pranavm716.vgmixr.domain.search.SearchRanking
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlayHistory
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakePlaylistRepository
import io.github.pranavm716.vgmixr.testing.FakeSortSettings
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {
    private val sortSettings = FakeSortSettings()

    @get:Rule
    val main = MainDispatcherRule()

    private val ruby = game("ruby", shortName = "R")
    private val sapphire = game("sapphire", shortName = "S")
    private val omegaRuby = game("omega-ruby", familyId = "oras", shortName = "OR", generation = 6)
    private val alphaSapphire =
        game("alpha-sapphire", familyId = "oras", shortName = "AS", generation = 6)

    private fun track(
        id: String,
        games: List<Game>,
        title: String,
        order: Int,
        unplayable: BrokenReason? = null
    ) = CatalogTrack(id, games.first().familyId, games, title, order, emptyList(), unplayable)

    private val catalog = Catalog(
        games = listOf(ruby, sapphire, omegaRuby, alphaSapphire),
        families = listOf(
            Family("rse", "Ruby / Sapphire", "RS"),
            Family("oras", "Omega Ruby / Alpha Sapphire", "ORAS")
        ),
        tracks = listOf(
            track("rse-littleroot", listOf(ruby, sapphire), "Littleroot Town", 1),
            track("rse-route-101", listOf(ruby, sapphire), "Route 101", 2),
            track("rse-dead", listOf(ruby), "Route 102", 3, unplayable = BrokenReason.NO_AUDIO),
            track("rse-route-103", listOf(ruby, sapphire), "Route 103", 4),
            track("oras-route-101", listOf(omegaRuby, alphaSapphire), "Route 101", 1)
        )
    )

    private val playback = FakePlaybackController()
    private val messages = UserMessages()
    private val catalogRepository = FakeCatalogRepository(catalog)
    private val history = FakePlayHistory()

    // Built once the main dispatcher rule has started, since viewModelScope uses it.
    private val viewModel by lazy {
        SearchViewModel(
            catalogRepository,
            SearchRanking(
                history,
                FakePlaylistRepository(),
                playback,
                catalogRepository,
                BrowsedFamily()
            ),
            playback,
            messages,
            TestCatalog.clock,
            sortSettings,
            main.dispatcher
        )
    }

    private fun chip(game: Game) = ChipUi(game.shortName, game.color)

    @Test
    fun `an empty field shows nothing`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList<SearchRowUi>(), state.results)
            assertFalse(state.noResults)
        }
    }

    @Test
    fun `typing shows the matches, best first, with all their game chips`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("route 101")

            assertEquals(
                listOf(
                    SearchRowUi(
                        "rse-route-101",
                        "Route 101",
                        listOf(chip(ruby), chip(sapphire)),
                        familyId = "rse"
                    ),
                    SearchRowUi(
                        "oras-route-101",
                        "Route 101",
                        listOf(chip(omegaRuby), chip(alphaSapphire)),
                        familyId = "oras"
                    )
                ),
                awaitItem().results
            )
        }
    }

    @Test
    fun `a track that was played comes before a same-named one that wasn't`() = runTest {
        history.plays.value = listOf(Play("oras-route-101", TestCatalog.clock.instant()))
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("route 101")

            assertEquals(
                listOf("oras-route-101", "rse-route-101"),
                awaitItem().results.map {
                    it.id
                }
            )
        }
    }

    @Test
    fun `a play doesn't reorder the results on screen, the next search uses it`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("route 101")
            assertEquals(
                listOf("rse-route-101", "oras-route-101"),
                awaitItem().results.map {
                    it.id
                }
            )

            history.plays.value = listOf(Play("oras-route-101", TestCatalog.clock.instant()))
            runCurrent()
            expectNoEvents()

            viewModel.onQueryChange("route 101 ")
            assertEquals(
                listOf("oras-route-101", "rse-route-101"),
                awaitItem().results.map {
                    it.id
                }
            )
        }
    }

    @Test
    fun `a greyed track is found, greyed`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("route 102")

            assertEquals(listOf(true), awaitItem().results.map { it.unplayable })
        }
    }

    @Test
    fun `a query that matches nothing says so`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("zzzz")

            val state = awaitItem()
            assertEquals(emptyList<SearchRowUi>(), state.results)
            assertTrue(state.noResults)
        }
    }

    @Test
    fun `clearing the field shows nothing again`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("route")
            awaitItem()
            viewModel.onQueryChange("")

            val state = awaitItem()
            assertEquals(emptyList<SearchRowUi>(), state.results)
            assertFalse(state.noResults)
        }
    }

    @Test
    fun `results wait until typing pauses`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("r")
            advanceTimeBy(100)
            viewModel.onQueryChange("ro")
            advanceTimeBy(SEARCH_DELAY_MS - 1)
            runCurrent()
            expectNoEvents()

            advanceTimeBy(1)
            runCurrent()

            assertEquals(4, expectMostRecentItem().results.size)
        }
    }

    @Test
    fun `clearing the field empties the results at once`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("route")
            awaitItem()

            viewModel.onQueryChange("")
            runCurrent()

            assertEquals(emptyList<SearchRowUi>(), expectMostRecentItem().results)
        }
    }

    @Test
    fun `leaving the tab clears the search`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("route")
            awaitItem()
            viewModel.onLeave()

            assertEquals(emptyList<SearchRowUi>(), awaitItem().results)
        }
    }

    @Test
    fun `the playing track is highlighted`() = runTest {
        playback.state.value = PlaybackState(trackId = "oras-route-101")
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("route 101")

            assertEquals(
                listOf("oras-route-101"),
                awaitItem().results.filter { it.playing }.map { it.id }
            )
        }
    }

    @Test
    fun `a tap plays the track's family from it, without its greyed tracks`() = runTest {
        viewModel.onTrackClick("rse-route-103")

        assertEquals(
            listOf(
                FakePlaybackController.PlayFrom(
                    listOf("rse-littleroot", "rse-route-101", "rse-route-103"),
                    2,
                    QueueContext.Family("rse")
                )
            ),
            playback.calls
        )
    }

    @Test
    fun `a tap on a greyed track says it can't play`() = runTest {
        messages.messages.test {
            viewModel.onTrackClick("rse-dead")

            assertEquals(
                UserMessage.TrackUnplayable("rse-dead", "Route 102", BrokenReason.NO_AUDIO),
                awaitItem()
            )
        }
        assertEquals(emptyList<Any>(), playback.calls)
    }

    @Test
    fun `a swipe adds the track to the queue`() = runTest {
        messages.messages.test {
            viewModel.onQueueTrack("rse-route-101")

            assertEquals(UserMessage.AddedToQueue(1), awaitItem())
        }
        assertEquals(
            listOf(FakePlaybackController.AddToQueue(listOf("rse-route-101"))),
            playback.calls
        )
    }

    @Test
    fun `a greyed track can't be queued`() = runTest {
        viewModel.onQueueTrack("rse-dead")

        assertEquals(emptyList<Any>(), playback.calls)
    }

    private val remixes = listOf(
        CatalogTrack(
            "yt:AAAAAAAAAAA",
            "rse",
            listOf(ruby, sapphire),
            "Route 101",
            0,
            emptyList(),
            channel = "GlitchxCity",
            remixOf = "rse-route-101",
            thumbnail = "thumbnails/AAAAAAAAAAA.jpg",
            videoTitle = "Route 101 [Remix]"
        ),
        CatalogTrack(
            "yt:BBBBBBBBBBB",
            null,
            emptyList(),
            "Some Remix",
            0,
            emptyList(),
            channel = "GlitchxCity",
            videoTitle = "Some Remix"
        )
    )

    @Test
    fun `a remix is found, with its thumbnail and its channel`() = runTest {
        catalogRepository.catalog.value = catalog.copy(userTracks = remixes)
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("route 101 remix")

            val row = awaitItem().results.single()
            assertEquals("yt:AAAAAAAAAAA", row.id)
            assertEquals("thumbnails/AAAAAAAAAAA.jpg", row.thumbnail)
            assertEquals("GlitchxCity", row.subtitle)
        }
    }

    @Test
    fun `a tap on a remix plays Remixes from it`() = runTest {
        catalogRepository.catalog.value = catalog.copy(userTracks = remixes)

        viewModel.onTrackClick("yt:BBBBBBBBBBB")

        assertEquals(
            listOf(
                FakePlaybackController.PlayFrom(
                    listOf("yt:AAAAAAAAAAA", "yt:BBBBBBBBBBB"),
                    1,
                    QueueContext.Remixes
                )
            ),
            playback.calls
        )
    }
}
