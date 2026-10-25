package io.github.pranavm716.vgmixr.ui.remixes

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LinkViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val remix = CatalogTrack(
        "yt:AAAAAAAAAAA",
        "gsc",
        emptyList(),
        "Olivine Lighthouse",
        0,
        emptyList(),
        channel = "GlitchxCity",
        remixOf = "gsc-olivine-lighthouse",
        videoTitle = "Pokémon Gold and Silver - Olivine Lighthouse (Remix)"
    )

    private fun viewModel(trackId: String) = LinkViewModel(
        SavedStateHandle(mapOf("trackId" to trackId)),
        FakeCatalogRepository(Catalog(emptyList(), emptyList(), emptyList(), listOf(remix)))
    )

    @Test
    fun `the header names the remix by its video's title`() = runTest {
        viewModel("yt:AAAAAAAAAAA").uiState.test {
            assertEquals(
                LinkUiState("Pokémon Gold and Silver - Olivine Lighthouse (Remix)"),
                awaitItem()
            )
        }
    }

    @Test
    fun `a remix that is gone names nothing`() = runTest {
        viewModel("yt:ZZZZZZZZZZZ").uiState.test {
            assertEquals(LinkUiState(""), awaitItem())
        }
    }
}
