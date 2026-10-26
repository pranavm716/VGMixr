package io.github.pranavm716.vgmixr.data.catalog

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.manifest
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.track
import io.github.pranavm716.vgmixr.data.db.inMemoryDatabase
import io.github.pranavm716.vgmixr.data.offline.RoomOfflineStore
import io.github.pranavm716.vgmixr.data.stream.RoomBrokenSources
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.offline.OfflineKind
import io.github.pranavm716.vgmixr.domain.offline.OfflinePlan
import io.github.pranavm716.vgmixr.domain.offline.OfflineWant
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnplayableTracksTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = inMemoryDatabase(dispatcher)
    private val repository = RoomCatalogRepository(db, db.catalogDao())
    private val broken =
        RoomBrokenSources(db.brokenStreamDao(), db.importStateDao(), TestCatalog.clock)

    @After
    fun closeDb() = db.close()

    private fun stream(videoId: String, startMs: Long = 0) = Source.Stream(videoId, startMs, null)

    private fun Catalog.unplayable() = tracks.associate { it.id to it.unplayable }

    private suspend fun import() = TestCatalog.importer(db).import(
        manifest(
            tracks = listOf(
                track("rse-one", sources = listOf(stream("AAAAAAAAAAA"))),
                track("rse-two", sources = listOf(stream("AAAAAAAAAAA"), stream("BBBBBBBBBBB"))),
                track("rse-slice", sources = listOf(stream("CCCCCCCCCCC", 60_000))),
                track("rse-local", sources = listOf(Source.Local("rse/local.m4a", null)))
            )
        )
    )

    @Test
    fun `a track is unplayable once every stream source is marked, for its first mark's reason`() =
        runTest(dispatcher) {
            import()
            broken.mark("AAAAAAAAAAA", "rse-one", BrokenReason.NO_AUDIO, null)
            broken.mark("CCCCCCCCCCC", "rse-slice", BrokenReason.UNAVAILABLE, "private")

            assertEquals(
                mapOf(
                    "rse-one" to BrokenReason.NO_AUDIO,
                    "rse-two" to null,
                    "rse-slice" to BrokenReason.UNAVAILABLE,
                    // No stream to mark: missing local files are the player's business.
                    "rse-local" to null
                ),
                repository.catalog.first().unplayable()
            )
        }

    @Test
    fun `the catalog follows marks as they come and go`() = runTest(dispatcher) {
        import()

        repository.catalog.test {
            assertEquals(null, awaitItem().unplayable()["rse-one"])
            broken.mark("AAAAAAAAAAA", "rse-one", BrokenReason.NO_AUDIO, null)
            assertEquals(BrokenReason.NO_AUDIO, awaitItem().unplayable()["rse-one"])
            broken.clear("AAAAAAAAAAA")
            assertEquals(null, awaitItem().unplayable()["rse-one"])
        }
    }

    @Test
    fun `clearing a video's mark un-greys every track that uses it`() = runTest(dispatcher) {
        import()
        broken.mark("AAAAAAAAAAA", "rse-one", BrokenReason.NO_AUDIO, null)
        broken.mark("BBBBBBBBBBB", "rse-two", BrokenReason.NO_AUDIO, null)

        broken.clear("AAAAAAAAAAA")

        assertEquals(setOf("BBBBBBBBBBB"), broken.marks.first().keys)
        assertEquals(null, repository.catalog.first().unplayable()["rse-one"])
    }

    @Test
    fun `a marked video that is downloaded still plays`() = runTest(dispatcher) {
        import()
        val offline = RoomOfflineStore(db.offlineDao())
        offline.apply(
            OfflinePlan(
                listOf(OfflineWant("AAAAAAAAAAA", OfflineKind.DOWNLOADED, listOf("rse-one"))),
                emptyList()
            )
        )
        offline.setComplete("AAAAAAAAAAA", 1_000)

        broken.mark("AAAAAAAAAAA", "rse-one", BrokenReason.NO_AUDIO, null)

        assertEquals(null, repository.catalog.first().unplayable()["rse-one"])
    }
}
