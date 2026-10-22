package io.github.pranavm716.vgmixr.playback

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import io.github.pranavm716.vgmixr.ui.components.GAME_TINT_ALPHA
import io.github.pranavm716.vgmixr.ui.components.tintColor
import io.github.pranavm716.vgmixr.ui.theme.Background
import java.io.File

/**
 * The media notification's artwork until families have covers: the mini player's tint, the track's
 * games' colors left to right over the app's background. The system draws the notification's
 * background from it.
 */
class NotificationArtwork(private val dir: File) {
    /** A file URI for the artwork of a track in games of [gameColors]; each one is drawn once. */
    fun uriFor(gameColors: List<String>): Uri {
        val colors = artworkColors(gameColors)
        val name = colors.joinToString("-", prefix = "v1-", postfix = ".png") { "%08x".format(it) }
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
 * The artwork's gradient stops as opaque ARGB: each game's tint at the mini player's strength over
 * the app background. No games gives the plain background.
 */
fun artworkColors(gameColors: List<String>): List<Int> = gameColors.map {
    tintColor(it).copy(alpha = GAME_TINT_ALPHA).compositeOver(Background).toArgb()
}
    .ifEmpty { listOf(Background.toArgb()) }

private const val SIZE_PX = 512
