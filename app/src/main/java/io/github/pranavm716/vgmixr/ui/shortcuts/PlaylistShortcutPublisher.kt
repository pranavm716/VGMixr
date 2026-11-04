package io.github.pranavm716.vgmixr.ui.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pranavm716.vgmixr.MainActivity
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.di.ApplicationScope
import io.github.pranavm716.vgmixr.di.IoDispatcher
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistShortcut
import io.github.pranavm716.vgmixr.domain.playlist.playlistShortcuts
import io.github.pranavm716.vgmixr.domain.settings.AccentSetting
import io.github.pranavm716.vgmixr.ui.theme.color
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the app icon's press-and-hold menu in line with the playlists ([playlistShortcuts]) and
 * the accent: each row is a white heart (Favorites) or music note on an accent tile, and plays its
 * playlist (see [MainActivity.ACTION_PLAY_PLAYLIST]).
 */
@Singleton
class PlaylistShortcutPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlists: PlaylistRepository,
    private val accent: AccentSetting,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    fun start() {
        scope.launch(io) {
            combine(playlists.playlists, accent.accent) { lists, accent ->
                playlistShortcuts(lists) to accent.color.toArgb()
            }.distinctUntilChanged().collect { (shortcuts, color) -> publish(shortcuts, color) }
        }
    }

    private fun publish(shortcuts: List<PlaylistShortcut>, color: Int) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        manager.dynamicShortcuts = shortcuts.mapIndexed { rank, shortcut ->
            val glyph =
                if (shortcut.isFavorites) R.drawable.ic_favorite else R.drawable.ic_music_note
            ShortcutInfo.Builder(context, "playlist-${shortcut.playlistId}")
                .setShortLabel(shortcut.label)
                .setIcon(tile(glyph, color))
                .setIntent(
                    Intent(context, MainActivity::class.java)
                        .setAction(MainActivity.ACTION_PLAY_PLAYLIST)
                        .putExtra(MainActivity.EXTRA_PLAYLIST_ID, shortcut.playlistId)
                        // Reuses the open screen (onNewIntent) instead of stacking another.
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                .setRank(rank)
                .build()
        }
    }

    /** A white [glyph] on a [color] square, as an adaptive icon the launcher shapes itself. */
    private fun tile(@DrawableRes glyph: Int, color: Int): Icon {
        val bitmap = Bitmap.createBitmap(TILE_PX, TILE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        ContextCompat.getDrawable(context, glyph)?.run {
            val half = (TILE_PX * GLYPH_SHARE / 2).toInt()
            val center = TILE_PX / 2
            setBounds(center - half, center - half, center + half, center + half)
            draw(canvas)
        }
        return Icon.createWithAdaptiveBitmap(bitmap)
    }

    private companion object {
        /** An adaptive icon's full 108 dp at 4x; the launcher shows the middle two thirds. */
        const val TILE_PX = 432

        /** The glyph's share of the full tile: about half of the visible part, as the mockup. */
        const val GLYPH_SHARE = 0.35
    }
}
