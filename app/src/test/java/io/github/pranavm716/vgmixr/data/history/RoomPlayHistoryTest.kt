package io.github.pranavm716.vgmixr.data.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.db.inMemoryDatabase
import io.github.pranavm716.vgmixr.domain.history.Play
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPlayHistoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = inMemoryDatabase(dispatcher)
    private var now = Instant.parse("2026-10-22T12:00:00Z")
    private val clock = object : Clock() {
        override fun instant(): Instant = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
    }
    private val history = RoomPlayHistory(db.userDataDao(), clock)

    @After
    fun closeDb() = db.close()

    @Test
    fun `a play is recorded at the clock's time`() = runTest(dispatcher) {
        history.record("rse-route-101")

        assertEquals(
            listOf(Play("rse-route-101", Instant.parse("2026-10-22T12:00:00Z"))),
            history.plays.first()
        )
    }

    @Test
    fun `plays are kept in the order they happened, repeats included`() = runTest(dispatcher) {
        history.record("rse-route-101")
        now = Instant.parse("2026-10-22T12:03:00Z")
        history.record("rse-littleroot-town")
        now = Instant.parse("2026-10-22T12:06:00Z")
        history.record("rse-route-101")

        assertEquals(
            listOf(
                Play("rse-route-101", Instant.parse("2026-10-22T12:00:00Z")),
                Play("rse-littleroot-town", Instant.parse("2026-10-22T12:03:00Z")),
                Play("rse-route-101", Instant.parse("2026-10-22T12:06:00Z"))
            ),
            history.plays.first()
        )
    }

    @Test
    fun `plays update when a play is recorded`() = runTest(dispatcher) {
        history.plays.test {
            assertEquals(emptyList<Play>(), awaitItem())

            history.record("rse-route-101")

            assertEquals(listOf("rse-route-101"), awaitItem().map { it.trackId })
        }
    }
}
