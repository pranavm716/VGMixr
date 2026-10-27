package io.github.pranavm716.vgmixr.data.catalog

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.manifest
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.track
import io.github.pranavm716.vgmixr.data.db.FAVORITES_PLAYLIST_ID
import io.github.pranavm716.vgmixr.data.db.PlaylistEntryEntity
import io.github.pranavm716.vgmixr.data.db.inMemoryDatabase
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.model.Loop
import io.github.pranavm716.vgmixr.domain.model.Source
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCatalogRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = inMemoryDatabase(dispatcher)
    private val importer = TestCatalog.importer(db)
    private val repository = RoomCatalogRepository(db, db.catalogDao())

    private val ruby = game("ruby", name = "Ruby")
    private val sapphire = game("sapphire", name = "Sapphire")
    private val emerald = game("emerald", name = "Emerald")
    private val rse = listOf(ruby, sapphire, emerald)

    @After
    fun closeDb() = db.close()

    @Test
    fun `retired tracks and games are excluded`() = runTest(dispatcher) {
        importer.import(
            manifest(
                games = rse,
                tracks = listOf(
                    track("rse-shared"),
                    track("rse-sapphire", games = listOf("sapphire"))
                )
            )
        )
        importer.import(
            manifest(
                generatedAt = "2026-10-14T08:00:00Z",
                games = listOf(ruby, emerald),
                tracks = listOf(track("rse-shared"))
            )
        )

        val catalog = repository.catalog.first()

        assertEquals(listOf(ruby, emerald), catalog.games)
        assertEquals(listOf("rse-shared"), catalog.tracks.map { it.id })
        assertEquals(listOf(ruby, emerald), catalog.tracks.single().games)
    }

    @Test
    fun `chip games are the family's games for shared tracks and the subset for exclusives`() =
        runTest(dispatcher) {
            importer.import(
                manifest(
                    games = rse,
                    tracks = listOf(
                        track("rse-shared"),
                        // Listed out of manifest order; chips follow manifest order.
                        track("rse-exclusive", games = listOf("emerald", "ruby"))
                    )
                )
            )

            val chips = repository.catalog.first().tracks.associate { it.id to it.games }

            assertEquals(rse, chips["rse-shared"])
            assertEquals(listOf(ruby, emerald), chips["rse-exclusive"])
        }

    @Test
    fun `family name and label leave out a retired game`() = runTest(dispatcher) {
        importer.import(manifest(games = rse))
        importer.import(
            manifest(generatedAt = "2026-10-14T08:00:00Z", games = listOf(ruby, emerald))
        )

        val families = repository.catalog.first().families

        assertEquals(listOf(Family("rse", "Ruby / Emerald", "RE")), families)
    }

    @Test
    fun `families follow the manifest order of their games`() = runTest(dispatcher) {
        val pla = game("pla", familyId = "pla", name = "Legends: Arceus", shortName = "PLA")
        val diamond = game("diamond", familyId = "dppt", name = "Diamond", generation = 4)
        importer.import(
            manifest(
                games = listOf(pla) + rse + diamond,
                tracks = listOf(track("pla-village", familyId = "pla"))
            )
        )

        val families = repository.catalog.first().families

        assertEquals(
            listOf(
                Family("pla", "Legends: Arceus", "PLA"),
                Family("rse", "Ruby / Sapphire / Emerald", "RSE"),
                Family("dppt", "Diamond", "D")
            ),
            families
        )
    }

    @Test
    fun `sources round-trip in priority order`() = runTest(dispatcher) {
        val sources = listOf(
            Source.Local("rse/012.flac", Loop(32000, 96000, 1824000)),
            Source.Local("rse/012-alt.ogg", null),
            Source.Stream("XXXXXXXXXXX", 1000, 2000),
            Source.Stream("YYYYYYYYYYY", 0, null)
        )
        importer.import(manifest(tracks = listOf(track("rse-route-101", sources = sources))))

        val track = repository.catalog.first().tracks.single()

        assertEquals(sources, track.sources)
    }

    @Test
    fun `re-emits after a re-import`() = runTest(dispatcher) {
        importer.import(manifest(tracks = listOf(track("rse-route-101"))))

        repository.catalog.test {
            assertEquals(listOf("rse-route-101"), awaitItem().tracks.map { it.id })

            importer.import(
                manifest(
                    generatedAt = "2026-10-14T08:00:00Z",
                    tracks = listOf(track("rse-route-101"), track("rse-route-102"))
                )
            )

            assertEquals(
                listOf("rse-route-101", "rse-route-102"),
                awaitItem().tracks.map { it.id }.sorted()
            )
        }
    }

    @Test
    fun `a favorites write does not re-emit`() = runTest(dispatcher) {
        importer.import(manifest())

        repository.catalog.test {
            awaitItem()

            db.userDataDao().insertPlaylistEntry(
                PlaylistEntryEntity(
                    FAVORITES_PLAYLIST_ID,
                    "rse-route-101",
                    Instant.parse("2026-10-15T09:00:00Z")
                )
            )
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    @Test
    fun `retired tracks are kept apart, marked retired, for playlists to show`() =
        runTest(dispatcher) {
            importer.import(manifest(games = rse, tracks = listOf(track("rse-a"), track("rse-b"))))
            importer.import(
                manifest(
                    generatedAt = "2026-10-14T08:00:00Z",
                    games = rse,
                    tracks = listOf(track("rse-a"))
                )
            )

            val catalog = repository.catalog.first()

            assertEquals(listOf("rse-a"), catalog.tracks.map { it.id })
            val retired = catalog.retiredTracks.single()
            assertEquals("rse-b", retired.id)
            assertEquals(true, retired.retired)
            assertEquals(rse, retired.games)
            assertEquals(false, catalog.tracks.single().retired)
        }
}
