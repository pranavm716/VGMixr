package io.github.pranavm716.vgmixr.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import java.time.Instant

@Database(
    entities = [
        GameEntity::class,
        TrackEntity::class,
        TrackGameEntity::class,
        SourceEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        PlayHistoryEntity::class,
        ImportStateEntity::class,
        BrokenStreamEntity::class
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = DropTrackCategory::class),
        // 2 to 3: broken stream marks (roadmap step 10), a new table.
        AutoMigration(from = 2, to = 3)
        // 3 to 4: MIGRATION_3_4, by hand.
    ]
)
@TypeConverters(Converters::class)
abstract class VGMixrDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    abstract fun userDataDao(): UserDataDao

    abstract fun importStateDao(): ImportStateDao

    abstract fun brokenStreamDao(): BrokenStreamDao
}

/** Creates the built-in Favorites playlist in a new database (an existing one gets it by migration). */
object SeedFavorites : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) =
        connection.execSQL(INSERT_FAVORITES_PLAYLIST)
}

/** 1 to 2: track categories are gone from the manifest (roadmap step 9b). */
@DeleteColumn(tableName = "tracks", columnName = "category")
class DropTrackCategory : AutoMigrationSpec

/** Instants as epoch millis, so timestamp columns sort numerically. */
class Converters {
    @TypeConverter
    fun fromInstant(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long): Instant = Instant.ofEpochMilli(value)
}
