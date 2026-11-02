package io.github.pranavm716.vgmixr.domain.history

import io.github.pranavm716.vgmixr.testing.FakePlayHistory
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentPlaysTest {
    private val now = Instant.parse("2026-10-26T12:00:00Z")

    private fun TestScope.recentPlays(history: PlayHistory) = RecentPlays(
        history,
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
    )

    @Test
    fun `ranks every track by its last play, newest first, however long ago`() = runTest {
        val history = FakePlayHistory(
            listOf(
                Play("a", now.minusSeconds(3 * 86_400)),
                Play("b", now.minusSeconds(86_400)),
                Play("a", now.minusSeconds(3600))
            )
        )

        assertEquals(mapOf("a" to 0, "b" to 1), recentPlays(history).lastPlayed())
    }

    @Test
    fun `new plays update the ranks`() = runTest {
        val history = FakePlayHistory(listOf(Play("a", now.minusSeconds(60))))
        val recent = recentPlays(history)

        history.plays.value += Play("b", now)

        assertEquals(mapOf("b" to 0, "a" to 1), recent.lastPlayed())
    }

    @Test
    fun `an empty history ranks nothing`() = runTest {
        assertEquals(emptyMap<String, Int>(), recentPlays(FakePlayHistory()).lastPlayed())
    }
}
