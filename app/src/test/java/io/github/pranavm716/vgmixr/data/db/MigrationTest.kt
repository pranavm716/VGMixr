package io.github.pranavm716.vgmixr.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    // The driver-based helper: the SupportSQLite one rejects Windows database paths.
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = ApplicationProvider.getApplicationContext<Context>().getDatabasePath(DB),
        driver = AndroidSQLiteDriver(),
        databaseClass = VGMixrDatabase::class
    )

    @Test
    fun `1 to 2 drops the track category and keeps every row`() {
        helper.createDatabase(1).use { db ->
            db.execSQL(
                "INSERT INTO games (id, name, shortName, color, generation, platform, familyId, " +
                    "position, retired) VALUES ('ruby', 'Ruby', 'R', '#C8372D', 3, 'GBA', 'rse', 0, 0)"
            )
            db.execSQL(
                "INSERT INTO tracks (id, familyId, title, category, sortOrder, retired) " +
                    "VALUES ('rse-drought', 'rse', 'Drought', 'EVENT', 90, 1)"
            )
            db.execSQL("INSERT INTO track_games (trackId, gameId) VALUES ('rse-drought', 'ruby')")
            db.execSQL(
                "INSERT INTO sources (trackId, position, type, videoId, startMs, endMs) " +
                    "VALUES ('rse-drought', 0, 'stream', 'YYYYYYYYYYY', 1000, 2000)"
            )
            db.execSQL("INSERT INTO favorites (trackId, addedAt) VALUES ('rse-drought', 111)")
            db.execSQL("INSERT INTO play_history (trackId, playedAt) VALUES ('rse-drought', 222)")
            db.execSQL(
                "INSERT INTO import_state (id, generatedAt, importedAt) " +
                    "VALUES (0, '2026-10-14T08:00:00Z', 333)"
            )
        }

        helper.runMigrationsAndValidate(2).use { db ->
            assertEquals(
                listOf("id", "familyId", "title", "sortOrder", "retired"),
                db.rows("PRAGMA table_info(tracks)").map { it[1] }
            )
            assertEquals(
                listOf(listOf("rse-drought", "rse", "Drought", "90", "1")),
                db.rows("SELECT id, familyId, title, sortOrder, retired FROM tracks")
            )
            assertEquals(
                listOf(listOf("ruby", "Ruby", "R", "#C8372D", "3", "GBA", "rse", "0", "0")),
                db.rows("SELECT * FROM games")
            )
            assertEquals(
                listOf(listOf("rse-drought", "ruby")),
                db.rows("SELECT trackId, gameId FROM track_games")
            )
            assertEquals(
                listOf(listOf("rse-drought", "0", "stream", "YYYYYYYYYYY", "1000", "2000")),
                db.rows("SELECT trackId, position, type, videoId, startMs, endMs FROM sources")
            )
            assertEquals(
                listOf(listOf("rse-drought", "111")),
                db.rows("SELECT trackId, addedAt FROM favorites")
            )
            assertEquals(
                listOf(listOf("rse-drought", "222")),
                db.rows("SELECT trackId, playedAt FROM play_history")
            )
            assertEquals(
                listOf(listOf("0", "2026-10-14T08:00:00Z", "333")),
                db.rows("SELECT id, generatedAt, importedAt FROM import_state")
            )
        }
    }

    @Test
    fun `2 to 3 adds an empty broken_streams table and keeps every row`() {
        helper.createDatabase(2).use { db ->
            db.execSQL(
                "INSERT INTO games (id, name, shortName, color, generation, platform, familyId, " +
                    "position, retired) VALUES ('ruby', 'Ruby', 'R', '#C8372D', 3, 'GBA', 'rse', 0, 0)"
            )
            db.execSQL(
                "INSERT INTO tracks (id, familyId, title, sortOrder, retired) " +
                    "VALUES ('rse-drought', 'rse', 'Drought', 90, 1)"
            )
            db.execSQL("INSERT INTO track_games (trackId, gameId) VALUES ('rse-drought', 'ruby')")
            db.execSQL(
                "INSERT INTO sources (trackId, position, type, videoId, startMs, endMs) " +
                    "VALUES ('rse-drought', 0, 'stream', 'YYYYYYYYYYY', 1000, 2000)"
            )
            db.execSQL("INSERT INTO favorites (trackId, addedAt) VALUES ('rse-drought', 111)")
            db.execSQL("INSERT INTO play_history (trackId, playedAt) VALUES ('rse-drought', 222)")
            db.execSQL(
                "INSERT INTO import_state (id, generatedAt, importedAt) " +
                    "VALUES (0, '2026-10-14T08:00:00Z', 333)"
            )
        }

        helper.runMigrationsAndValidate(3).use { db ->
            assertEquals(
                listOf("videoId", "trackId", "reason", "detail", "markedAt", "catalogGeneratedAt"),
                db.rows("PRAGMA table_info(broken_streams)").map { it[1] }
            )
            assertEquals(emptyList<List<String?>>(), db.rows("SELECT * FROM broken_streams"))
            assertEquals(
                listOf(listOf("rse-drought", "rse", "Drought", "90", "1")),
                db.rows("SELECT id, familyId, title, sortOrder, retired FROM tracks")
            )
            assertEquals(
                listOf(listOf("ruby", "Ruby", "R", "#C8372D", "3", "GBA", "rse", "0", "0")),
                db.rows("SELECT * FROM games")
            )
            assertEquals(
                listOf(listOf("rse-drought", "ruby")),
                db.rows("SELECT trackId, gameId FROM track_games")
            )
            assertEquals(
                listOf(listOf("rse-drought", "0", "stream", "YYYYYYYYYYY", "1000", "2000")),
                db.rows("SELECT trackId, position, type, videoId, startMs, endMs FROM sources")
            )
            assertEquals(
                listOf(listOf("rse-drought", "111")),
                db.rows("SELECT trackId, addedAt FROM favorites")
            )
            assertEquals(
                listOf(listOf("rse-drought", "222")),
                db.rows("SELECT trackId, playedAt FROM play_history")
            )
            assertEquals(
                listOf(listOf("0", "2026-10-14T08:00:00Z", "333")),
                db.rows("SELECT id, generatedAt, importedAt FROM import_state")
            )
        }
    }

    @Test
    fun `3 to 4 moves favorites into the built-in Favorites playlist`() {
        helper.createDatabase(3).use { db ->
            db.execSQL("INSERT INTO favorites (trackId, addedAt) VALUES ('rse-drought', 111)")
            db.execSQL("INSERT INTO favorites (trackId, addedAt) VALUES ('yt:abc', 222)")
            db.execSQL("INSERT INTO play_history (trackId, playedAt) VALUES ('rse-drought', 333)")
        }

        helper.runMigrationsAndValidate(4, listOf(MIGRATION_3_4)).use { db ->
            assertEquals(
                listOf(listOf("1", "Favorites", "0")),
                db.rows("SELECT id, name, createdAt FROM playlists")
            )
            assertEquals(
                listOf(listOf("1", "rse-drought", "111"), listOf("1", "yt:abc", "222")),
                db.rows(
                    "SELECT playlistId, trackId, addedAt FROM playlist_entries ORDER BY trackId"
                )
            )
            assertEquals(
                emptyList<List<String?>>(),
                db.rows("SELECT name FROM sqlite_master WHERE name = 'favorites'")
            )
            assertEquals(
                listOf(listOf("rse-drought", "333")),
                db.rows("SELECT trackId, playedAt FROM play_history")
            )
        }
    }

    @Test
    fun `3 to 4 creates Favorites with no favorites to move`() {
        helper.createDatabase(3).close()

        helper.runMigrationsAndValidate(4, listOf(MIGRATION_3_4)).use { db ->
            assertEquals(
                listOf(listOf("1", "Favorites")),
                db.rows("SELECT id, name FROM playlists")
            )
            assertEquals(emptyList<List<String?>>(), db.rows("SELECT * FROM playlist_entries"))
        }
    }

    @Test
    fun `4 to 5 adds an empty user_tracks table and keeps playlists and history`() {
        helper.createDatabase(4).use { db ->
            db.execSQL(
                "INSERT INTO playlist_entries (playlistId, trackId, addedAt) " +
                    "VALUES (1, 'yt:AAAAAAAAAAA', 111)"
            )
            db.execSQL(
                "INSERT INTO play_history (trackId, playedAt) VALUES ('yt:AAAAAAAAAAA', 222)"
            )
        }

        helper.runMigrationsAndValidate(5).use { db ->
            assertEquals(
                listOf(
                    "id", "videoId", "startMs", "endMs", "title", "channel", "durationMs",
                    "addedAt", "remixOf"
                ),
                db.rows("PRAGMA table_info(user_tracks)").map { it[1] }
            )
            assertEquals(emptyList<List<String?>>(), db.rows("SELECT * FROM user_tracks"))
            assertEquals(
                listOf(listOf("1", "yt:AAAAAAAAAAA", "111")),
                db.rows("SELECT playlistId, trackId, addedAt FROM playlist_entries")
            )
            assertEquals(
                listOf(listOf("yt:AAAAAAAAAAA", "222")),
                db.rows("SELECT trackId, playedAt FROM play_history")
            )
        }
    }

    @Test
    fun `5 to 6 adds a playlist's sort, unset, and keeps its rows`() {
        helper.createDatabase(5).use { db ->
            db.execSQL("INSERT INTO playlists (id, name, createdAt) VALUES (7, 'Hype', 333)")
            db.execSQL(
                "INSERT INTO playlist_entries (playlistId, trackId, addedAt) " +
                    "VALUES (7, 'rse-route-101', 111)"
            )
        }

        helper.runMigrationsAndValidate(6).use { db ->
            assertEquals(
                listOf(listOf("7", "Hype", null, null)),
                db.rows("SELECT id, name, sortKey, sortDirection FROM playlists ORDER BY id")
            )
            assertEquals(
                listOf(listOf("7", "rse-route-101", "111")),
                db.rows("SELECT playlistId, trackId, addedAt FROM playlist_entries")
            )
        }
    }

    private fun SQLiteConnection.rows(sql: String): List<List<String?>> = prepare(sql).use { row ->
        buildList {
            while (row.step()) {
                add(
                    (0 until row.getColumnCount()).map {
                        if (row.isNull(it)) null else row.getText(it)
                    }
                )
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
