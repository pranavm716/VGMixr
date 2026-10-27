package io.github.pranavm716.vgmixr.domain.usertrack

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.offline.Connectivity
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakeThumbnailStore
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreThumbnailsTest {
    private fun remix(videoId: String, thumbnail: String? = null) = CatalogTrack(
        UserTrackId.of(videoId),
        null,
        emptyList(),
        videoId,
        0,
        emptyList(),
        thumbnail = thumbnail
    )

    private val catalog = FakeCatalogRepository(
        Catalog(
            games = emptyList(),
            families = emptyList(),
            tracks = emptyList(),
            userTracks = listOf(
                remix("AAAAAAAAAAA", thumbnail = File("thumbnails/AAAAAAAAAAA.jpg").path),
                remix("BBBBBBBBBBB"),
                remix("CCCCCCCCCCC")
            )
        )
    )
    private val thumbnails = FakeThumbnailStore()
    private val connectivity = object : Connectivity {
        override val online = MutableStateFlow(true)
    }
    private val restore = RestoreThumbnails(catalog, thumbnails, connectivity)

    @Test
    fun `online, the missing thumbnails are downloaded`() = runTest {
        val job = launch { restore() }
        runCurrent()

        assertEquals(setOf("BBBBBBBBBBB", "CCCCCCCCCCC"), thumbnails.downloaded)
        job.cancel()
    }

    @Test
    fun `offline, nothing is fetched until the connection comes back`() = runTest {
        connectivity.online.value = false
        val job = launch { restore() }
        runCurrent()
        assertEquals(emptySet<String>(), thumbnails.downloaded)

        connectivity.online.value = true
        runCurrent()

        assertEquals(setOf("BBBBBBBBBBB", "CCCCCCCCCCC"), thumbnails.downloaded)
        job.cancel()
    }
}
