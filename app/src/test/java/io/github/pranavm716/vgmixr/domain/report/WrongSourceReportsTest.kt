package io.github.pranavm716.vgmixr.domain.report

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.model.Source
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WrongSourceReportsTest {
    private val album = Source.Stream("album", startMs = 60_000, endMs = 120_000)
    private val upload = Source.Stream("upload", startMs = 0, endMs = null)
    private val route = CatalogTrack(
        "rse-route-101",
        "rse",
        emptyList(),
        "Route 101",
        0,
        listOf(Source.Local("route.flac", null), upload, album)
    )
    private val catalog = Catalog(emptyList(), emptyList(), listOf(route))

    private fun report(trackId: String = route.id, videoId: String = "upload") =
        WrongSourceReport(trackId, videoId, 0, null, 5_000, Instant.EPOCH, null)

    @Test
    fun `the picked stream is the first one not marked broken, slice included`() {
        assertEquals(upload, pickedStream(route, broken = emptySet()))
        assertEquals(album, pickedStream(route, broken = setOf("upload")))
    }

    @Test
    fun `a track whose streams are all broken has no picked stream`() {
        assertNull(pickedStream(route, broken = setOf("upload", "album")))
    }

    @Test
    fun `a report stays while its track still picks the reported video`() {
        val kept = report()
        assertEquals(listOf(kept), retained(listOf(kept), catalog, broken = emptySet()))
    }

    @Test
    fun `a report goes once its track picks another video or left the catalog`() {
        val replaced = report(videoId = "album")
        val gone = report(trackId = "rse-route-102")
        assertEquals(
            emptyList<WrongSourceReport>(),
            retained(listOf(replaced, gone), catalog, broken = emptySet())
        )
    }

    @Test
    fun `toggling adds a report for an unreported track and removes it again`() {
        val added = toggled(emptyList(), report())
        assertEquals(listOf(report()), added)
        assertEquals(emptyList<WrongSourceReport>(), toggled(added, report(videoId = "album")))
    }

    @Test
    fun `toggling one track leaves the other reports alone`() {
        val other = report(trackId = "rse-route-102")
        assertEquals(listOf(other, report()), toggled(listOf(other), report()))
    }
}
