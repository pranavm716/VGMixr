package io.github.pranavm716.vgmixr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.pranavm716.vgmixr.domain.settings.AccentSetting
import io.github.pranavm716.vgmixr.ui.AppScreen
import io.github.pranavm716.vgmixr.ui.VGMixrApp
import io.github.pranavm716.vgmixr.ui.family.FamilyTab
import io.github.pranavm716.vgmixr.ui.messages.MessagesEffect
import io.github.pranavm716.vgmixr.ui.player.AddToPlaylist
import io.github.pranavm716.vgmixr.ui.player.MiniPlayerBar
import io.github.pranavm716.vgmixr.ui.player.MiniPlayerInSheet
import io.github.pranavm716.vgmixr.ui.player.NowPlayingHeader
import io.github.pranavm716.vgmixr.ui.player.NowPlayingScreen
import io.github.pranavm716.vgmixr.ui.playlist.EditPlaylistTab
import io.github.pranavm716.vgmixr.ui.playlist.PlaylistTab
import io.github.pranavm716.vgmixr.ui.queue.QueueScreen
import io.github.pranavm716.vgmixr.ui.settings.SettingsTab
import io.github.pranavm716.vgmixr.ui.theme.Background
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import io.github.pranavm716.vgmixr.ui.theme.color
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var accentSetting: AccentSetting

    // Held here so the notification's intent (onNewIntent) can open it.
    private val nowPlayingOpen = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nowPlayingOpen.value = savedInstanceState?.getBoolean(NOW_PLAYING_OPEN)
            ?: intent.opensNowPlaying()
        // A transparent status bar, so now playing's game tint runs up behind it.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Background.toArgb())
        )
        setContent {
            // A pick in Settings recolors the whole app at once.
            val accent by accentSetting.accent.collectAsStateWithLifecycle()
            VGMixrTheme(accent.color) {
                val snackbars = remember { SnackbarHostState() }
                MessagesEffect(snackbars)
                VGMixrApp(
                    screen = { destination, nav, reselects, addToPlaylist ->
                        AppScreen(destination, nav, reselects, addToPlaylist)
                    },
                    familyScreen = { addToPlaylist -> FamilyTab(addToPlaylist) },
                    playlistScreen = { edit, close ->
                        PlaylistTab(onEditPlaylist = edit, onClose = close)
                    },
                    editPlaylistScreen = { back -> EditPlaylistTab(onBack = back) },
                    settingsScreen = { back -> SettingsTab(onBack = back) },
                    snackbars = snackbars,
                    miniPlayer = { sheet, open -> MiniPlayerBar(sheet, onOpenNowPlaying = open) },
                    miniPlayerInSheet = { MiniPlayerInSheet() },
                    nowPlayingHeader = { close, open -> NowPlayingHeader(close, open) },
                    nowPlaying = { openQueue, addToPlaylist ->
                        NowPlayingScreen(onOpenQueue = openQueue, onAddToPlaylist = addToPlaylist)
                    },
                    addToPlaylist = { trackId, close -> AddToPlaylist(trackId, onClose = close) },
                    queue = { close -> QueueScreen(onClose = close) },
                    nowPlayingOpen = nowPlayingOpen
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.opensNowPlaying()) nowPlayingOpen.value = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(NOW_PLAYING_OPEN, nowPlayingOpen.value)
    }

    companion object {
        /** The action of the notification's intent: open the app on now playing. */
        const val ACTION_OPEN_NOW_PLAYING = "io.github.pranavm716.vgmixr.OPEN_NOW_PLAYING"

        private const val NOW_PLAYING_OPEN = "nowPlayingOpen"

        private fun Intent.opensNowPlaying() = action == ACTION_OPEN_NOW_PLAYING
    }
}
