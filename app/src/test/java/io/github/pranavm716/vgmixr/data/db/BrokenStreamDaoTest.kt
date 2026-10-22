package io.github.pranavm716.vgmixr.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import java.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrokenStreamDaoTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = inMemoryDatabase(dispatcher)
    private val dao = db.brokenStreamDao()

    @After
    fun closeDb() = db.close()

    private fun mark(videoId: String, trackId: String = "oras-a", at: Long = 1_000) =
        BrokenStreamEntity(
            videoId = videoId,
            trackId = trackId,
            reason = "no-audio",
            detail = "no playable audio stream",
            markedAt = Instant.ofEpochMilli(at),
            catalogGeneratedAt = "2026-10-20T08:00:00Z"
        )

    @Test
    fun `a video keeps its first mark`() = runTest(dispatcher) {
        assertTrue(dao.insert(mark("7DbGoUJkNfU", trackId = "oras-a", at = 1_000)))
        assertFalse(dao.insert(mark("7DbGoUJkNfU", trackId = "oras-b", at = 2_000)))

        assertEquals(listOf(mark("7DbGoUJkNfU", "oras-a", 1_000)), dao.all())
    }

    @Test
    fun `marks are observed oldest first, and a cleared mark goes`() = runTest(dispatcher) {
        dao.insert(mark("bbbbbbbbbbb", at = 2_000))
        dao.insert(mark("aaaaaaaaaaa", at = 1_000))

        dao.observeAll().test {
            assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), awaitItem().map { it.videoId })
            dao.delete("aaaaaaaaaaa")
            assertEquals(listOf("bbbbbbbbbbb"), awaitItem().map { it.videoId })
        }
    }
}
