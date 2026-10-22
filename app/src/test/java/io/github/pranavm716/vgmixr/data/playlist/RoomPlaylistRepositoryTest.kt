package io.github.pranavm716.vgmixr.data.playlist

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.db.FAVORITES_PLAYLIST_ID
import io.github.pranavm716.vgmixr.data.db.PlaylistEntity
import io.github.pranavm716.vgmixr.data.db.PlaylistEntryEntity
import io.github.pranavm716.vgmixr.data.db.inMemoryDatabase
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import java.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPlaylistRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = inMemoryDatabase(dispatcher)
    private val dao = db.userDataDao()
    private val repository = RoomPlaylistRepository(dao)

    @After
    fun closeDb() = db.close()

    private val emptyFavorites = PlaylistSummary(FAVORITES_PLAYLIST_ID, "Favorites", 0, true)

    @Test
    fun `a new database has Favorites with no tracks`() = runTest(dispatcher) {
        repository.playlists.test {
            assertEquals(listOf(emptyFavorites), awaitItem())
        }
    }

    @Test
    fun `counts each playlist's entries, Favorites first`() = runTest(dispatcher) {
        // Created at the epoch too, so only the Favorites rule puts it second.
        val mix = dao.insertPlaylist(PlaylistEntity(name = "Mix", createdAt = Instant.EPOCH))
        val later = dao.insertPlaylist(
            PlaylistEntity(name = "Later", createdAt = Instant.ofEpochMilli(1_000))
        )
        entry(FAVORITES_PLAYLIST_ID, "rse-route-101")
        entry(FAVORITES_PLAYLIST_ID, "yt:abc")
        entry(mix, "rse-route-101")

        repository.playlists.test {
            assertEquals(
                listOf(
                    emptyFavorites.copy(trackCount = 2),
                    PlaylistSummary(mix, "Mix", 1, false),
                    PlaylistSummary(later, "Later", 0, false)
                ),
                awaitItem()
            )
        }
    }

    private suspend fun entry(playlistId: Long, trackId: String) =
        dao.insertPlaylistEntry(PlaylistEntryEntity(playlistId, trackId, Instant.EPOCH))
}
