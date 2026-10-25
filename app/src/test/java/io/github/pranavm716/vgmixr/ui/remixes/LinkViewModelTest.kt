package io.github.pranavm716.vgmixr.ui.remixes

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrack
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakeUserTrackRepository
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LinkViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val gold = game("gold", familyId = "gsc", shortName = "G")
    private val heartGold = game("heartgold", familyId = "hgss", shortName = "HG")

    private val gscOlivine = CatalogTrack(
        "gsc-olivine-lighthouse",
        "gsc",
        listOf(gold),
        "Olivine Lighthouse",
        40,
        emptyList()
    )
    private val hgssOlivine = CatalogTrack(
        "hgss-olivine-lighthouse",
        "hgss",
        listOf(heartGold),
        "Olivine Lighthouse",
        40,
        emptyList()
    )
    private val remix = CatalogTrack(
        "yt:AAAAAAAAAAA",
        "gsc",
        listOf(gold),
        "Olivine Lighthouse",
        0,
        emptyList(),
        channel = "GlitchxCity",
        remixOf = "gsc-olivine-lighthouse",
        videoTitle = "Pokémon Gold and Silver - Olivine Lighthouse (Remix)"
    )
    private val catalog = Catalog(
        games = listOf(gold, heartGold),
        families = listOf(Family("gsc", "Gold", "G"), Family("hgss", "HeartGold", "HG")),
        tracks = listOf(gscOlivine, hgssOlivine),
        userTracks = listOf(remix)
    )

    private val userTracks = FakeUserTrackRepository(
        listOf(
            UserTrack(
                videoId = "AAAAAAAAAAA",
                title = "Pokémon Gold and Silver - Olivine Lighthouse (Remix)",
                channel = "GlitchxCity",
                durationMs = null,
                addedAt = Instant.EPOCH,
                remixOf = "gsc-olivine-lighthouse"
            )
        )
    )

    private fun viewModel(trackId: String = "yt:AAAAAAAAAAA", catalog: Catalog = this.catalog) =
        LinkViewModel(
            SavedStateHandle(mapOf("trackId" to trackId)),
            FakeCatalogRepository(catalog),
            userTracks,
            main.dispatcher
        )

    private val linkedRow = LinkRowUi(
        "gsc-olivine-lighthouse",
        "Olivine Lighthouse",
        listOf(ChipUi("G", "#336699")),
        familyId = "gsc",
        linked = true
    )

    @Test
    fun `the header names the remix by its video's title`() = runTest {
        viewModel().uiState.test {
            assertEquals(
                "Pokémon Gold and Silver - Olivine Lighthouse (Remix)",
                awaitItem().videoTitle
            )
        }
    }

    @Test
    fun `a remix that is gone names nothing`() = runTest {
        viewModel("yt:ZZZZZZZZZZZ").uiState.test {
            assertEquals(LinkUiState(""), awaitItem())
        }
    }

    @Test
    fun `shows the track the remix is linked to, and an empty field lists nothing`() = runTest {
        viewModel().uiState.test {
            val state = awaitItem()
            assertEquals(linkedRow, state.linked)
            assertEquals(emptyList<LinkRowUi>(), state.results)
        }
    }

    @Test
    fun `an unlinked remix shows no link`() = runTest {
        val unlinked = catalog.copy(userTracks = listOf(remix.copy(remixOf = null)))
        viewModel(catalog = unlinked).uiState.test {
            assertEquals(null, awaitItem().linked)
        }
    }

    @Test
    fun `typing searches the catalog's tracks only, the linked one checked`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("olivine")

            assertEquals(
                listOf(
                    linkedRow,
                    LinkRowUi(
                        "hgss-olivine-lighthouse",
                        "Olivine Lighthouse",
                        listOf(ChipUi("HG", "#336699")),
                        familyId = "hgss"
                    )
                ),
                awaitItem().results
            )
        }
    }

    @Test
    fun `picking a track links the remix to it and closes the page`() = runTest {
        val viewModel = viewModel()
        viewModel.events.test {
            viewModel.onPick("hgss-olivine-lighthouse")

            assertEquals(LinkEvent.Close, awaitItem())
            assertEquals("hgss-olivine-lighthouse", userTracks.userTracks.first().single().remixOf)
        }
    }

    @Test
    fun `clearing removes the link`() = runTest {
        viewModel().onClear()

        assertEquals(null, userTracks.userTracks.first().single().remixOf)
    }
}
