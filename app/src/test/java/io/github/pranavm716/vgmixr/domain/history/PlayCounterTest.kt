package io.github.pranavm716.vgmixr.domain.history

import io.github.pranavm716.vgmixr.domain.history.PlayCounter.Transition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayCounterTest {
    private val counter = PlayCounter()

    @Test
    fun `a track counts once it has played 10 seconds`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)

        assertNull(counter.onPlaying(0))
        assertNull(counter.onPlaying(9_999))
        assertEquals("a", counter.onPlaying(10_000))
    }

    @Test
    fun `a track counts at most once per visit`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(10_000)

        assertNull(counter.onPlaying(11_000))
        assertNull(counter.onEnded())
    }

    @Test
    fun `restarting within a visit doesn't count again`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(12_000)
        counter.onPlaying(0)

        assertNull(counter.onPlaying(10_000))
    }

    @Test
    fun `a short track counts when it ends`() {
        counter.onTransition("jingle", Transition.QUEUE_CHANGED)
        counter.onPlaying(0)
        counter.onPlaying(4_000)

        assertEquals("jingle", counter.onEnded())
    }

    @Test
    fun `a track skipped before 10 seconds doesn't count`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(5_000)
        counter.onTransition("b", Transition.SKIP)

        assertNull(counter.onPlaying(0))
    }

    @Test
    fun `a track that ends before its audio started doesn't count`() {
        counter.onTransition("broken", Transition.QUEUE_CHANGED)

        assertNull(counter.onEnded())
    }

    @Test
    fun `auto-advance counts the next track too`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(0)
        assertEquals("a", counter.onEnded())
        counter.onTransition("b", Transition.AUTO)

        assertEquals("b", counter.onPlaying(10_000))
    }

    @Test
    fun `coming back to a track later counts again`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(10_000)
        counter.onTransition("b", Transition.SKIP)
        counter.onTransition("a", Transition.SKIP)

        assertEquals("a", counter.onPlaying(10_000))
    }

    @Test
    fun `a repeat of the same track is a new visit`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(10_000)
        counter.onTransition("a", Transition.REPEAT)

        assertEquals("a", counter.onPlaying(10_000))
    }

    @Test
    fun `swapping to another source of the same track keeps the visit`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(10_000)
        // Failover replaces the current item with the track's next source.
        counter.onTransition("a", Transition.QUEUE_CHANGED)

        assertNull(counter.onPlaying(10_000))
    }

    @Test
    fun `nothing counts without a track`() {
        assertNull(counter.onPlaying(10_000))
        counter.onTransition(null, Transition.QUEUE_CHANGED)
        assertNull(counter.onPlaying(10_000))
        assertNull(counter.onEnded())
    }
}
