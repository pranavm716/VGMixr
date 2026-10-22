package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrokenSkipTest {
    private val a = Source.Stream("aaaaaaaaaaa", 0, null)
    private val b = Source.Stream("bbbbbbbbbbb", 0, null)
    private val c = Source.Stream("ccccccccccc", 0, null)
    private val broken = SourceFailure.Broken(BrokenReason.NO_AUDIO, "no playable audio stream")

    @Test
    fun `a track starts at its first source whose video isn't marked broken`() {
        assertEquals(0, nextSource(listOf(a, b), from = 0, broken = emptySet()))
        assertEquals(1, nextSource(listOf(a, b), from = 0, broken = setOf(a.videoId)))
    }

    @Test
    fun `a track whose sources are all marked has none left`() {
        assertNull(nextSource(listOf(a, b), from = 0, broken = setOf(a.videoId, b.videoId)))
        assertNull(nextSource(emptyList(), from = 0, broken = emptySet()))
    }

    @Test
    fun `a slice of a marked video is skipped too, since a dead video is dead for every slice`() {
        val slice = Source.Stream(a.videoId, 60_000, 120_000)

        assertEquals(1, nextSource(listOf(slice, b), from = 0, broken = setOf(a.videoId)))
    }

    @Test
    fun `a broken source moves on to the track's next source, like any failed one`() {
        assertEquals(
            FailoverAction.TrySource(1),
            failover(broken, sourceIndex = 0, sourceCount = 2)
        )
        assertEquals(FailoverAction.SkipTrack, failover(broken, sourceIndex = 1, sourceCount = 2))
    }

    @Test
    fun `failing over skips the sources whose videos are marked`() {
        assertEquals(
            FailoverAction.TrySource(2),
            failover(broken, 0, listOf(a, b, c), setOf(a.videoId, b.videoId))
        )
        assertEquals(
            FailoverAction.SkipTrack,
            failover(SourceFailure.ThisSource, 0, listOf(a, b), setOf(b.videoId))
        )
    }

    @Test
    fun `the other failover decisions are the same with marks`() {
        val marks = setOf(b.videoId)

        assertEquals(
            FailoverAction.RetrySource,
            failover(SourceFailure.Refused, 0, listOf(a, b), marks, retried = false)
        )
        assertEquals(
            FailoverAction.Stop(ResolveFailure.NETWORK),
            failover(SourceFailure.Everything(ResolveFailure.NETWORK), 0, listOf(a, b), marks)
        )
    }

    @Test
    fun `reasons have stable names for broken json`() {
        assertEquals(listOf("unavailable", "no-audio"), BrokenReason.entries.map { it.key })
        assertEquals(BrokenReason.NO_AUDIO, BrokenReason.fromKey("no-audio"))
        assertNull(BrokenReason.fromKey("refused"))
    }
}
