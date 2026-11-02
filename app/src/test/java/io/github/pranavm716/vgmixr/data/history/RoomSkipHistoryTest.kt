package io.github.pranavm716.vgmixr.data.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.data.db.SkipEntity
import io.github.pranavm716.vgmixr.data.db.inMemoryDatabase
import io.github.pranavm716.vgmixr.domain.history.Skip
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomSkipHistoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = inMemoryDatabase(dispatcher)
    private val now = Instant.parse("2026-11-02T12:00:00Z")
    private val skips = RoomSkipHistory(db.userDataDao(), Clock.fixed(now, ZoneOffset.UTC))

    @After
    fun closeDb() = db.close()

    @Test
    fun `a skip is recorded with the clock's time, how far in and where it played from`() =
        runTest(dispatcher) {
            skips.record(Skip("rse-route-101", 4_000), QueueContext.Playlist(2))
            skips.record(Skip("rse-route-103", 1_000), null)

            assertEquals(
                listOf(
                    SkipEntity(1, "rse-route-101", now, 4_000, "playlist:2"),
                    SkipEntity(2, "rse-route-103", now, 1_000, null)
                ),
                db.userDataDao().skips()
            )
        }
}
