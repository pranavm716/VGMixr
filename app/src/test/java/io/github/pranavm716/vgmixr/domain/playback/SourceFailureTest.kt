package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.model.Source
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceFailureTest {
    private val a = Source.Stream("aaaaaaaaaaa", 0, null)
    private val b = Source.Stream("bbbbbbbbbbb", 0, null)
    private val c = Source.Stream("ccccccccccc", 0, null)
    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val earlier = Instant.parse("2026-10-21T07:23:44Z")
    private val noAudio = SourceFailure.Broken(BrokenReason.NO_AUDIO, "no playable audio stream")

    private fun mark(videoId: String, at: Instant = earlier) =
        BrokenMark(videoId, "oras-x", BrokenReason.NO_AUDIO, null, at, null)

    private fun failed(
        index: Int,
        streams: List<Source.Stream>,
        retried: Boolean = false,
        report: SourceReport = SourceReport(index, emptyMap())
    ) = FailedSource("oras-a", index, streams.getOrNull(index)?.videoId, retried, report)

    private fun outcome(
        failure: SourceFailure,
        failed: FailedSource,
        streams: List<Source.Stream>,
        marks: Map<String, BrokenMark> = emptyMap()
    ) = onSourceFailure(
        failure,
        failed,
        streams,
        marks,
        now,
        errorName = "ERROR_CODE_IO_UNSPECIFIED"
    )

    @Test
    fun `a track starts at its first unmarked source, the marked ones reported as broken`() {
        assertEquals(
            SourceReport(
                playing = 1,
                skipped = mapOf(0 to SkipReason.Broken(BrokenReason.NO_AUDIO, earlier))
            ),
            startReport(listOf(a, b), mapOf(a.videoId to mark(a.videoId)))
        )
        assertEquals(SourceReport(0, emptyMap()), startReport(listOf(a, b), emptyMap()))
    }

    @Test
    fun `a track whose sources are all marked has nothing to play`() {
        val report = startReport(listOf(a), mapOf(a.videoId to mark(a.videoId)))

        assertNull(report.playing)
        assertEquals(setOf(0), report.skipped.keys)
    }

    @Test
    fun `a newly broken video is marked, the next source plays, and the user is told`() {
        val result = outcome(noAudio, failed(0, listOf(a, b)), listOf(a, b))

        assertEquals(FailoverAction.TrySource(1), result.action)
        assertEquals(
            NewMark(a.videoId, BrokenReason.NO_AUDIO, "no playable audio stream"),
            result.mark
        )
        assertEquals(
            SourceReport(1, mapOf(0 to SkipReason.Broken(BrokenReason.NO_AUDIO, now))),
            result.report
        )
        assertEquals(
            PlaybackEvent.SourceFailed("oras-a", BrokenReason.NO_AUDIO, trackSkipped = false),
            result.event
        )
    }

    @Test
    fun `a newly broken last source skips the track, and the user is told`() {
        val result = outcome(noAudio, failed(0, listOf(a)), listOf(a))

        assertEquals(FailoverAction.SkipTrack, result.action)
        assertEquals(a.videoId, result.mark?.videoId)
        assertNull(result.report.playing)
        assertEquals(
            PlaybackEvent.SourceFailed("oras-a", BrokenReason.NO_AUDIO, trackSkipped = true),
            result.event
        )
    }

    @Test
    fun `a video already marked is skipped quietly, keeping its first mark`() {
        val marks = mapOf(a.videoId to mark(a.videoId))

        val result = outcome(noAudio, failed(0, listOf(a, b)), listOf(a, b), marks)

        assertEquals(FailoverAction.TrySource(1), result.action)
        assertNull(result.mark)
        assertNull(result.event)
        assertEquals(SkipReason.Broken(BrokenReason.NO_AUDIO, earlier), result.report.skipped[0])
    }

    @Test
    fun `a track without any source left is skipped quietly`() {
        val streams = listOf(a)
        val marks = mapOf(a.videoId to mark(a.videoId))
        val sourceless = FailedSource("oras-a", 1, null, false, startReport(streams, marks))

        val result = outcome(SourceFailure.ThisSource, sourceless, streams, marks)

        assertEquals(FailoverAction.SkipTrack, result.action)
        assertNull(result.mark)
        assertNull(result.event)
        assertEquals(startReport(streams, marks), result.report)
    }

    @Test
    fun `moving on jumps over marked sources and reports them`() {
        val marks = mapOf(b.videoId to mark(b.videoId))

        val result = outcome(noAudio, failed(0, listOf(a, b, c)), listOf(a, b, c), marks)

        assertEquals(FailoverAction.TrySource(2), result.action)
        assertEquals(
            SourceReport(
                2,
                mapOf(
                    0 to SkipReason.Broken(BrokenReason.NO_AUDIO, now),
                    1 to SkipReason.Broken(BrokenReason.NO_AUDIO, earlier)
                )
            ),
            result.report
        )
    }

    @Test
    fun `a refused URL is retried quietly with nothing marked`() {
        val result = outcome(SourceFailure.Refused, failed(0, listOf(a)), listOf(a))

        assertEquals(FailoverAction.RetrySource, result.action)
        assertNull(result.mark)
        assertNull(result.event)
        assertEquals(SourceReport(0, emptyMap()), result.report)
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
        assertEquals(SourceReport(1, mapOf(0 to SkipReason.Refused)), result.report)
        assertEquals(PlaybackEvent.SourceFailed("oras-a", null, trackSkipped = false), result.event)
    }

    @Test
    fun `another player error moves on unmarked, reporting the error`() {
        val result = outcome(SourceFailure.ThisSource, failed(0, listOf(a)), listOf(a))

        assertEquals(FailoverAction.SkipTrack, result.action)
        assertNull(result.mark)
        assertEquals(
            mapOf(0 to SkipReason.Failed("ERROR_CODE_IO_UNSPECIFIED")),
            result.report.skipped
        )
        assertEquals(PlaybackEvent.SourceFailed("oras-a", null, trackSkipped = true), result.event)
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
        assertEquals(SourceReport(0, emptyMap()), result.report)
    }

    @Test
    fun `earlier skips stay in the report as the track moves on`() {
        val report = SourceReport(1, mapOf(0 to SkipReason.Refused))

        val result = outcome(noAudio, failed(1, listOf(a, b, c), report = report), listOf(a, b, c))

        assertEquals(
            mapOf(0 to SkipReason.Refused, 1 to SkipReason.Broken(BrokenReason.NO_AUDIO, now)),
            result.report.skipped
        )
        assertEquals(2, result.report.playing)
    }
}
