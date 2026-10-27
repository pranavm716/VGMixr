package io.github.pranavm716.vgmixr.domain.history

import io.github.pranavm716.vgmixr.testing.FakePlayHistory
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentPlaysTest {
    private val now = Instant.parse("2026-10-26T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun TestScope.recentPlays(history: PlayHistory) = RecentPlays(
        history,
        clock,
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
    )

    @Test
    fun `knows the tracks played in the last hour, and new plays as they come`() = runTest {
        val history = FakePlayHistory(
            listOf(Play("old", now.minusSeconds(2 * 3600)), Play("a", now.minusSeconds(60)))
        )
        val recent = recentPlays(history)

        assertEquals(setOf("a"), recent.trackIds())
        history.plays.value += Play("b", now)
        assertEquals(setOf("a", "b"), recent.trackIds())
    }

    @Test
    fun `an empty history has no recent tracks`() = runTest {
        assertEquals(emptySet<String>(), recentPlays(FakePlayHistory()).trackIds())
    }
}
