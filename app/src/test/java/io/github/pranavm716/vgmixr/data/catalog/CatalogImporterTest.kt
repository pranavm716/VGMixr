package io.github.pranavm716.vgmixr.data.catalog

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.manifest
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.track
import io.github.pranavm716.vgmixr.data.db.GameEntity
import io.github.pranavm716.vgmixr.data.db.SourceEntity
import io.github.pranavm716.vgmixr.data.db.TrackEntity
import io.github.pranavm716.vgmixr.data.db.inMemoryDatabase
import io.github.pranavm716.vgmixr.data.manifest.ManifestFixtures
import io.github.pranavm716.vgmixr.data.manifest.ManifestParser
import io.github.pranavm716.vgmixr.domain.catalog.CatalogImportResult
import io.github.pranavm716.vgmixr.domain.model.Loop
import io.github.pranavm716.vgmixr.domain.model.Source
import java.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogImporterTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = inMemoryDatabase(dispatcher)
    private val catalogDao = db.catalogDao()
    private val importer = TestCatalog.importer(db)

    @After
    fun closeDb() = db.close()

    // Upsert and child tables

    @Test
    fun `imports games tracks and sources`() = runTest(dispatcher) {
        val result = importer.import(
            manifest(
                tracks = listOf(
                    track(
                        "rse-route-101",
                        sources = listOf(
                            Source.Local("rse/012.flac", Loop(32000, 96000, 1824000)),
                            Source.Stream("XXXXXXXXXXX", 1000, 2000)
                        )
                    )
                )
            )
        )

        assertEquals(CatalogImportResult.Imported(Instant.parse("2026-10-13T08:00:00Z")), result)
        assertEquals(
            GameEntity("sapphire", "sapphire", "S", "#336699", 3, "GBA", "rse", 1, false),
            catalogDao.game("sapphire")
        )
        assertEquals(
            TrackEntity("rse-route-101", "rse", "rse-route-101", "ROUTE", 0, false),
            catalogDao.track("rse-route-101")
        )
        assertEquals(
            listOf(
                SourceEntity(
                    trackId = "rse-route-101",
                    position = 0,
                    type = SourceEntity.TYPE_LOCAL,
                    path = "rse/012.flac",
                    loopSampleRate = 32000,
                    loopIntroEnd = 96000,
                    loopEnd = 1824000
                ),
                SourceEntity(
                    trackId = "rse-route-101",
                    position = 1,
                    type = SourceEntity.TYPE_STREAM,
                    videoId = "XXXXXXXXXXX",
                    startMs = 1000,
                    endMs = 2000
                )
            ),
            catalogDao.sources("rse-route-101")
        )
    }

    @Test
    fun `stores a track's games subset, and no rows when games is omitted`() = runTest(dispatcher) {
        importer.import(
            manifest(
                tracks = listOf(
                    track("rse-route-101"),
                    track("rse-weather-institute", games = listOf("ruby", "emerald"))
                )
            )
        )

        assertEquals(emptyList<String>(), catalogDao.trackGames("rse-route-101"))
        assertEquals(listOf("emerald", "ruby"), catalogDao.trackGames("rse-weather-institute"))
    }

    @Test
    fun `re-import updates games and tracks in place`() = runTest(dispatcher) {
        importer.import(manifest())

        importer.import(
            manifest(
                generatedAt = "2026-10-14T08:00:00Z",
                games = listOf(
                    game("ruby", name = "Pokémon Ruby"),
                    game("sapphire"),
                    game("emerald")
                ),
                tracks = listOf(
                    track("rse-route-101", title = "Route 101 (Day)", games = listOf("ruby"))
                )
            )
        )

        assertEquals("Pokémon Ruby", catalogDao.game("ruby")?.name)
        assertEquals("Route 101 (Day)", catalogDao.track("rse-route-101")?.title)
        assertEquals(listOf("ruby"), catalogDao.trackGames("rse-route-101"))
        assertEquals(3, catalogDao.games().size)
        assertEquals(1, catalogDao.tracks().size)
    }

    @Test
    fun `re-import replaces sources wholesale in manifest order`() = runTest(dispatcher) {
        importer.import(
            manifest(
                tracks = listOf(
                    track(
                        "rse-route-101",
                        sources = listOf(
                            Source.Stream("AAAAAAAAAAA", 0, null),
                            Source.Stream("BBBBBBBBBBB", 0, null),
                            Source.Stream("CCCCCCCCCCC", 0, null)
                        )
                    )
                )
            )
        )

        importer.import(
            manifest(
                generatedAt = "2026-10-14T08:00:00Z",
                tracks = listOf(
                    track(
                        "rse-route-101",
                        sources = listOf(
                            Source.Stream("CCCCCCCCCCC", 0, null),
                            Source.Stream("AAAAAAAAAAA", 0, null)
                        )
                    )
                )
            )
        )

        assertEquals(
            listOf("CCCCCCCCCCC" to 0, "AAAAAAAAAAA" to 1),
            catalogDao.sources("rse-route-101").map { it.videoId to it.position }
        )
    }

    @Test
    fun `imports the shared valid fixture`() = runTest(dispatcher) {
        val result = importer.import(ManifestParser().parse(ManifestFixtures.read("valid.json")))

        assertEquals(CatalogImportResult.Imported(Instant.parse("2026-10-14T08:00:00Z")), result)
        assertEquals(7, catalogDao.games().size)
        assertEquals(6, catalogDao.tracks().size)
        assertEquals(
            listOf("ruby", "sapphire", "emerald"),
            catalogDao.gamesInFamily("rse").map { it.id }
        )
        assertEquals("OTHER", catalogDao.track("rse-weather-institute")?.category)
        assertEquals(2, catalogDao.sources("botw-hyrule-field").size)
    }
}
