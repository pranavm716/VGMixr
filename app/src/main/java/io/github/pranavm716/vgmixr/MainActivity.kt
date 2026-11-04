package io.github.pranavm716.vgmixr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.playback.PlayableTab
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.TabPlayer
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.playlist.playPlaylist
import io.github.pranavm716.vgmixr.domain.settings.AccentSetting
import io.github.pranavm716.vgmixr.domain.settings.BackgroundSetting
import io.github.pranavm716.vgmixr.ui.AppScreen
import io.github.pranavm716.vgmixr.ui.VGMixrApp
import io.github.pranavm716.vgmixr.ui.family.FamilyTab
import io.github.pranavm716.vgmixr.ui.messages.MessagesEffect
import io.github.pranavm716.vgmixr.ui.navigation.TopLevelDestination
import io.github.pranavm716.vgmixr.ui.player.AddToPlaylist
import io.github.pranavm716.vgmixr.ui.player.MiniPlayerBar
import io.github.pranavm716.vgmixr.ui.player.MiniPlayerInSheet
import io.github.pranavm716.vgmixr.ui.player.MiniPlayerViewModel
import io.github.pranavm716.vgmixr.ui.player.NowPlayingHeader
import io.github.pranavm716.vgmixr.ui.player.NowPlayingScreen
import io.github.pranavm716.vgmixr.ui.playlist.EditPlaylistTab
import io.github.pranavm716.vgmixr.ui.playlist.PlaylistTab
import io.github.pranavm716.vgmixr.ui.queue.QueueScreen
import io.github.pranavm716.vgmixr.ui.remixes.LinkTab
import io.github.pranavm716.vgmixr.ui.settings.SettingsTab
import io.github.pranavm716.vgmixr.ui.theme.Background
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import io.github.pranavm716.vgmixr.ui.theme.color
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var accentSetting: AccentSetting

    @Inject
    lateinit var backgroundSetting: BackgroundSetting

    @Inject
    lateinit var playlists: PlaylistRepository

    @Inject
    lateinit var catalog: CatalogRepository

    @Inject
    lateinit var playback: PlaybackController

    @Inject
    lateinit var tabPlayer: TabPlayer

    // Held here so the notification's intent (onNewIntent) can open it.
    private val nowPlayingOpen = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nowPlayingOpen.value = savedInstanceState?.getBoolean(NOW_PLAYING_OPEN)
            ?: intent.opensNowPlaying()
        // A restored activity already handled the intent it was started with.
        if (savedInstanceState == null) playShortcutPlaylist(intent)
        // A transparent status bar, so now playing's game tint runs up behind it.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Background.toArgb())
        )
        setContent {
            // A pick in Settings recolors the whole app at once.
            val accent by accentSetting.accent.collectAsStateWithLifecycle()
            val background by backgroundSetting.background.collectAsStateWithLifecycle()
            // The navigation bar takes the picked background too.
            LaunchedEffect(background) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.dark(background.color.toArgb())
                )
            }
            VGMixrTheme(accent.color, background.color) {
                val snackbars = remember { SnackbarHostState() }
                MessagesEffect(snackbars)
                val miniPlayer: MiniPlayerViewModel = hiltViewModel()
                val nothingLoaded by miniPlayer.nothingLoaded.collectAsStateWithLifecycle()
                VGMixrApp(
                    screen = { destination, nav, reselects, addToPlaylist ->
                        AppScreen(destination, nav, reselects, addToPlaylist)
                    },
                    familyScreen = { addToPlaylist -> FamilyTab(addToPlaylist) },
                    playlistScreen = { edit, close ->
                        PlaylistTab(onEditPlaylist = edit, onClose = close)
                    },
                    editPlaylistScreen = { back -> EditPlaylistTab(onBack = back) },
                    linkScreen = { back -> LinkTab(onBack = back) },
                    settingsScreen = { back -> SettingsTab(onBack = back) },
                    snackbars = snackbars,
                    onPlayTab = ::playTab,
                    miniPlayer = { sheet, open -> MiniPlayerBar(sheet, onOpenNowPlaying = open) },
                    miniPlayerInSheet = { MiniPlayerInSheet() },
                    nowPlayingHeader = { close, open -> NowPlayingHeader(close, open) },
                    nowPlaying = { openQueue, addToPlaylist, openFamily, openRemixes ->
                        NowPlayingScreen(
                            onOpenQueue = openQueue,
                            onAddToPlaylist = addToPlaylist,
                            onOpenFamily = openFamily,
                            onOpenRemixes = openRemixes
                        )
                    },
                    addToPlaylist = { trackId, close -> AddToPlaylist(trackId, onClose = close) },
                    queue = { close -> QueueScreen(onClose = close) },
                    nowPlayingOpen = nowPlayingOpen,
                    nothingLoaded = nothingLoaded
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.opensNowPlaying()) nowPlayingOpen.value = true
        playShortcutPlaylist(intent)
    }

    /** A playlist's row on the app icon's menu plays it, as a long press in Playlists does. */
    private fun playShortcutPlaylist(intent: Intent) {
        if (intent.action != ACTION_PLAY_PLAYLIST) return
        val playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1L).takeIf { it >= 0 } ?: return
        lifecycleScope.launch { playPlaylist(playlistId, playlists, catalog, playback) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(NOW_PLAYING_OPEN, nowPlayingOpen.value)
    }

    /** A long press on a navbar tab that plays: Games, Playlists or Remixes. */
    private fun playTab(tab: TopLevelDestination) {
        val playable = when (tab) {
            TopLevelDestination.GAMES -> PlayableTab.GAMES
            TopLevelDestination.PLAYLISTS -> PlayableTab.PLAYLISTS
            TopLevelDestination.REMIXES -> PlayableTab.REMIXES
            TopLevelDestination.SEARCH -> return
        }
        lifecycleScope.launch { tabPlayer.play(playable) }
    }

    companion object {
        /** The action of the notification's intent: open the app on now playing. */
        const val ACTION_OPEN_NOW_PLAYING = "io.github.pranavm716.vgmixr.OPEN_NOW_PLAYING"

        /** The action of a playlist's row on the app icon's menu: play [EXTRA_PLAYLIST_ID]. */
        const val ACTION_PLAY_PLAYLIST = "io.github.pranavm716.vgmixr.PLAY_PLAYLIST"
        const val EXTRA_PLAYLIST_ID = "playlistId"

        private const val NOW_PLAYING_OPEN = "nowPlayingOpen"

        private fun Intent.opensNowPlaying() = action == ACTION_OPEN_NOW_PLAYING
    }
}
