package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.history.Play
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartShuffleTest {
    private val tracks = (1..30).map { "t$it" }

    @Test
    fun `with no recent tracks it is a plain shuffle`() {
        assertEquals(
            tracks.shuffled(Random(1)),
            smartShuffled(tracks, { false }, Random(1))
        )
    }

    @Test
    fun `the first 10 are fresh and recent tracks land only after them`() {
        val recent = tracks.take(12).toSet()
        repeat(50) { seed ->
            val order = smartShuffled(tracks, { it in recent }, Random(seed))

            assertEquals(tracks.sorted(), order.sorted())
            assertTrue(order.take(10).none { it in recent })
        }
    }

    @Test
    fun `recent tracks can land anywhere after slot 10`() {
        val recent = setOf("t1")
        val places = (0 until 200).map { seed ->
            smartShuffled(tracks, { it in recent }, Random(seed)).indexOf("t1")
        }.toSet()

        assertTrue(places.all { it >= 10 })
        assertTrue(places.size > 10)
    }

    @Test
    fun `with too few fresh tracks, fresh ones come first, then recent ones`() {
        val small = tracks.take(15)
        val recent = small.take(8).toSet()
        val seen = mutableSetOf<List<String>>()
        repeat(20) { seed ->
            val order = smartShuffled(small, { it in recent }, Random(seed))

            assertEquals(small.drop(8).toSet(), order.take(7).toSet())
            assertEquals(recent, order.drop(7).toSet())
            seen += order.drop(7)
        }
        assertTrue("recent ones are shuffled too", seen.size > 1)
    }

    @Test
    fun `recent track ids are those played in the last hour`() {
        val now = Instant.parse("2026-10-26T12:00:00Z")
        fun ago(minutes: Int) = now.minus(minutes.minutes.toJavaDuration())
        val plays = listOf(
            Play("old", ago(61)),
            Play("edge", ago(60)),
            Play("fresh-ish", ago(59)),
            Play("now", now)
        )

        assertEquals(setOf("fresh-ish", "now"), recentTrackIds(plays, now))
        assertEquals(emptySet<String>(), recentTrackIds(emptyList(), now))
    }
}
