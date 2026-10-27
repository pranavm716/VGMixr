package io.github.pranavm716.vgmixr.data.catalog

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.manifest
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.track
import io.github.pranavm716.vgmixr.data.db.inMemoryDatabase
import io.github.pranavm716.vgmixr.data.stream.RoomBrokenSources
import io.github.pranavm716.vgmixr.data.usertrack.RoomUserTrackRepository
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrack
import io.github.pranavm716.vgmixr.testing.FakeThumbnailStore
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserTracksInCatalogTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = inMemoryDatabase(dispatcher)
    private val repository = RoomCatalogRepository(db, db.catalogDao())
    private val userTracks = RoomUserTrackRepository(db.userTrackDao())

    @After
    fun closeDb() = db.close()

    private fun userTrack(videoId: String, addedAt: Long, remixOf: String? = null) = UserTrack(
        videoId = videoId,
        title = "Remix $videoId",
        channel = "GlitchxCity",
        durationMs = null,
        addedAt = Instant.ofEpochMilli(addedAt),
        remixOf = remixOf
    )

    private suspend fun import() =
        TestCatalog.importer(db).import(manifest(tracks = listOf(track("rse-route-101"))))

    @Test
    fun `user tracks come newest first, apart from the catalog's tracks`() = runTest(dispatcher) {
        import()
        userTracks.add(userTrack("AAAAAAAAAAA", addedAt = 1_000, remixOf = "rse-route-101"))
        userTracks.add(userTrack("BBBBBBBBBBB", addedAt = 2_000))

        val catalog = repository.catalog.first()

        assertEquals(listOf("rse-route-101"), catalog.tracks.map { it.id })
        assertEquals(
            listOf("yt:BBBBBBBBBBB" to null, "yt:AAAAAAAAAAA" to "rse"),
            catalog.userTracks.map { it.id to it.familyId }
        )
    }

    @Test
    fun `the catalog follows user tracks as they come and go`() = runTest(dispatcher) {
        import()

        repository.catalog.test {
            assertEquals(emptyList<String>(), awaitItem().userTracks.map { it.id })
            userTracks.add(userTrack("AAAAAAAAAAA", addedAt = 1_000))
            assertEquals(listOf("yt:AAAAAAAAAAA"), awaitItem().userTracks.map { it.id })
            userTracks.delete("yt:AAAAAAAAAAA")
            assertEquals(emptyList<String>(), awaitItem().userTracks.map { it.id })
        }
    }

    @Test
    fun `a user track whose video is marked broken is unplayable`() = runTest(dispatcher) {
        import()
        userTracks.add(userTrack("AAAAAAAAAAA", addedAt = 1_000))
        RoomBrokenSources(db.brokenStreamDao(), db.importStateDao(), TestCatalog.clock)
            .mark("AAAAAAAAAAA", "yt:AAAAAAAAAAA", BrokenReason.NO_AUDIO, null)

        assertEquals(
            BrokenReason.NO_AUDIO,
            repository.catalog.first().userTracks.single().unplayable
        )
    }

    @Test
    fun `a user track carries its thumbnail's path once it is downloaded`() = runTest(dispatcher) {
        import()
        val thumbnails = FakeThumbnailStore().apply { downloaded += "AAAAAAAAAAA" }
        userTracks.add(userTrack("AAAAAAAAAAA", addedAt = 2_000))
        userTracks.add(userTrack("BBBBBBBBBBB", addedAt = 1_000))

        val catalog = RoomCatalogRepository(db, db.catalogDao(), thumbnails).catalog.first()

        assertEquals(
            listOf(File("thumbnails/AAAAAAAAAAA.jpg").path, null),
            catalog.userTracks.map { it.thumbnail }
        )
    }

    @Test
    fun `a thumbnail downloaded later shows without a database change`() = runTest(dispatcher) {
        import()
        val thumbnails = FakeThumbnailStore()
        userTracks.add(userTrack("AAAAAAAAAAA", addedAt = 2_000))

        RoomCatalogRepository(db, db.catalogDao(), thumbnails).catalog.test {
            assertEquals(null, awaitItem().userTracks.single().thumbnail)

            thumbnails.download("AAAAAAAAAAA")

            assertEquals(
                File("thumbnails/AAAAAAAAAAA.jpg").path,
                awaitItem().userTracks.single().thumbnail
            )
        }
    }
}
