package io.github.pranavm716.vgmixr.data.stream

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog
import io.github.pranavm716.vgmixr.data.db.inMemoryDatabase
import io.github.pranavm716.vgmixr.domain.playback.BrokenMark
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
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
class RoomBrokenSourcesTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = inMemoryDatabase(dispatcher)
    private val sources =
        RoomBrokenSources(db.brokenStreamDao(), db.importStateDao(), TestCatalog.clock)

    @After
    fun closeDb() = db.close()

    @Test
    fun `a mark records when it broke and the catalog it played from`() = runTest(dispatcher) {
        TestCatalog.importer(db).import(TestCatalog.manifest(generatedAt = "2026-10-20T08:00:00Z"))

        assertTrue(
            sources.mark("7DbGoUJkNfU", "oras-a", BrokenReason.NO_AUDIO, "no playable audio stream")
        )

        assertEquals(
            mapOf(
                "7DbGoUJkNfU" to BrokenMark(
                    videoId = "7DbGoUJkNfU",
                    trackId = "oras-a",
                    reason = BrokenReason.NO_AUDIO,
                    detail = "no playable audio stream",
                    markedAt = Instant.parse("2026-10-15T12:00:00Z"),
                    catalogGeneratedAt = "2026-10-20T08:00:00Z"
                )
            ),
            sources.marks.first()
        )
    }

    @Test
    fun `a mark before any import has no catalog`() = runTest(dispatcher) {
        sources.mark("7DbGoUJkNfU", "oras-a", BrokenReason.UNAVAILABLE, null)

        assertEquals(null, sources.marks.first().getValue("7DbGoUJkNfU").catalogGeneratedAt)
    }

    @Test
    fun `marking a marked video again keeps the first mark`() = runTest(dispatcher) {
        sources.mark("7DbGoUJkNfU", "oras-a", BrokenReason.NO_AUDIO, null)

        assertFalse(sources.mark("7DbGoUJkNfU", "oras-b", BrokenReason.UNAVAILABLE, "removed"))
        assertEquals("oras-a", sources.marks.first().getValue("7DbGoUJkNfU").trackId)
    }

    @Test
    fun `clearing a mark lets the video play again`() = runTest(dispatcher) {
        sources.mark("7DbGoUJkNfU", "oras-a", BrokenReason.NO_AUDIO, null)

        sources.marks.test {
            assertEquals(setOf("7DbGoUJkNfU"), awaitItem().keys)
            sources.clear("7DbGoUJkNfU")
            assertEquals(emptySet<String>(), awaitItem().keys)
        }
    }
}
