package io.github.pranavm716.vgmixr.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

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

    /** A family's games, retired included, in the order family name and label derive from. */
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
}

@Dao
interface UserDataDao {
    @Insert
    suspend fun insertFavorite(favorite: FavoriteEntity)

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
