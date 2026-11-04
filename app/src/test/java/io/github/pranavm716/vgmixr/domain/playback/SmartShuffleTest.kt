package io.github.pranavm716.vgmixr.domain.playback

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartShuffleTest {
    private val tracks = (1..30).map { "t$it" }

    /** Ranks for [newestFirst]: 0 is the track heard most recently. */
    private fun ranks(vararg newestFirst: String) =
        newestFirst.withIndex().associate { (i, id) -> id to i }

    private fun shuffled(items: List<String>, lastPlayed: Map<String, Int>, seed: Int) =
        smartShuffled(items, { it }, lastPlayed, Random(seed))

    @Test
    fun `with no history in the list it is a plain shuffle`() {
        assertEquals(tracks.shuffled(Random(1)), shuffled(tracks, emptyMap(), seed = 1))
        assertEquals(
            tracks.shuffled(Random(1)),
            shuffled(tracks, ranks("elsewhere"), seed = 1)
        )
    }

    @Test
    fun `heard tracks go to the back, the one heard last at the very end`() {
        repeat(20) { seed ->
            val order = shuffled(tracks, ranks("t5", "t9", "t2"), seed)

            assertEquals(tracks.sorted(), order.sorted())
            assertEquals(listOf("t2", "t9", "t5"), order.takeLast(3))
        }
    }

    @Test
    fun `at most two thirds of the list is held, the most recently heard`() {
        // All 30 heard, t1 most recently: the 20 newest are held, the 10 oldest shuffle up front.
        val newestFirst = tracks.toTypedArray()
        repeat(20) { seed ->
            val order = shuffled(tracks, ranks(*newestFirst), seed)

            assertEquals(tracks.drop(20).toSet(), order.take(10).toSet())
            assertEquals(tracks.take(20).reversed(), order.drop(10))
        }
    }

    @Test
    fun `tracks heard outside the list don't use up the cap`() {
        val small = tracks.take(3) // cap 2
        val lastPlayed = ranks("x", "y", "z", "t1", "t2", "t3")

        repeat(10) { seed ->
            val order = shuffled(small, lastPlayed, seed)

            assertEquals(listOf("t3", "t2", "t1"), order)
        }
    }

    @Test
    fun `the front stays shuffled`() {
        val fronts = (0 until 20).map { seed ->
            shuffled(tracks, ranks("t1", "t2"), seed).dropLast(2)
        }.toSet()

        assertTrue(fronts.size > 1)
    }

    @Test
    fun `repeats of one track are held together`() {
        val items = listOf("a", "b", "a", "c", "d", "e")
        repeat(10) { seed ->
            val order = shuffled(items, ranks("a"), seed)

            assertEquals(listOf("a", "a"), order.takeLast(2))
        }
    }
}
