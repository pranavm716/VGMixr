package io.github.pranavm716.vgmixr.data.playlist

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.db.FAVORITES_PLAYLIST_ID
import io.github.pranavm716.vgmixr.data.db.PlaylistEntity
import io.github.pranavm716.vgmixr.data.db.PlaylistEntryEntity
import io.github.pranavm716.vgmixr.data.db.inMemoryDatabase
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
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
    private val clock = MutableClock(Instant.ofEpochMilli(1_000))
    private val repository = RoomPlaylistRepository(dao, clock)

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

    @Test
    fun `favoriting adds the track to Favorites, unfavoriting takes it out`() =
        runTest(dispatcher) {
            repository.favorites.test {
                assertEquals(emptySet<String>(), awaitItem())

                repository.setFavorite("rse-route-101", true)
                assertEquals(setOf("rse-route-101"), awaitItem())
                assertEquals(
                    listOf(
                        PlaylistEntryEntity(
                            FAVORITES_PLAYLIST_ID,
                            "rse-route-101",
                            Instant.ofEpochMilli(1_000)
                        )
                    ),
                    dao.playlistEntries(FAVORITES_PLAYLIST_ID)
                )

                repository.setFavorite("rse-route-101", false)
                assertEquals(emptySet<String>(), awaitItem())
            }
        }

    @Test
    fun `favoriting a favorite keeps when it was first added`() = runTest(dispatcher) {
        repository.setFavorite("rse-route-101", true)
        clock.instant = Instant.ofEpochMilli(5_000)

        repository.setFavorite("rse-route-101", true)

        assertEquals(
            listOf(Instant.ofEpochMilli(1_000)),
            dao.playlistEntries(FAVORITES_PLAYLIST_ID).map { it.addedAt }
        )
    }

    @Test
    fun `unfavoriting a track that isn't a favorite does nothing`() = runTest(dispatcher) {
        repository.setFavorite("rse-route-101", false)

        assertEquals(emptyList<PlaylistEntryEntity>(), dao.playlistEntries(FAVORITES_PLAYLIST_ID))
    }

    private class MutableClock(var instant: Instant) : Clock() {
        override fun instant(): Instant = instant

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    private suspend fun entry(playlistId: Long, trackId: String) =
        dao.insertPlaylistEntry(PlaylistEntryEntity(playlistId, trackId, Instant.EPOCH))
}
