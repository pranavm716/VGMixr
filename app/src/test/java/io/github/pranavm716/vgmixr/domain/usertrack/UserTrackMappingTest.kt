package io.github.pranavm716.vgmixr.domain.usertrack

import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class UserTrackMappingTest {
    private val ruby = game("ruby")
    private val emerald = game("emerald")
    private val catalog = Catalog(
        games = listOf(ruby, emerald),
        families = listOf(Family("rse", "Ruby / Emerald", "RE")),
        tracks = listOf(
            CatalogTrack(
                "rse-route-101",
                "rse",
                listOf(ruby, emerald),
                "Route 101",
                10,
                listOf(Source.Stream("CATALOGXXXX", 0, null))
            )
        )
    )

    private fun userTrack(remixOf: String? = null) = UserTrack(
        videoId = "AAAAAAAAAAA",
        startMs = 61_000,
        endMs = 245_000,
        title = "Route 101 [Remix]",
        channel = "GlitchxCity",
        durationMs = 300_000,
        addedAt = Instant.EPOCH,
        remixOf = remixOf
    )

    @Test
    fun `a remix has its original's title, family and games and plays its own video`() {
        assertEquals(
            CatalogTrack(
                id = "yt:AAAAAAAAAAA",
                familyId = "rse",
                games = listOf(ruby, emerald),
                title = "Route 101",
                order = 0,
                sources = listOf(Source.Stream("AAAAAAAAAAA", 61_000, 245_000)),
                channel = "GlitchxCity",
                remixOf = "rse-route-101"
            ),
            userTrack(remixOf = "rse-route-101").toCatalogTrack(catalog, unplayable = null)
        )
    }

    @Test
    fun `a track that remixes nothing has no family or games`() {
        val track = userTrack().toCatalogTrack(catalog, unplayable = null)

        assertEquals(null, track.familyId)
        assertEquals(emptyList<Any>(), track.games)
    }

    @Test
    fun `a remix of a track no longer in the catalog has no family or games`() {
        val track = userTrack(remixOf = "rse-retired").toCatalogTrack(catalog, unplayable = null)

        assertEquals(null, track.familyId)
        assertEquals(emptyList<Any>(), track.games)
    }

    @Test
    fun `a broken video makes it unplayable`() {
        assertEquals(
            BrokenReason.UNAVAILABLE,
            userTrack().toCatalogTrack(catalog, BrokenReason.UNAVAILABLE).unplayable
        )
    }

    @Test
    fun `it carries its thumbnail`() {
        assertEquals(
            "thumbnails/AAAAAAAAAAA.jpg",
            userTrack().toCatalogTrack(catalog, null, "thumbnails/AAAAAAAAAAA.jpg").thumbnail
        )
    }

    @Test
    fun `a track that remixes nothing keeps its video's title`() {
        assertEquals("Route 101 [Remix]", userTrack().toCatalogTrack(catalog, null).title)
    }

    @Test
    fun `a remix whose original left the catalog keeps its video's title`() {
        assertEquals(
            "Route 101 [Remix]",
            userTrack(remixOf = "rse-retired").toCatalogTrack(catalog, null).title
        )
    }
}
