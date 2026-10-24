package io.github.pranavm716.vgmixr.data.catalog

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.manifest
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.track
import io.github.pranavm716.vgmixr.data.db.BrokenStreamEntity
import io.github.pranavm716.vgmixr.data.db.UserTrackEntity
import io.github.pranavm716.vgmixr.data.db.inMemoryDatabase
import io.github.pranavm716.vgmixr.domain.manifest.ManifestError
import io.github.pranavm716.vgmixr.domain.manifest.ManifestParseResult
import io.github.pranavm716.vgmixr.domain.model.Source
import java.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportClearsBrokenMarksTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = inMemoryDatabase(dispatcher)
    private val importer = TestCatalog.importer(db)
    private val broken = db.brokenStreamDao()

    @After
    fun closeDb() = db.close()

    private fun stream(videoId: String, startMs: Long = 0) = Source.Stream(videoId, startMs, null)

    private suspend fun mark(videoId: String) = broken.insert(
        BrokenStreamEntity(videoId, "rse-a", "no-audio", null, Instant.EPOCH, null)
    )

    private suspend fun marked() = broken.all().map { it.videoId }

    @Test
    fun `a mark stays while the new catalog still uses its video`() = runTest(dispatcher) {
        importer.import(manifest("2026-10-13T08:00:00Z", tracks = listOf(track("rse-a"))))
        mark("AAAAAAAAAAA")

        // The same video, now as a slice of another track.
        importer.import(
            manifest(
                "2026-10-14T08:00:00Z",
                tracks = listOf(track("rse-b", sources = listOf(stream("AAAAAAAAAAA", 5_000))))
            )
        )

        assertEquals(listOf("AAAAAAAAAAA"), marked())
    }

    @Test
    fun `a mark goes once the new catalog no longer uses its video`() = runTest(dispatcher) {
        importer.import(manifest("2026-10-13T08:00:00Z", tracks = listOf(track("rse-a"))))
        mark("AAAAAAAAAAA")

        importer.import(
            manifest(
                "2026-10-14T08:00:00Z",
                tracks = listOf(track("rse-a", sources = listOf(stream("BBBBBBBBBBB"))))
            )
        )

        assertEquals(emptyList<String>(), marked())
    }

    @Test
    fun `a video only a retired track used counts as no longer used`() = runTest(dispatcher) {
        importer.import(manifest("2026-10-13T08:00:00Z", tracks = listOf(track("rse-a"))))
        mark("AAAAAAAAAAA")

        // rse-a is retired but keeps its sources in Room.
        importer.import(
            manifest(
                "2026-10-14T08:00:00Z",
                tracks = listOf(track("rse-b", sources = listOf(stream("BBBBBBBBBBB"))))
            )
        )

        assertEquals(emptyList<String>(), marked())
    }

    @Test
    fun `a mark stays while a user track plays its video`() = runTest(dispatcher) {
        importer.import(manifest("2026-10-13T08:00:00Z", tracks = listOf(track("rse-a"))))
        db.userTrackDao().insert(
            UserTrackEntity(
                id = "yt:UUUUUUUUUUU",
                videoId = "UUUUUUUUUUU",
                startMs = 0,
                endMs = null,
                title = "Remix",
                channel = "GlitchxCity",
                durationMs = null,
                addedAt = Instant.EPOCH,
                remixOf = null
            )
        )
        mark("UUUUUUUUUUU")

        importer.import(manifest("2026-10-14T08:00:00Z", tracks = listOf(track("rse-a"))))

        assertEquals(listOf("UUUUUUUUUUU"), marked())
    }

    @Test
    fun `an unchanged or failed import leaves the marks alone`() = runTest(dispatcher) {
        importer.import(manifest("2026-10-13T08:00:00Z", tracks = listOf(track("rse-a"))))
        mark("ZZZZZZZZZZZ")

        importer.import(manifest("2026-10-13T08:00:00Z", tracks = listOf(track("rse-a"))))
        importer.import(ManifestParseResult.Invalid(listOf(ManifestError.Malformed("broken"))))

        assertEquals(listOf("ZZZZZZZZZZZ"), marked())
    }
}
