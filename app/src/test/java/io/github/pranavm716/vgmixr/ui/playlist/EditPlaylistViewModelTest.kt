package io.github.pranavm716.vgmixr.ui.playlist

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaylistRepository
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditPlaylistViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val ruby = game("ruby", shortName = "R")
    private val sapphire = game("sapphire", shortName = "S")

    private val catalog = Catalog(
        games = listOf(ruby, sapphire),
        families = listOf(Family("rse", "Ruby / Sapphire", "RS")),
        tracks = listOf(
            CatalogTrack(
                "rse-route-101",
                "rse",
                listOf(ruby, sapphire),
                "Route 101",
                1,
                emptyList()
            ),
            CatalogTrack("rse-route-102", "rse", listOf(ruby), "Route 102", 2, emptyList()),
            CatalogTrack(
                "rse-littleroot",
                "rse",
                listOf(ruby, sapphire),
                "Littleroot Town",
                3,
                emptyList()
            )
        )
    )

    private val playlists = FakePlaylistRepository().apply {
        contents.value = mapOf(7L to Playlist(7, "Hype", false, listOf("rse-route-102")))
    }

    private val viewModel by lazy {
        EditPlaylistViewModel(
            SavedStateHandle(mapOf("playlistId" to 7L)),
            FakeCatalogRepository(catalog),
            playlists,
            main.dispatcher
        )
    }

    private val chipR = ChipUi("R", "#336699")
    private val chipS = ChipUi("S", "#336699")

    @Test
    fun `the title has the playlist's name from the start, before the playlist loads`() {
        val opened = EditPlaylistViewModel(
            SavedStateHandle(mapOf("playlistId" to 7L, "playlistName" to "Hype")),
            FakeCatalogRepository(catalog),
            FakePlaylistRepository(),
            main.dispatcher
        )

        assertEquals("Hype", opened.uiState.value.playlistName)
    }

    @Test
    fun `an empty field lists the playlist's tracks, newest first, all checked`() = runTest {
        playlists.contents.value = mapOf(
            7L to Playlist(7, "Hype", false, listOf("rse-route-102", "rse-gone", "rse-littleroot"))
        )

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Hype", state.playlistName)
            assertEquals(
                listOf(
                    EditPlaylistRowUi("rse-route-102", "Route 102", listOf(chipR), added = true),
                    EditPlaylistRowUi(
                        "rse-littleroot",
                        "Littleroot Town",
                        listOf(chipR, chipS),
                        added = true
                    )
                ),
                state.results
            )
            assertFalse(state.noResults)
        }
    }

    @Test
    fun `unchecking a track under an empty field takes its row away`() = runTest {
        viewModel.uiState.test {
            assertEquals(listOf("rse-route-102"), awaitItem().results.map { it.id })

            viewModel.onToggle("rse-route-102")

            assertEquals(emptyList<EditPlaylistRowUi>(), awaitItem().results)
            assertEquals(emptyList<String>(), playlists.contents.value[7]?.trackIds)
        }
    }

    @Test
    fun `clearing the field goes back to the playlist's tracks`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("littleroot")
            assertEquals(listOf("rse-littleroot"), awaitItem().results.map { it.id })

            viewModel.onQueryChange("")

            assertEquals(listOf("rse-route-102"), awaitItem().results.map { it.id })
        }
    }

    @Test
    fun `typing shows the matches with all their games, checked if the playlist has them`() =
        runTest {
            viewModel.uiState.test {
                awaitItem()
                viewModel.onQueryChange("route")

                assertEquals(
                    listOf(
                        EditPlaylistRowUi("rse-route-101", "Route 101", listOf(chipR, chipS)),
                        EditPlaylistRowUi("rse-route-102", "Route 102", listOf(chipR), added = true)
                    ),
                    awaitItem().results
                )
            }
        }

    @Test
    fun `the plus adds a track, and the check takes it out again`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("route 101")
            assertFalse(awaitItem().results.single().added)

            viewModel.onToggle("rse-route-101")
            assertTrue(awaitItem().results.single().added)
            assertEquals(
                listOf("rse-route-101", "rse-route-102"),
                playlists.contents.value[7]?.trackIds
            )

            viewModel.onToggle("rse-route-101")
            assertFalse(awaitItem().results.single().added)
            assertEquals(listOf("rse-route-102"), playlists.contents.value[7]?.trackIds)
        }
    }

    @Test
    fun `a query that matches nothing says so`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("zzzz")

            assertTrue(awaitItem().noResults)
        }
    }
}
