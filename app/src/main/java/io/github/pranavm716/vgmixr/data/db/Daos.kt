package io.github.pranavm716.vgmixr.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Upsert
    suspend fun upsertGames(games: List<GameEntity>)

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("UPDATE games SET retired = 1")
    suspend fun retireAllGames()

    @Query("UPDATE tracks SET retired = 1")
    suspend fun retireAllTracks()

    @Insert
    suspend fun insertTrackGames(trackGames: List<TrackGameEntity>)

    @Query("DELETE FROM track_games WHERE trackId = :trackId")
    suspend fun deleteTrackGames(trackId: String)

    @Insert
    suspend fun insertSources(sources: List<SourceEntity>)

    @Query("DELETE FROM sources WHERE trackId = :trackId")
    suspend fun deleteSources(trackId: String)

    /** A family's games, retired included, in manifest position order. */
    @Query("SELECT * FROM games WHERE familyId = :familyId ORDER BY position, id")
    suspend fun gamesInFamily(familyId: String): List<GameEntity>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun game(id: String): GameEntity?

    @Query("SELECT * FROM games ORDER BY id")
    suspend fun games(): List<GameEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun track(id: String): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY id")
    suspend fun tracks(): List<TrackEntity>

    @Query("SELECT gameId FROM track_games WHERE trackId = :trackId ORDER BY gameId")
    suspend fun trackGames(trackId: String): List<String>

    @Query("SELECT * FROM sources WHERE trackId = :trackId ORDER BY position")
    suspend fun sources(trackId: String): List<SourceEntity>

    // Bulk reads for building the whole catalog in one transaction.

    @Query("SELECT * FROM games WHERE retired = 0 ORDER BY position, id")
    suspend fun activeGames(): List<GameEntity>

    @Query("SELECT * FROM tracks WHERE retired = 0 ORDER BY id")
    suspend fun activeTracks(): List<TrackEntity>

    @Query("SELECT * FROM track_games")
    suspend fun allTrackGames(): List<TrackGameEntity>

    @Query("SELECT * FROM sources ORDER BY trackId, position")
    suspend fun allSources(): List<SourceEntity>

    /**
     * Clears the broken marks of videos no stream source of an active track uses any more. A
     * subquery, not a list of ids, so it stays clear of SQLite's 999-variable limit on API 26.
     */
    @Query(
        "DELETE FROM broken_streams WHERE videoId NOT IN (" +
            "SELECT s.videoId FROM sources s JOIN tracks t ON t.id = s.trackId " +
            "WHERE s.type = 'stream' AND t.retired = 0)"
    )
    suspend fun clearUnusedBrokenMarks()
}

@Dao
interface UserDataDao {
    @Insert
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Upsert
    suspend fun upsertFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteEntity)

    @Query("SELECT * FROM favorites WHERE trackId = :trackId")
    suspend fun favorite(trackId: String): FavoriteEntity?

    @Query("SELECT * FROM favorites ORDER BY trackId")
    suspend fun favorites(): List<FavoriteEntity>

    @Insert
    suspend fun insertHistory(entry: PlayHistoryEntity)

    @Query("SELECT * FROM play_history ORDER BY id")
    suspend fun history(): List<PlayHistoryEntity>

    @Query("UPDATE play_history SET trackId = :newId WHERE trackId = :oldId")
    suspend fun repointHistory(oldId: String, newId: String)
}

@Dao
interface ImportStateDao {
    @Query("SELECT * FROM import_state WHERE id = 0")
    suspend fun get(): ImportStateEntity?

    @Upsert
    suspend fun upsert(state: ImportStateEntity)
}

@Dao
abstract class BrokenStreamDao {
    /** Inserts [mark] unless its video is already marked; true if it was new. */
    suspend fun insert(mark: BrokenStreamEntity): Boolean = insertOrIgnore(mark) != -1L

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertOrIgnore(mark: BrokenStreamEntity): Long

    /** Clears the marks of [trackId]'s stream videos, which other tracks may share. */
    @Query(
        "DELETE FROM broken_streams WHERE videoId IN (" +
            "SELECT videoId FROM sources WHERE trackId = :trackId AND type = 'stream')"
    )
    abstract suspend fun deleteForTrack(trackId: String)

    @Query("SELECT * FROM broken_streams ORDER BY markedAt, videoId")
    abstract suspend fun all(): List<BrokenStreamEntity>

    @Query("SELECT * FROM broken_streams ORDER BY markedAt, videoId")
    abstract fun observeAll(): Flow<List<BrokenStreamEntity>>
}
