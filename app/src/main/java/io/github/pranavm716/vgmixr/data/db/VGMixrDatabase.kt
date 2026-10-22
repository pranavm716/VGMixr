package io.github.pranavm716.vgmixr.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import java.time.Instant

@Database(
    entities = [
        GameEntity::class,
        TrackEntity::class,
        TrackGameEntity::class,
        SourceEntity::class,
        FavoriteEntity::class,
        PlayHistoryEntity::class,
        ImportStateEntity::class
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = DropTrackCategory::class)]
)
@TypeConverters(Converters::class)
abstract class VGMixrDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    abstract fun userDataDao(): UserDataDao

    abstract fun importStateDao(): ImportStateDao
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
