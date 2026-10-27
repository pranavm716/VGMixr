package io.github.pranavm716.vgmixr.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT * FROM tracks WHERE retired = 1 ORDER BY id")
    suspend fun retiredTracks(): List<TrackEntity>

    @Query("SELECT * FROM track_games")
    suspend fun allTrackGames(): List<TrackGameEntity>

    @Query("SELECT * FROM sources ORDER BY trackId, position")
    suspend fun allSources(): List<SourceEntity>

    /**
     * Clears the broken marks of videos that no stream source of an active track and no user track
     * uses any more. A subquery, not a list of ids, so it stays clear of SQLite's 999-variable
     * limit on API 26.
     */
    @Query(
        "DELETE FROM broken_streams WHERE videoId NOT IN (" +
            "SELECT s.videoId FROM sources s JOIN tracks t ON t.id = s.trackId " +
            "WHERE s.type = 'stream' AND t.retired = 0) " +
            "AND videoId NOT IN (SELECT videoId FROM user_tracks)"
    )
    suspend fun clearUnusedBrokenMarks()
}

@Dao
interface UserDataDao {
    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    /**
     * Every playlist with how many tracks its page lists: Favorites first, then by creation. A
     * deleted remix's entry (kept for Undo) isn't counted; a retired track's is, since it shows.
     */
    @Query(
        "SELECT p.id, p.name, COUNT(e.trackId) AS trackCount FROM playlists p " +
            "LEFT JOIN playlist_entries e ON e.playlistId = p.id " +
            "AND (e.trackId NOT LIKE 'yt:%' OR e.trackId IN (SELECT id FROM user_tracks)) " +
            "GROUP BY p.id " +
            "ORDER BY p.id = $FAVORITES_PLAYLIST_ID DESC, p.createdAt, p.id"
    )
    fun observePlaylists(): Flow<List<PlaylistCountRow>>

    @Insert
    suspend fun insertPlaylistEntry(entry: PlaylistEntryEntity)

    /** Adds [entry] unless its track is already in the playlist, which keeps its addedAt. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistEntryIfAbsent(entry: PlaylistEntryEntity)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeFromPlaylist(playlistId: Long, trackId: String)

    /** Renames playlist [id]; Favorites keeps its name. */
    @Query(
        "UPDATE playlists SET name = :name WHERE id = :id AND id != $FAVORITES_PLAYLIST_ID"
    )
    suspend fun renamePlaylist(id: Long, name: String)

    /** Deletes playlist [id] and, by cascade, its entries; Favorites stays. */
    @Query("DELETE FROM playlists WHERE id = :id AND id != $FAVORITES_PLAYLIST_ID")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query(
        "SELECT * FROM playlist_entries WHERE playlistId = :playlistId " +
            "ORDER BY addedAt DESC, trackId"
    )
    fun observePlaylistEntriesNewestFirst(playlistId: Long): Flow<List<PlaylistEntryEntity>>

    /** Saves playlist [id]'s sort, Favorites' too. */
    @Query("UPDATE playlists SET sortKey = :key, sortDirection = :direction WHERE id = :id")
    suspend fun setPlaylistSort(id: Long, key: String, direction: String)

    @Query("SELECT playlistId FROM playlist_entries WHERE trackId = :trackId")
    fun observePlaylistIdsContaining(trackId: String): Flow<List<Long>>

    @Query("SELECT trackId FROM playlist_entries WHERE playlistId = :playlistId")
    fun observePlaylistTrackIds(playlistId: Long): Flow<List<String>>

    @Query("SELECT DISTINCT trackId FROM playlist_entries")
    fun observeTrackIdsInPlaylists(): Flow<List<String>>

    @Upsert
    suspend fun upsertPlaylistEntry(entry: PlaylistEntryEntity)

    @Delete
    suspend fun deletePlaylistEntry(entry: PlaylistEntryEntity)

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun playlistEntry(playlistId: Long, trackId: String): PlaylistEntryEntity?

    /** A track's entries across all playlists. */
    @Query("SELECT * FROM playlist_entries WHERE trackId = :trackId ORDER BY playlistId")
    suspend fun playlistEntriesOf(trackId: String): List<PlaylistEntryEntity>

    @Query(
        "SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY addedAt, trackId"
    )
    suspend fun playlistEntries(playlistId: Long): List<PlaylistEntryEntity>

    @Insert
    suspend fun insertHistory(entry: PlayHistoryEntity)

    @Query("SELECT * FROM play_history ORDER BY id")
    suspend fun history(): List<PlayHistoryEntity>

    @Query("SELECT * FROM play_history ORDER BY id")
    fun observeHistory(): Flow<List<PlayHistoryEntity>>

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

    @Query("DELETE FROM broken_streams WHERE videoId = :videoId")
    abstract suspend fun delete(videoId: String)

    @Query("SELECT * FROM broken_streams ORDER BY markedAt, videoId")
    abstract suspend fun all(): List<BrokenStreamEntity>

    @Query("SELECT * FROM broken_streams ORDER BY markedAt, videoId")
    abstract fun observeAll(): Flow<List<BrokenStreamEntity>>
}

@Dao
interface UserTrackDao {
    /** Inserts [track]; returns -1 and changes nothing if its id is taken. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(track: UserTrackEntity): Long

    @Query("SELECT * FROM user_tracks ORDER BY addedAt DESC, id")
    fun observeNewestFirst(): Flow<List<UserTrackEntity>>

    @Query("SELECT * FROM user_tracks ORDER BY addedAt DESC, id")
    suspend fun allNewestFirst(): List<UserTrackEntity>

    @Query("UPDATE user_tracks SET remixOf = :remixOf WHERE id = :id")
    suspend fun setRemixOf(id: String, remixOf: String?)

    @Query("UPDATE user_tracks SET credit = :credit WHERE id = :id")
    suspend fun setCredit(id: String, credit: String?)

    @Query("DELETE FROM user_tracks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE user_tracks SET remixOf = :newId WHERE remixOf = :oldId")
    suspend fun repointRemixOf(oldId: String, newId: String)
}

@Dao
abstract class OfflineDao {
    @Query("SELECT * FROM offline_videos")
    abstract fun observeVideos(): Flow<List<OfflineVideoEntity>>

    @Query("SELECT * FROM offline_video_tracks")
    abstract fun observeTracks(): Flow<List<OfflineVideoTrackEntity>>

    @Query(
        "SELECT DISTINCT t.trackId FROM offline_video_tracks t " +
            "JOIN offline_videos v ON v.videoId = t.videoId WHERE v.complete"
    )
    abstract fun observePlayableTracks(): Flow<List<String>>

    @Query("SELECT videoId FROM offline_videos WHERE complete")
    abstract fun observeOnPhone(): Flow<List<String>>

    @Query("SELECT videoId FROM offline_videos WHERE complete")
    abstract suspend fun onPhone(): List<String>

    @Query("SELECT * FROM offline_videos WHERE videoId = :videoId")
    abstract suspend fun video(videoId: String): OfflineVideoEntity?

    @Upsert
    abstract suspend fun upsertVideo(video: OfflineVideoEntity)

    @Query("DELETE FROM offline_video_tracks WHERE videoId = :videoId")
    abstract suspend fun deleteTracks(videoId: String)

    @Insert
    abstract suspend fun insertTracks(tracks: List<OfflineVideoTrackEntity>)

    @Query("DELETE FROM offline_videos WHERE videoId IN (:videoIds)")
    abstract suspend fun deleteVideos(videoIds: List<String>)

    @Query("UPDATE offline_videos SET complete = 0, bytes = 0 WHERE videoId = :videoId")
    abstract suspend fun setIncomplete(videoId: String)

    @Query("UPDATE offline_videos SET complete = 1, bytes = :bytes WHERE videoId = :videoId")
    abstract suspend fun setComplete(videoId: String, bytes: Long)

    /** Deletes [remove] and writes [puts], in one transaction, so watchers see one change. */
    @Transaction
    open suspend fun apply(
        remove: List<String>,
        puts: List<Pair<OfflineVideoEntity, List<String>>>
    ) {
        if (remove.isNotEmpty()) deleteVideos(remove)
        puts.forEach { (video, trackIds) -> put(video, trackIds) }
    }

    /** Writes [video] with exactly [trackIds], keeping it finished if it already was. */
    @Transaction
    open suspend fun put(video: OfflineVideoEntity, trackIds: List<String>) {
        val old = video(video.videoId)
        upsertVideo(
            if (old?.complete == true) video.copy(complete = true, bytes = old.bytes) else video
        )
        deleteTracks(video.videoId)
        insertTracks(trackIds.distinct().map { OfflineVideoTrackEntity(video.videoId, it) })
    }
}
