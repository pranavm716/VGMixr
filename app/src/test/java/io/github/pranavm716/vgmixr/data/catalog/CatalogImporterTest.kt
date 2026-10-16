package io.github.pranavm716.vgmixr.data.catalog

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.manifest
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.track
import io.github.pranavm716.vgmixr.data.db.FavoriteEntity
import io.github.pranavm716.vgmixr.data.db.GameEntity
import io.github.pranavm716.vgmixr.data.db.ImportStateEntity
import io.github.pranavm716.vgmixr.data.db.PlayHistoryEntity
import io.github.pranavm716.vgmixr.data.db.SourceEntity
import io.github.pranavm716.vgmixr.data.db.TrackEntity
import io.github.pranavm716.vgmixr.data.db.UserDataDao
import io.github.pranavm716.vgmixr.data.db.inMemoryDatabase
import io.github.pranavm716.vgmixr.data.manifest.ManifestFixtures
import io.github.pranavm716.vgmixr.data.manifest.ManifestParser
import io.github.pranavm716.vgmixr.domain.catalog.CatalogImportResult
import io.github.pranavm716.vgmixr.domain.catalog.ImportError
import io.github.pranavm716.vgmixr.domain.manifest.ManifestError
import io.github.pranavm716.vgmixr.domain.manifest.ManifestParseResult
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
    private val userDataDao = db.userDataDao()
    private val importer = TestCatalog.importer(db)
    private val importStateDao = db.importStateDao()

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

    // Retire, un-retire and positions

    @Test
    fun `games and tracks missing from a new manifest are retired, not deleted`() =
        runTest(dispatcher) {
            importer.import(
                manifest(tracks = listOf(track("rse-route-101"), track("rse-drought")))
            )

            importer.import(
                manifest(
                    generatedAt = "2026-10-14T08:00:00Z",
                    games = listOf(game("ruby"), game("sapphire")),
                    tracks = listOf(track("rse-route-101"))
                )
            )

            assertEquals(true, catalogDao.game("emerald")?.retired)
            assertEquals(false, catalogDao.game("ruby")?.retired)
            assertEquals(true, catalogDao.track("rse-drought")?.retired)
            assertEquals(false, catalogDao.track("rse-route-101")?.retired)
        }

    @Test
    fun `retired track keeps its last sources and games subset`() = runTest(dispatcher) {
        importer.import(
            manifest(
                tracks = listOf(
                    track("rse-route-101"),
                    track(
                        "rse-drought",
                        games = listOf("ruby"),
                        sources = listOf(Source.Stream("DDDDDDDDDDD", 0, null))
                    )
                )
            )
        )

        importer.import(
            manifest(generatedAt = "2026-10-14T08:00:00Z", tracks = listOf(track("rse-route-101")))
        )

        assertEquals(listOf("DDDDDDDDDDD"), catalogDao.sources("rse-drought").map { it.videoId })
        assertEquals(listOf("ruby"), catalogDao.trackGames("rse-drought"))
    }

    @Test
    fun `retired game and track that reappear are un-retired and updated`() = runTest(dispatcher) {
        importer.import(manifest(tracks = listOf(track("rse-route-101"), track("rse-drought"))))
        importer.import(
            manifest(
                generatedAt = "2026-10-14T08:00:00Z",
                games = listOf(game("ruby"), game("sapphire")),
                tracks = listOf(track("rse-route-101"))
            )
        )

        importer.import(
            manifest(
                generatedAt = "2026-10-15T08:00:00Z",
                games = listOf(
                    game("ruby"),
                    game("sapphire"),
                    game("emerald", name = "Emerald")
                ),
                tracks = listOf(track("rse-route-101"), track("rse-drought", title = "Drought"))
            )
        )

        assertEquals(false, catalogDao.game("emerald")?.retired)
        assertEquals("Emerald", catalogDao.game("emerald")?.name)
        assertEquals(false, catalogDao.track("rse-drought")?.retired)
        assertEquals("Drought", catalogDao.track("rse-drought")?.title)
    }

    @Test
    fun `game positions follow the newest manifest`() = runTest(dispatcher) {
        importer.import(manifest())

        importer.import(
            manifest(
                generatedAt = "2026-10-14T08:00:00Z",
                games = listOf(game("emerald"), game("ruby"), game("sapphire"))
            )
        )

        assertEquals(
            listOf("emerald" to 0, "ruby" to 1, "sapphire" to 2),
            catalogDao.gamesInFamily("rse").map { it.id to it.position }
        )
    }

    @Test
    fun `retired game keeps its last position`() = runTest(dispatcher) {
        importer.import(manifest())

        importer.import(
            manifest(
                generatedAt = "2026-10-14T08:00:00Z",
                games = listOf(game("ruby"), game("emerald"))
            )
        )

        assertEquals(1, catalogDao.game("sapphire")?.position)
        assertEquals(
            listOf("ruby", "emerald", "sapphire"),
            catalogDao.gamesInFamily("rse").map { it.id }
        )
    }

    // Replaces

    @Test
    fun `replaces re-points every history row to the new id`() = runTest(dispatcher) {
        importer.import(manifest(tracks = listOf(track("rse-weather-lab"), track("rse-route-101"))))
        history("rse-weather-lab", 1_000)
        history("rse-route-101", 2_000)
        history("rse-weather-lab", 3_000)

        importer.import(
            manifest(
                generatedAt = "2026-10-14T08:00:00Z",
                tracks = listOf(
                    track("rse-weather-institute", replaces = listOf("rse-weather-lab")),
                    track("rse-route-101")
                )
            )
        )

        assertEquals(
            listOf(
                "rse-weather-institute" to 1_000L,
                "rse-route-101" to 2_000L,
                "rse-weather-institute" to 3_000L
            ),
            userDataDao.history().map { it.trackId to it.playedAt.toEpochMilli() }
        )
        assertEquals(true, catalogDao.track("rse-weather-lab")?.retired)
    }

    @Test
    fun `replaces moves a favorite on the old id to the new id`() = runTest(dispatcher) {
        importer.import(manifest(tracks = listOf(track("rse-weather-lab"))))
        favorite("rse-weather-lab", 1_000)

        importer.import(
            manifest(
                generatedAt = "2026-10-14T08:00:00Z",
                tracks = listOf(
                    track("rse-weather-institute", replaces = listOf("rse-weather-lab"))
                )
            )
        )

        assertEquals(
            listOf(FavoriteEntity("rse-weather-institute", Instant.ofEpochMilli(1_000))),
            userDataDao.favorites()
        )
    }

    @Test
    fun `replaces merges favorites on both ids keeping the earlier addedAt`() =
        runTest(dispatcher) {
            importer.import(
                manifest(tracks = listOf(track("rse-a"), track("rse-b"), track("rse-c")))
            )
            favorite("rse-a", 1_000) // old favorited first
            favorite("rse-new-a", 2_000)
            favorite("rse-new-b", 3_000) // new favorited first
            favorite("rse-b", 4_000)

            importer.import(
                manifest(
                    generatedAt = "2026-10-14T08:00:00Z",
                    tracks = listOf(
                        track("rse-new-a", replaces = listOf("rse-a")),
                        track("rse-new-b", replaces = listOf("rse-b")),
                        track("rse-c")
                    )
                )
            )

            assertEquals(
                listOf(
                    FavoriteEntity("rse-new-a", Instant.ofEpochMilli(1_000)),
                    FavoriteEntity("rse-new-b", Instant.ofEpochMilli(3_000))
                ),
                userDataDao.favorites()
            )
        }

    @Test
    fun `replaces leaves unrelated favorites and history untouched`() = runTest(dispatcher) {
        importer.import(manifest(tracks = listOf(track("rse-weather-lab"), track("rse-route-101"))))
        favorite("rse-route-101", 1_000)
        favorite("rse-retired-elsewhere", 2_000)
        history("rse-route-101", 3_000)

        importer.import(
            manifest(
                generatedAt = "2026-10-14T08:00:00Z",
                tracks = listOf(
                    track("rse-weather-institute", replaces = listOf("rse-weather-lab"))
                )
            )
        )

        assertEquals(
            listOf("rse-retired-elsewhere", "rse-route-101"),
            userDataDao.favorites().map { it.trackId }
        )
        assertEquals(listOf("rse-route-101"), userDataDao.history().map { it.trackId })
    }

    @Test
    fun `replaces of an id with no favorites or history changes nothing`() = runTest(dispatcher) {
        importer.import(manifest(tracks = listOf(track("rse-route-101"))))
        favorite("rse-route-101", 1_000)

        importer.import(
            manifest(
                generatedAt = "2026-10-14T08:00:00Z",
                tracks = listOf(track("rse-route-101", replaces = listOf("rse-never-existed")))
            )
        )

        assertEquals(
            listOf(FavoriteEntity("rse-route-101", Instant.ofEpochMilli(1_000))),
            userDataDao.favorites()
        )
        assertEquals(null, catalogDao.track("rse-never-existed"))
    }

    // Import state and failures

    @Test
    fun `records the imported generatedAt and the import time`() = runTest(dispatcher) {
        importer.import(manifest(generatedAt = "2026-10-13T08:00:00.123456Z"))

        assertEquals(
            ImportStateEntity(
                generatedAt = "2026-10-13T08:00:00.123456Z",
                importedAt = TestCatalog.clock.instant()
            ),
            importStateDao.get()
        )
    }

    @Test
    fun `re-import of the same generatedAt is a no-op`() = runTest(dispatcher) {
        importer.import(manifest())

        val result = importer.import(
            manifest(tracks = listOf(track("rse-route-101", title = "Changed"), track("rse-new")))
        )

        assertEquals(CatalogImportResult.Unchanged, result)
        assertEquals("rse-route-101", catalogDao.track("rse-route-101")?.title)
        assertEquals(null, catalogDao.track("rse-new"))
    }

    @Test
    fun `an invalid manifest fails and writes nothing`() = runTest(dispatcher) {
        val error = ManifestError.TrackTitleEmpty("rse-route-101")

        val result = importer.import(ManifestParseResult.Invalid(listOf(error)))

        assertEquals(CatalogImportResult.Failed(listOf(ImportError.Invalid(error))), result)
        assertEquals(emptyList<TrackEntity>(), catalogDao.tracks())
        assertEquals(null, importStateDao.get())
    }

    @Test
    fun `an invalid manifest leaves the previous catalog intact`() = runTest(dispatcher) {
        importer.import(manifest())
        val games = catalogDao.games()
        val tracks = catalogDao.tracks()
        val state = importStateDao.get()

        importer.import(ManifestParseResult.Invalid(listOf(ManifestError.Malformed("bad"))))

        assertEquals(games, catalogDao.games())
        assertEquals(tracks, catalogDao.tracks())
        assertEquals(state, importStateDao.get())
    }

    @Test
    fun `a write failure rolls back and keeps the previous catalog and user data`() =
        runTest(dispatcher) {
            importer.import(
                manifest(tracks = listOf(track("rse-weather-lab"), track("rse-route-101")))
            )
            favorite("rse-weather-lab", 1_000)
            history("rse-weather-lab", 2_000)
            val games = catalogDao.games()
            val tracks = catalogDao.tracks()
            val sources = catalogDao.sources("rse-route-101")
            val state = importStateDao.get()
            // Throws after the catalog writes and the history re-point, while moving the favorite.
            val failingDao = object : UserDataDao by userDataDao {
                override suspend fun upsertFavorite(favorite: FavoriteEntity) =
                    throw IllegalStateException("disk full")
            }

            val result = TestCatalog.importer(db, failingDao).import(
                manifest(
                    generatedAt = "2026-10-14T08:00:00Z",
                    games = listOf(game("ruby")),
                    tracks = listOf(
                        track("rse-weather-institute", replaces = listOf("rse-weather-lab")),
                        track(
                            "rse-route-101",
                            title = "Changed",
                            sources = listOf(Source.Stream("ZZZZZZZZZZZ", 0, null))
                        )
                    )
                )
            )

            assertEquals(
                CatalogImportResult.Failed(listOf(ImportError.WriteFailed("disk full"))),
                result
            )
            assertEquals(games, catalogDao.games())
            assertEquals(tracks, catalogDao.tracks())
            assertEquals(sources, catalogDao.sources("rse-route-101"))
            assertEquals(null, catalogDao.track("rse-weather-institute"))
            assertEquals(listOf("rse-weather-lab"), userDataDao.favorites().map { it.trackId })
            assertEquals(listOf("rse-weather-lab"), userDataDao.history().map { it.trackId })
            assertEquals(state, importStateDao.get())
        }

    private suspend fun favorite(trackId: String, addedAtMillis: Long) =
        userDataDao.insertFavorite(FavoriteEntity(trackId, Instant.ofEpochMilli(addedAtMillis)))

    private suspend fun history(trackId: String, playedAtMillis: Long) = userDataDao.insertHistory(
        PlayHistoryEntity(trackId = trackId, playedAt = Instant.ofEpochMilli(playedAtMillis))
    )
}
