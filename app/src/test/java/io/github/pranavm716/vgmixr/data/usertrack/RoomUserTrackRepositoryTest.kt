package io.github.pranavm716.vgmixr.data.usertrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.db.FAVORITES_PLAYLIST_ID
import io.github.pranavm716.vgmixr.data.db.PlayHistoryEntity
import io.github.pranavm716.vgmixr.data.db.PlaylistEntryEntity
import io.github.pranavm716.vgmixr.data.db.inMemoryDatabase
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrack
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomUserTrackRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = inMemoryDatabase(dispatcher)
    private val repository = RoomUserTrackRepository(db.userTrackDao())

    @After
    fun closeDb() = db.close()

    private fun track(videoId: String, addedAt: Long = 1_000, remixOf: String? = null) = UserTrack(
        videoId = videoId,
        title = "Route 101 Remix $videoId",
        channel = "GlitchxCity",
        durationMs = 185_000,
        addedAt = Instant.ofEpochMilli(addedAt),
        remixOf = remixOf
    )

    @Test
    fun `its id is yt and the video id`() {
        assertEquals("yt:AAAAAAAAAAA", track("AAAAAAAAAAA").id)
    }

    @Test
    fun `lists added tracks newest first, every field kept`() = runTest(dispatcher) {
        val sliced = track("BBBBBBBBBBB", addedAt = 2_000, remixOf = "rse-route-101")
            .copy(startMs = 61_000, endMs = 245_000)
        repository.userTracks.test {
            assertEquals(emptyList<UserTrack>(), awaitItem())
            assertTrue(repository.add(track("AAAAAAAAAAA", addedAt = 1_000)))
            assertEquals(listOf(track("AAAAAAAAAAA")), awaitItem())
            assertTrue(repository.add(sliced))
            assertEquals(listOf(sliced, track("AAAAAAAAAAA")), awaitItem())
        }
    }

    @Test
    fun `adding a video again changes nothing`() = runTest(dispatcher) {
        repository.add(track("AAAAAAAAAAA", addedAt = 1_000))

        assertFalse(repository.add(track("AAAAAAAAAAA", addedAt = 2_000, remixOf = "rse-x")))

        assertEquals(listOf(track("AAAAAAAAAAA", addedAt = 1_000)), repository.userTracks.first())
    }

    @Test
    fun `sets and clears what a track remixes`() = runTest(dispatcher) {
        repository.add(track("AAAAAAAAAAA"))

        repository.setRemixOf("yt:AAAAAAAAAAA", "rse-route-101")
        assertEquals("rse-route-101", repository.userTracks.first().single().remixOf)

        repository.setRemixOf("yt:AAAAAAAAAAA", null)
        assertEquals(null, repository.userTracks.first().single().remixOf)
    }

    @Test
    fun `sets and clears a track's credit`() = runTest(dispatcher) {
        repository.add(track("AAAAAAAAAAA"))

        repository.setCredit("yt:AAAAAAAAAAA", "Super Smash Bros. Brawl")
        assertEquals("Super Smash Bros. Brawl", repository.userTracks.first().single().credit)

        repository.setCredit("yt:AAAAAAAAAAA", null)
        assertEquals(null, repository.userTracks.first().single().credit)
    }

    @Test
    fun `adding a track back keeps its credit`() = runTest(dispatcher) {
        val credited = track("AAAAAAAAAAA").copy(credit = "Super Smash Bros. Brawl")
        repository.add(credited)

        assertEquals(credited, repository.userTracks.first().single())
    }

    @Test
    fun `deleting a track keeps its playlist entries and history`() = runTest(dispatcher) {
        val userData = db.userDataDao()
        repository.add(track("AAAAAAAAAAA"))
        val entry = PlaylistEntryEntity(FAVORITES_PLAYLIST_ID, "yt:AAAAAAAAAAA", Instant.EPOCH)
        userData.insertPlaylistEntry(entry)
        userData.insertHistory(
            PlayHistoryEntity(trackId = "yt:AAAAAAAAAAA", playedAt = Instant.EPOCH)
        )

        repository.delete("yt:AAAAAAAAAAA")

        assertEquals(emptyList<UserTrack>(), repository.userTracks.first())
        assertEquals(listOf(entry), userData.playlistEntries(FAVORITES_PLAYLIST_ID))
        assertEquals(listOf("yt:AAAAAAAAAAA"), userData.history().map { it.trackId })
    }

    @Test
    fun `a deleted track added back as it was keeps its place`() = runTest(dispatcher) {
        repository.add(track("AAAAAAAAAAA", addedAt = 1_000))
        repository.add(track("BBBBBBBBBBB", addedAt = 2_000))
        val deleted = repository.userTracks.first().last()

        repository.delete(deleted.id)
        repository.add(deleted)

        assertEquals(
            listOf("yt:BBBBBBBBBBB", "yt:AAAAAAAAAAA"),
            repository.userTracks.first().map { it.id }
        )
    }
}
