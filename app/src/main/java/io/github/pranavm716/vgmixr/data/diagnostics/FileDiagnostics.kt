package io.github.pranavm716.vgmixr.data.diagnostics

import io.github.pranavm716.vgmixr.domain.playback.PlaybackDiagnostics
import java.io.File
import java.time.Clock

/**
 * Appends timestamped events to [file], keeping roughly the newest [maxLines] (between [maxLines]
 * and twice that). The file lives in the app's external files folder, so adb can pull it from a
 * release build: `adb pull /sdcard/Android/data/io.github.pranavm716.vgmixr/files/playback.log`.
 */
class FileDiagnostics(
    private val file: File,
    private val clock: Clock,
    private val maxLines: Int = 2000
) : PlaybackDiagnostics {
    private var lines = if (file.exists()) file.useLines { it.count() } else 0

    @Synchronized
    override fun log(event: String) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText("${clock.instant()} $event\n")
            lines++
            if (lines > maxLines * 2) {
                val kept = file.readLines().takeLast(maxLines)
                file.writeText(kept.joinToString("\n", postfix = "\n"))
                lines = kept.size
            }
        }
    }
}
