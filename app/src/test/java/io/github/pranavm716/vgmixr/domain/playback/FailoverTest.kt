package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class FailoverTest {
    private val stream1 = Source.Stream("aaa", 0, null)
    private val stream2 = Source.Stream("bbb", 133_000, 209_000)

    @Test
    fun `streams play in manifest order and local files count as missing until local playback`() {
        val sources = listOf(Source.Local("rse/route-101.m4a", null), stream1, stream2)

        assertEquals(listOf(stream1, stream2), playableStreams(sources))
    }

    @Test
    fun `a failed source moves on to the track's next source`() {
        assertEquals(
            FailoverAction.TrySource(1),
            failover(SourceFailure.ThisSource, sourceIndex = 0, sourceCount = 2)
        )
    }

    @Test
    fun `when the last source fails the track is skipped`() {
        assertEquals(
            FailoverAction.SkipTrack,
            failover(SourceFailure.ThisSource, sourceIndex = 1, sourceCount = 2)
        )
        assertEquals(
            FailoverAction.SkipTrack,
            failover(SourceFailure.ThisSource, sourceIndex = 0, sourceCount = 0)
        )
    }

    @Test
    fun `a failure that affects every source stops playback`() {
        assertEquals(
            FailoverAction.Stop(ResolveFailure.NETWORK),
            failover(SourceFailure.Everything(ResolveFailure.NETWORK), 0, 2)
        )
    }
}
