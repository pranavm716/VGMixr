package io.github.pranavm716.vgmixr.domain.offline

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class LookupPacerTest {
    private val start = Instant.parse("2026-10-26T12:00:00Z")
    private val pacer = LookupPacer(
        gap = Duration.ofSeconds(6),
        hourlyCap = 3,
        botCheckPause = Duration.ofMinutes(30)
    )

    @Test
    fun `the first lookup goes at once`() {
        assertEquals(Duration.ZERO, pacer.waitBefore(start))
    }

    @Test
    fun `lookups are spaced by the gap`() {
        pacer.onLookup(start)

        assertEquals(Duration.ofSeconds(4), pacer.waitBefore(start.plusSeconds(2)))
        assertEquals(Duration.ZERO, pacer.waitBefore(start.plusSeconds(6)))
    }

    @Test
    fun `past the hourly cap the next lookup waits until the oldest is an hour old`() {
        pacer.onLookup(start)
        pacer.onLookup(start.plusSeconds(60))
        pacer.onLookup(start.plusSeconds(120))

        assertEquals(Duration.ofMinutes(50), pacer.waitBefore(start.plusSeconds(600)))
        assertEquals(Duration.ZERO, pacer.waitBefore(start.plusSeconds(3600)))
    }

    @Test
    fun `a bot check pauses lookups`() {
        pacer.onBotCheck(start)

        assertEquals(Duration.ofMinutes(30), pacer.waitBefore(start))
        assertEquals(Duration.ofMinutes(10), pacer.waitBefore(start.plus(Duration.ofMinutes(20))))
    }
}
