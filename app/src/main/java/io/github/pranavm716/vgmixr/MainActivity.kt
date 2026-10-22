package io.github.pranavm716.vgmixr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import dagger.hilt.android.AndroidEntryPoint
import io.github.pranavm716.vgmixr.ui.AppScreen
import io.github.pranavm716.vgmixr.ui.VGMixrApp
import io.github.pranavm716.vgmixr.ui.family.FamilyTab
import io.github.pranavm716.vgmixr.ui.messages.MessagesEffect
import io.github.pranavm716.vgmixr.ui.player.MiniPlayerBar
import io.github.pranavm716.vgmixr.ui.player.MiniPlayerInSheet
import io.github.pranavm716.vgmixr.ui.player.NowPlayingHeader
import io.github.pranavm716.vgmixr.ui.player.NowPlayingScreen
import io.github.pranavm716.vgmixr.ui.playlist.PlaylistTab
import io.github.pranavm716.vgmixr.ui.queue.QueueScreen
import io.github.pranavm716.vgmixr.ui.theme.Background
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
            VGMixrTheme {
                val snackbars = remember { SnackbarHostState() }
                MessagesEffect(snackbars)
                VGMixrApp(
                    screen = { destination, nav, reselects ->
                        AppScreen(destination, nav, reselects)
                    },
                    familyScreen = { FamilyTab() },
                    playlistScreen = { PlaylistTab() },
                    snackbars = snackbars,
                    miniPlayer = { sheet, open -> MiniPlayerBar(sheet, onOpenNowPlaying = open) },
                    miniPlayerInSheet = { MiniPlayerInSheet() },
                    nowPlayingHeader = { close, open -> NowPlayingHeader(close, open) },
                    nowPlaying = { openQueue -> NowPlayingScreen(onOpenQueue = openQueue) },
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
