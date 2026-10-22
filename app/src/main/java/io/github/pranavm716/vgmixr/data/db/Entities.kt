package io.github.pranavm716.vgmixr.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A game as of the last manifest that contained it. [position] is its index in that manifest,
 * which orders games, chips and families.
 */
@Entity(tableName = "games", indices = [Index("familyId")])
data class GameEntity(
    @PrimaryKey val id: String,
    val name: String,
    val shortName: String,
    val color: String,
    val generation: Int?,
    val platform: String,
    val familyId: String,
    val position: Int,
    val retired: Boolean
)

@Entity(tableName = "tracks", indices = [Index("familyId")])
data class TrackEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val title: String,
    // `order` is an SQL keyword.
    @ColumnInfo(name = "sortOrder") val order: Int,
    val retired: Boolean
)

/** A track's `games` subset. A track with no rows exists in all of its family's games. */
@Entity(
    tableName = "track_games",
    primaryKeys = ["trackId", "gameId"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"]
        )
    ]
)
data class TrackGameEntity(val trackId: String, val gameId: String)

/**
 * One manifest source, in priority order by [position]. Catalog facts only: replaced wholesale
 * on each import, so runtime source state must never live here.
 */
@Entity(
    tableName = "sources",
    primaryKeys = ["trackId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"]
        )
    ]
)
data class SourceEntity(
    val trackId: String,
    val position: Int,
    val type: String,
    val path: String? = null,
    val loopSampleRate: Int? = null,
    val loopIntroEnd: Int? = null,
    val loopEnd: Int? = null,
    val videoId: String? = null,
    val startMs: Long? = null,
    val endMs: Long? = null
) {
    companion object {
        const val TYPE_LOCAL = "local"
        const val TYPE_STREAM = "stream"
    }
}

// User data has no foreign keys into the catalog: it must outlive any catalog shape.

/**
 * A playlist. Favorites is built in: the row with [FAVORITES_PLAYLIST_ID], created with the
 * database, which can't be renamed or deleted.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Instant
)

/** The built-in Favorites playlist's id. */
const val FAVORITES_PLAYLIST_ID = 1L

/**
 * A track in a playlist, once per playlist. [trackId] is a catalog id or a user track's `yt:` id,
 * with no foreign key, so entries outlive the catalog.
 */
@Entity(
    tableName = "playlist_entries",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index("trackId")],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistEntryEntity(val playlistId: Long, val trackId: String, val addedAt: Instant)

/** A playlist with how many entries it has. */
data class PlaylistCountRow(val id: Long, val name: String, val trackCount: Int)

@Entity(tableName = "play_history", indices = [Index("trackId")])
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val playedAt: Instant
)

/** Single row ([id] is always 0). [generatedAt] is `Instant.toString()`, which round-trips exactly. */
@Entity(tableName = "import_state")
data class ImportStateEntity(
    @PrimaryKey val id: Int = 0,
    val generatedAt: String,
    val importedAt: Instant
)

/**
 * A stream video found broken (no playable audio, or unavailable): runtime state keyed by video id,
 * since a dead video is dead for every slice. [reason] is a `BrokenReason` key, [markedAt] when
 * it first broke, [catalogGeneratedAt] the catalog then (null before the first import).
 */
@Entity(tableName = "broken_streams")
data class BrokenStreamEntity(
    @PrimaryKey val videoId: String,
    val trackId: String,
    val reason: String,
    val detail: String?,
    val markedAt: Instant,
    val catalogGeneratedAt: String?
)
