package io.github.pranavm716.vgmixr.data.diagnostics

import io.github.pranavm716.vgmixr.domain.playback.BrokenMark
import io.github.pranavm716.vgmixr.domain.playback.BrokenSources
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Keeps `broken.json` (docs/broken.md) in step with the broken marks in Room. It sits in the app's
 * external files folder next to playback.log, so the pipeline pulls it over adb from a release
 * build: `uv run python -m vgmixr_pipeline.broken`.
 */
class BrokenMarksMirror(
    private val file: File,
    private val sources: BrokenSources,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher
) {
    fun start() {
        scope.launch(io) {
            sources.marks.collect { marks -> runCatching { write(brokenJson(marks.values)) } }
        }
    }

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

/** The broken.json text for [marks], oldest first. */
fun brokenJson(marks: Collection<BrokenMark>): String = json.encodeToString(
    BrokenFile(
        version = BROKEN_FILE_VERSION,
        marks = marks.sortedWith(compareBy({ it.markedAt }, { it.videoId })).map {
            BrokenFileMark(
                trackId = it.trackId,
                videoId = it.videoId,
                reason = it.reason.key,
                detail = it.detail,
                markedAt = it.markedAt.toString(),
                catalogGeneratedAt = it.catalogGeneratedAt
            )
        }
    )
) + "\n"

private const val BROKEN_FILE_VERSION = 1

@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    explicitNulls = false
}

@Serializable
private data class BrokenFile(val version: Int, val marks: List<BrokenFileMark>)

@Serializable
private data class BrokenFileMark(
    val trackId: String,
    val videoId: String,
    val reason: String,
    val detail: String? = null,
    val markedAt: String,
    val catalogGeneratedAt: String? = null
)
