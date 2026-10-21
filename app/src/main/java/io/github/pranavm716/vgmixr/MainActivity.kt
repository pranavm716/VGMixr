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
import io.github.pranavm716.vgmixr.ui.player.NowPlayingPlaceholder
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
        val bars = SystemBarStyle.dark(Background.toArgb())
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            VGMixrTheme {
                val snackbars = remember { SnackbarHostState() }
                MessagesEffect(snackbars)
                VGMixrApp(
                    screen = { destination, nav -> AppScreen(destination, nav) },
                    familyScreen = { onTrackPlayed -> FamilyTab(onTrackPlayed) },
                    snackbars = snackbars,
                    miniPlayer = { open -> MiniPlayerBar(onOpenNowPlaying = open) },
                    nowPlaying = { close -> NowPlayingPlaceholder(onClose = close) },
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
