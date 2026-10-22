package io.github.pranavm716.vgmixr.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.SkipReason
import io.github.pranavm716.vgmixr.domain.playback.SourceReport
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceReportExtrasTest {
    private val track = CatalogTrack(
        id = "oras-a",
        familyId = "oras",
        title = "Battle! Elite Four",
        order = 0,
        games = emptyList(),
        sources = listOf(
            Source.Stream("aaaaaaaaaaa", 0, null),
            Source.Stream("bbbbbbbbbbb", 0, null),
            Source.Stream("ccccccccccc", 0, null)
        )
    )

    @Test
    fun `an item carries its track's source report to the controller`() {
        val report = SourceReport(
            playing = 2,
            skipped = mapOf(
                0 to
                    SkipReason.Broken(
                        BrokenReason.NO_AUDIO,
                        Instant.parse("2026-10-21T07:23:44.638Z")
                    ),
                1 to SkipReason.Refused
            )
        )

        val item = trackMediaItem(track, "ORAS", sourceIndex = 2, report = report)

        assertEquals(2, item.sourceIndex)
        assertEquals(report, item.sourceReport)
    }

    @Test
    fun `an item without sources left reports none playing`() {
        val report = SourceReport(null, mapOf(0 to SkipReason.Failed("ERROR_CODE_DECODING_FAILED")))

        val item = trackMediaItem(track, "ORAS", sourceIndex = 3, report = report)

        assertEquals(NO_SOURCE_URI, item.localConfiguration?.uri.toString())
        assertEquals(report, item.sourceReport)
    }

    @Test
    fun `an item built before reports existed plays its source with nothing skipped`() {
        val item = trackMediaItem(track, "ORAS", sourceIndex = 1)

        assertEquals(SourceReport(1, emptyMap()), item.sourceReport)
    }
}
