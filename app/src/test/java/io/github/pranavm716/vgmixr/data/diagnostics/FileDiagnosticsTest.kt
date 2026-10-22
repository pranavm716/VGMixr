package io.github.pranavm716.vgmixr.data.diagnostics

import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileDiagnosticsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-10-20T16:05:01.123Z"), ZoneOffset.UTC)

    private fun lines(file: File) = file.readLines()

    @Test
    fun `each event is a timestamped line`() {
        val file = File(folder.root, "playback.log")
        val log = FileDiagnostics(file, clock)

        log.log("resolve abc ok")
        log.log("now sv-x (auto)")

        assertEquals(
            listOf(
                "2026-10-20T16:05:01.123Z resolve abc ok",
                "2026-10-20T16:05:01.123Z now sv-x (auto)"
            ),
            lines(file)
        )
    }

    @Test
    fun `only the newest lines are kept`() {
        val file = File(folder.root, "playback.log")
        val log = FileDiagnostics(file, clock, maxLines = 3)

        (1..10).forEach { log.log("event $it") }

        val kept = lines(file)
        assertEquals(true, kept.size <= 3 * 2)
        assertEquals("2026-10-20T16:05:01.123Z event 10", kept.last())
        assertEquals(true, kept.none { it.endsWith("event 1") })
    }

    @Test
    fun `a new log appends to the old one, so a restart keeps what came before`() {
        val file = File(folder.root, "playback.log")
        FileDiagnostics(file, clock).log("before")
        FileDiagnostics(file, clock).log("after")

        assertEquals(2, lines(file).size)
    }
}
