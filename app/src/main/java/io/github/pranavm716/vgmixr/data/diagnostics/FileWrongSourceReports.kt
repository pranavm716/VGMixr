package io.github.pranavm716.vgmixr.data.diagnostics

import io.github.pranavm716.vgmixr.domain.catalog.CatalogImportResult
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogSync
import io.github.pranavm716.vgmixr.domain.playback.BrokenSources
import io.github.pranavm716.vgmixr.domain.report.WrongSourceReport
import io.github.pranavm716.vgmixr.domain.report.WrongSourceReports
import io.github.pranavm716.vgmixr.domain.report.pickedStream
import io.github.pranavm716.vgmixr.domain.report.retained
import io.github.pranavm716.vgmixr.domain.report.toggled
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wrong-source reports kept in `reports.json` (docs/broken.md) in the app's external files folder,
 * next to broken.json, so the pipeline pulls them over adb. The file is the only copy: dev
 * diagnostics, not user data, so not in Room or backups. [start] loads it and, after each catalog
 * import, drops the reports the new catalog fixed.
 */
class FileWrongSourceReports(
    private val file: File,
    private val catalog: CatalogRepository,
    private val broken: BrokenSources,
    private val sync: CatalogSync,
    private val generatedAt: suspend () -> String?,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher
) : WrongSourceReports {
    private val state = MutableStateFlow<List<WrongSourceReport>>(emptyList())
    override val reports: StateFlow<List<WrongSourceReport>> = state.asStateFlow()
    private val lock = Mutex()

    fun start() {
        scope.launch(io) {
            lock.withLock { state.value = read() }
            sync.status.filterIsInstance<CatalogImportResult.Imported>().collect {
                update { retained(it, catalog.catalog.first(), brokenVideos()) }
            }
        }
    }

    override suspend fun toggle(trackId: String, positionMs: Long) {
        update { reports ->
            val existing = reports.firstOrNull { it.trackId == trackId }
            val report = existing ?: catalog.catalog.first().track(trackId)
                ?.let { pickedStream(it, brokenVideos()) }
                ?.let {
                    WrongSourceReport(
                        trackId = trackId,
                        videoId = it.videoId,
                        startMs = it.startMs,
                        endMs = it.endMs,
                        positionMs = positionMs,
                        reportedAt = clock.instant(),
                        catalogGeneratedAt = generatedAt()
                    )
                }
            report?.let { toggled(reports, it) } ?: reports
        }
    }

    private suspend fun brokenVideos(): Set<String> = broken.marks.first().keys

    /** Applies [change] and writes the file if the reports changed. */
    private suspend fun update(
        change: suspend (List<WrongSourceReport>) -> List<WrongSourceReport>
    ) = withContext(io) {
        lock.withLock {
            val updated = change(state.value)
            if (updated != state.value) {
                runCatching { write(reportsJson(updated)) }
                state.value = updated
            }
        }
    }

    private fun read(): List<WrongSourceReport> =
        runCatching { parseReports(file.readText()) }.getOrDefault(emptyList())

    /** Written whole to a temporary file, then moved over the old one, so a pull never sees half. */
    private fun write(text: String) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(text)
        Files.move(
            temporary.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    }
}

/** The reports.json text for [reports], oldest first. */
fun reportsJson(reports: Collection<WrongSourceReport>): String = json.encodeToString(
    ReportsFile(
        version = REPORTS_FILE_VERSION,
        reports = reports.sortedWith(compareBy({ it.reportedAt }, { it.trackId })).map {
            ReportsFileEntry(
                trackId = it.trackId,
                videoId = it.videoId,
                startMs = it.startMs,
                endMs = it.endMs,
                positionMs = it.positionMs,
                reportedAt = it.reportedAt.toString(),
                catalogGeneratedAt = it.catalogGeneratedAt
            )
        }
    )
) + "\n"

/** The reports in a reports.json text; throws on text that isn't one. */
fun parseReports(text: String): List<WrongSourceReport> =
    json.decodeFromString<ReportsFile>(text).reports.map {
        WrongSourceReport(
            trackId = it.trackId,
            videoId = it.videoId,
            startMs = it.startMs,
            endMs = it.endMs,
            positionMs = it.positionMs,
            reportedAt = Instant.parse(it.reportedAt),
            catalogGeneratedAt = it.catalogGeneratedAt
        )
    }

private const val REPORTS_FILE_VERSION = 1

@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    explicitNulls = false
}

@Serializable
private data class ReportsFile(val version: Int, val reports: List<ReportsFileEntry>)

@Serializable
private data class ReportsFileEntry(
    val trackId: String,
    val videoId: String,
    val startMs: Long,
    val endMs: Long? = null,
    val positionMs: Long,
    val reportedAt: String,
    val catalogGeneratedAt: String? = null
)
