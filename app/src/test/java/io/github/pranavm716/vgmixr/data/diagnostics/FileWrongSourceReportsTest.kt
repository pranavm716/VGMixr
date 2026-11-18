package io.github.pranavm716.vgmixr.data.diagnostics

import io.github.pranavm716.vgmixr.data.manifest.ManifestFixtures
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogImportResult
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.playback.BrokenMark
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.report.WrongSourceReport
import io.github.pranavm716.vgmixr.testing.FakeBrokenSources
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakeCatalogSync
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileWrongSourceReportsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val route101 = WrongSourceReport(
        trackId = "rse-route-101",
        videoId = "WMj9b4vYTXU",
        startMs = 0,
        endMs = null,
        positionMs = 41_200,
        reportedAt = Instant.parse("2026-11-18T09:15:02.250Z"),
        catalogGeneratedAt = null
    )
    private val wild = WrongSourceReport(
        trackId = "sm-battle-wild-pokemon",
        videoId = "Q3d1kTZmUQo",
        startMs = 754_000,
        endMs = 871_500,
        positionMs = 12_000,
        reportedAt = Instant.parse("2026-11-18T20:40:00Z"),
        catalogGeneratedAt = "2026-11-17T22:03:11Z"
    )

    private val now = Instant.parse("2026-11-18T21:00:00Z")
    private val track = CatalogTrack(
        "rse-route-101",
        "rse",
        emptyList(),
        "Route 101",
        0,
        listOf(Source.Stream("first", 0, null), Source.Stream("second", 5_000, 9_000))
    )
    private val catalog = FakeCatalogRepository(Catalog(emptyList(), emptyList(), listOf(track)))
    private val broken = FakeBrokenSources()
    private val sync = FakeCatalogSync()
    private val file get() = File(folder.root, "reports.json")

    private fun TestScope.store(scope: CoroutineScope = backgroundScope) = FileWrongSourceReports(
        file = file,
        catalog = catalog,
        broken = broken,
        sync = sync,
        generatedAt = { "2026-11-17T22:03:11Z" },
        clock = Clock.fixed(now, ZoneOffset.UTC),
        scope = scope,
        io = StandardTestDispatcher(testScheduler)
    )

    @Test
    fun `the file is the shared fixture's format, oldest first`() {
        assertEquals(
            ManifestFixtures.read("reports.json").replace("\r\n", "\n"),
            reportsJson(listOf(wild, route101))
        )
    }

    @Test
    fun `the fixture reads back as its reports`() {
        assertEquals(listOf(route101, wild), parseReports(ManifestFixtures.read("reports.json")))
    }

    @Test
    fun `a missing or invalid file reads as no reports`() = runTest {
        val store = store().also { it.start() }
        runCurrent()
        assertEquals(emptyList<WrongSourceReport>(), store.reports.first())

        file.writeText("{ not json")
        val again = store().also { it.start() }
        runCurrent()
        assertEquals(emptyList<WrongSourceReport>(), again.reports.first())
    }

    @Test
    fun `a tap reports the picked stream and writes the file, a second tap takes it back`() =
        runTest {
            broken.marks.value = mapOf(
                "first" to BrokenMark("first", track.id, BrokenReason.NO_AUDIO, null, now, null)
            )
            val store = store().also { it.start() }
            runCurrent()

            store.toggle(track.id, positionMs = 7_000)
            val expected = WrongSourceReport(
                track.id,
                "second",
                5_000,
                9_000,
                7_000,
                now,
                "2026-11-17T22:03:11Z"
            )
            assertEquals(listOf(expected), store.reports.first())
            assertEquals(reportsJson(listOf(expected)), file.readText())

            store.toggle(track.id, positionMs = 8_000)
            assertEquals(emptyList<WrongSourceReport>(), store.reports.first())
            assertEquals(reportsJson(emptyList()), file.readText())
        }

    @Test
    fun `reports load from the file on start`() = runTest {
        file.writeText(reportsJson(listOf(route101)))
        val store = store().also { it.start() }
        runCurrent()
        assertEquals(listOf(route101), store.reports.first())
    }

    @Test
    fun `a track with nothing to play, or not in the catalog, can't be reported`() = runTest {
        val store = store().also { it.start() }
        runCurrent()
        store.toggle("rse-route-999", positionMs = 0)
        assertEquals(emptyList<WrongSourceReport>(), store.reports.first())
        assertFalse(file.exists())
    }

    @Test
    fun `an import drops the reports its catalog fixed`() = runTest {
        val stale = route101.copy(videoId = "gone")
        val open = route101.copy(trackId = track.id, videoId = "first")
        file.writeText(reportsJson(listOf(stale.copy(trackId = "rse-route-102"), open)))
        val store = store().also { it.start() }
        runCurrent()

        sync.status.value = CatalogImportResult.Imported(now)
        runCurrent()
        assertEquals(listOf(open), store.reports.first())
        assertEquals(reportsJson(listOf(open)), file.readText())
    }
}
