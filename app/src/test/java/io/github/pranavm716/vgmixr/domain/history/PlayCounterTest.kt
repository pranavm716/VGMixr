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

    @Test
    fun `the counted track is the current one once its visit counts`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(9_999)
        assertNull(counter.counted)

        counter.onPlaying(10_000)
        assertEquals("a", counter.counted)
    }

    @Test
    fun `moving to another track clears the counted track`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(10_000)

        counter.onTransition("b", Transition.SKIP)

        assertNull(counter.counted)
    }

    @Test
    fun `failover keeps the counted track`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(10_000)

        counter.onTransition("a", Transition.QUEUE_CHANGED)

        assertEquals("a", counter.counted)
    }

    @Test
    fun `leaving a track forward before it counts reports a skip, with how far in`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(0)
        counter.onPlaying(4_000)

        assertEquals(Skip("a", 4_000), counter.onTransition("b", Transition.SKIP))
    }

    @Test
    fun `tapping another track or list counts as leaving forward`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(2_000)

        assertEquals(Skip("a", 2_000), counter.onTransition("x", Transition.QUEUE_CHANGED))
    }

    @Test
    fun `leaving before the audio started is not a skip`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)

        assertNull(counter.onTransition("b", Transition.SKIP))
    }

    @Test
    fun `leaving after the track counted is not a skip`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(10_000)

        assertNull(counter.onTransition("b", Transition.SKIP))
    }

    @Test
    fun `going back to the previous track is not a skip`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(3_000)

        assertNull(counter.onTransition("z", Transition.SKIP, forward = false))
    }

    @Test
    fun `failover to the same track's next source is not a skip`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(3_000)

        assertNull(counter.onTransition("a", Transition.QUEUE_CHANGED))
    }

    @Test
    fun `a track that ends on its own or repeats is not a skip`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(3_000)
        assertNull(counter.onTransition("a", Transition.REPEAT))

        counter.onPlaying(3_000)
        assertNull(counter.onTransition("b", Transition.AUTO))
    }

    @Test
    fun `playback stopping (nothing next) is not a skip`() {
        counter.onTransition("a", Transition.QUEUE_CHANGED)
        counter.onPlaying(3_000)

        assertNull(counter.onTransition(null, Transition.QUEUE_CHANGED))
    }
}
