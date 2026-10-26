package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.model.Source
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FailureOutcomeTest {
    private val a = Source.Stream("aaaaaaaaaaa", 0, null)
    private val b = Source.Stream("bbbbbbbbbbb", 0, null)
    private val c = Source.Stream("ccccccccccc", 0, null)
    private val noAudio = SourceFailure.Broken(BrokenReason.NO_AUDIO, "no playable audio stream")

    private fun mark(videoId: String) = BrokenMark(
        videoId,
        "oras-x",
        BrokenReason.NO_AUDIO,
        null,
        Instant.parse("2026-10-21T07:23:44Z"),
        null
    )

    private fun failed(index: Int, streams: List<Source.Stream>, retried: Boolean = false) =
        FailedSource(
            "oras-a",
            "Battle! (Elite Four)",
            index,
            streams.getOrNull(index)?.videoId,
            retried
        )

    private fun outcome(
        failure: SourceFailure,
        failed: FailedSource,
        streams: List<Source.Stream>,
        marks: Map<String, BrokenMark> = emptyMap()
    ) = onSourceFailure(failure, failed, streams, marks)

    private fun told(reason: BrokenReason?, trackSkipped: Boolean) =
        PlaybackEvent.SourceFailed("oras-a", "Battle! (Elite Four)", reason, trackSkipped)

    @Test
    fun `a newly broken video is marked, the next source plays, and the user is told`() {
        val result = outcome(noAudio, failed(0, listOf(a, b)), listOf(a, b))

        assertEquals(FailoverAction.TrySource(1), result.action)
        assertEquals(
            NewMark(a.videoId, BrokenReason.NO_AUDIO, "no playable audio stream"),
            result.mark
        )
        assertEquals(told(BrokenReason.NO_AUDIO, trackSkipped = false), result.event)
    }

    @Test
    fun `a newly broken last source skips the track, and the user is told`() {
        val result = outcome(noAudio, failed(0, listOf(a)), listOf(a))

        assertEquals(FailoverAction.SkipTrack, result.action)
        assertEquals(a.videoId, result.mark?.videoId)
        assertEquals(told(BrokenReason.NO_AUDIO, trackSkipped = true), result.event)
    }

    @Test
    fun `a video already marked is skipped quietly, keeping its first mark`() {
        val marks = mapOf(a.videoId to mark(a.videoId))

        val result = outcome(noAudio, failed(0, listOf(a, b)), listOf(a, b), marks)

        assertEquals(FailoverAction.TrySource(1), result.action)
        assertNull(result.mark)
        assertNull(result.event)
    }

    @Test
    fun `a track without any source left is skipped quietly`() {
        val sourceless = FailedSource("oras-a", "Battle! (Elite Four)", 1, null, false)

        val result =
            outcome(
                SourceFailure.ThisSource,
                sourceless,
                listOf(a),
                mapOf(a.videoId to mark(a.videoId))
            )

        assertEquals(FailoverAction.SkipTrack, result.action)
        assertNull(result.mark)
        assertNull(result.event)
    }

    @Test
    fun `moving on jumps over marked sources`() {
        val marks = mapOf(b.videoId to mark(b.videoId))

        val result = outcome(noAudio, failed(0, listOf(a, b, c)), listOf(a, b, c), marks)

        assertEquals(FailoverAction.TrySource(2), result.action)
    }

    @Test
    fun `a refused URL is retried quietly with nothing marked`() {
        val result = outcome(SourceFailure.Refused, failed(0, listOf(a)), listOf(a))

        assertEquals(FailoverAction.RetrySource, result.action)
        assertNull(result.mark)
        assertNull(result.event)
    }

    @Test
    fun `refused again after the retry, the track moves on unmarked and the user is told`() {
        val result = outcome(
            SourceFailure.Refused,
            failed(0, listOf(a, b), retried = true),
            listOf(a, b)
        )

        assertEquals(FailoverAction.TrySource(1), result.action)
        assertNull(result.mark)
        assertEquals(told(null, trackSkipped = false), result.event)
    }

    @Test
    fun `another player error moves on unmarked, and the user is told`() {
        val result = outcome(SourceFailure.ThisSource, failed(0, listOf(a)), listOf(a))

        assertEquals(FailoverAction.SkipTrack, result.action)
        assertNull(result.mark)
        assertEquals(told(null, trackSkipped = true), result.event)
    }

    @Test
    fun `network trouble stops playback and never marks`() {
        val result = outcome(
            SourceFailure.Everything(ResolveFailure.NETWORK),
            failed(0, listOf(a, b)),
            listOf(a, b)
        )

        assertEquals(FailoverAction.Stop(ResolveFailure.NETWORK), result.action)
        assertNull(result.mark)
        assertNull(result.event)
    }

    @Test
    fun `with no connection a track that isn't on the phone is skipped quietly`() {
        val streams = listOf(a, b)
        val network = SourceFailure.Everything(ResolveFailure.NETWORK)

        assertEquals(
            FailureOutcome(FailoverAction.SkipTrack, null, null),
            onSourceFailure(network, failed(0, streams), streams, emptyMap(), offline = true)
        )
        assertEquals(
            FailoverAction.Stop(ResolveFailure.NETWORK),
            onSourceFailure(
                network,
                failed(0, streams),
                streams,
                emptyMap(),
                offline = false
            ).action
        )
    }
}
