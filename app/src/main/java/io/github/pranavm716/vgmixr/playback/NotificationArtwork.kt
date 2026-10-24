package io.github.pranavm716.vgmixr.playback

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.ui.components.tintColor
import io.github.pranavm716.vgmixr.ui.theme.Background
import java.io.File

/**
 * The media notification's artwork until families have covers: the track's games' tint colors left
 * to right, as the app's gradient runs. The system picks the notification's colors from it and
 * darkens them itself, so the colors are full strength: at the app's faint 25% over its dark
 * background they came out a muddy brown.
 */
class NotificationArtwork(private val dir: File) {
    /** A file URI for [track]'s artwork: its video's thumbnail, else its games' colors. */
    fun uriFor(track: CatalogTrack): Uri =
        track.thumbnail?.let { Uri.fromFile(File(it)) } ?: uriFor(track.games.map { it.color })

    /** A file URI for the artwork of a track in games of [gameColors]; each one is drawn once. */
    fun uriFor(gameColors: List<String>): Uri {
        val colors = artworkColors(gameColors)
        val name = colors.joinToString("-", prefix = "v2-", postfix = ".png") { "%08x".format(it) }
        val file = File(dir, name)
        if (!file.exists()) write(file, colors)
        return Uri.fromFile(file)
    }

    private fun write(file: File, colors: List<Int>) {
        dir.mkdirs()
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        if (colors.size == 1) {
            bitmap.eraseColor(colors.single())
        } else {
            val paint = Paint().apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    SIZE_PX.toFloat(),
                    0f,
                    colors.toIntArray(),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            Canvas(bitmap).drawRect(0f, 0f, SIZE_PX.toFloat(), SIZE_PX.toFloat(), paint)
        }
        // Written aside and moved in, so the player never loads half a file.
        val partial = File(dir, "${file.name}.part")
        partial.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        partial.renameTo(file)
        bitmap.recycle()
    }
}

/**
 * The artwork's gradient stops as opaque ARGB: each game's tint color (`tintColor`, its hue at an
 * even lightness). No games gives the plain app background.
 */
fun artworkColors(gameColors: List<String>): List<Int> =
    gameColors.map { tintColor(it).toArgb() }.ifEmpty { listOf(Background.toArgb()) }

private const val SIZE_PX = 512
