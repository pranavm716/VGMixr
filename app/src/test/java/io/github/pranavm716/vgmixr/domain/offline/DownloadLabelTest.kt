package io.github.pranavm716.vgmixr.domain.offline

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadLabelTest {
    private val catalog = Catalog(
        games = emptyList(),
        families = listOf(Family("sv", "Scarlet / Violet", "SV")),
        tracks = listOf(
            CatalogTrack("sv-cabo-poco", "sv", emptyList(), "Cabo Poco", 4, emptyList()),
            CatalogTrack("sv-home", "sv", emptyList(), "Home", 3, emptyList())
        )
    )

    @Test
    fun `a video for one track is named by that track`() {
        assertEquals("Cabo Poco", downloadLabel(setOf("sv-cabo-poco"), catalog))
    }

    @Test
    fun `an album video is named by its family`() {
        assertEquals(
            "Scarlet / Violet album",
            downloadLabel(setOf("sv-cabo-poco", "sv-home"), catalog)
        )
    }

    @Test
    fun `an unknown video still gets a name`() {
        assertEquals("a track", downloadLabel(emptySet(), catalog))
    }
}
