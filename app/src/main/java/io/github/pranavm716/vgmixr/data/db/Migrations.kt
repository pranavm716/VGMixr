package io.github.pranavm716.vgmixr.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL

/**
 * 3 to 4: favorites become the built-in Favorites playlist (roadmap step 12). Rows can't move in an
 * auto migration, hence this one.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    // The app's database runs migrations on SupportSQLite, the migration tests on a driver.
    override fun migrate(db: SupportSQLiteDatabase) = MIGRATION_3_4_SQL.forEach(db::execSQL)

    override fun migrate(connection: SQLiteConnection) =
        MIGRATION_3_4_SQL.forEach(connection::execSQL)
}

private val MIGRATION_3_4_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `playlist_entries` (`playlistId` INTEGER NOT NULL, " +
        "`trackId` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, " +
        "PRIMARY KEY(`playlistId`, `trackId`), FOREIGN KEY(`playlistId`) " +
        "REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_playlist_entries_trackId` ON `playlist_entries` (`trackId`)",
    INSERT_FAVORITES_PLAYLIST,
    "INSERT INTO playlist_entries (playlistId, trackId, addedAt) " +
        "SELECT $FAVORITES_PLAYLIST_ID, trackId, addedAt FROM favorites",
    "DROP TABLE favorites"
)

/** Creates the built-in Favorites playlist, dated at the epoch so it sorts before any other. */
const val INSERT_FAVORITES_PLAYLIST =
    "INSERT INTO playlists (id, name, createdAt) VALUES ($FAVORITES_PLAYLIST_ID, 'Favorites', 0)"
