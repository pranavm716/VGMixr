package io.github.pranavm716.vgmixr.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class VGMixrDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    abstract fun userDataDao(): UserDataDao

    abstract fun importStateDao(): ImportStateDao
}

/** Instants as epoch millis, so timestamp columns sort numerically. */
class Converters {
    @TypeConverter
    fun fromInstant(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long): Instant = Instant.ofEpochMilli(value)
}
