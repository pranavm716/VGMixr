package io.github.pranavm716.vgmixr.domain.report

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.playback.nextSource
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The user heard [trackId] play the wrong audio from [videoId] (its slice [startMs] to [endMs]),
 * [positionMs] into the track. A note for the pipeline: playback never changes. [catalogGeneratedAt]
 * is the catalog then (null before the first import). Format: docs/broken.md, `reports.json`.
 */
data class WrongSourceReport(
    val trackId: String,
    val videoId: String,
    val startMs: Long,
    val endMs: Long?,
    val positionMs: Long,
    val reportedAt: Instant,
    val catalogGeneratedAt: String?
)

/** Wrong-source reports, at most one per track, oldest first. */
interface WrongSourceReports {
    val reports: Flow<List<WrongSourceReport>>

    /** Reports the stream [trackId] plays, [positionMs] in; takes the report back if it has one. */
    suspend fun toggle(trackId: String, positionMs: Long)
}

/** The stream [track] plays: its first one not in [broken], as the player picks (`nextSource`). */
fun pickedStream(track: CatalogTrack, broken: Set<String>): Source.Stream? {
    val streams = track.sources.filterIsInstance<Source.Stream>()
    return nextSource(streams, 0, broken)?.let(streams::get)
}

/** [reports] whose track still picks the reported video in [catalog]: the others were fixed. */
fun retained(
    reports: List<WrongSourceReport>,
    catalog: Catalog,
    broken: Set<String>
): List<WrongSourceReport> = reports.filter { report ->
    catalog.track(report.trackId)?.let { pickedStream(it, broken)?.videoId } == report.videoId
}

/** [reports] with [report] added, or with its track's report removed if it had one. */
fun toggled(reports: List<WrongSourceReport>, report: WrongSourceReport): List<WrongSourceReport> =
    if (reports.any { it.trackId == report.trackId }) {
        reports.filterNot { it.trackId == report.trackId }
    } else {
        reports + report
    }
