package io.github.pranavm716.vgmixr.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.ui.navigation.TopLevelDestination
import io.github.pranavm716.vgmixr.ui.navigation.navItemTag
import io.github.pranavm716.vgmixr.ui.navigation.screenTag
import io.github.pranavm716.vgmixr.ui.player.PlayingFromUi
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Now playing's "Playing from" opens the playlist inside the Playlists tab, from any tab. */
// A plain Application, so the Hilt app's catalog import doesn't run.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class OpenPlayingFromTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController
    private lateinit var openPlayingFrom: (PlayingFromUi) -> Unit
    private val playlists = TopLevelDestination.PLAYLISTS

    @Before
    fun setUp() {
        compose.setContent {
            navController = rememberNavController()
            VGMixrTheme {
                VGMixrApp(
                    navController,
                    nowPlayingHeader = { _, open -> openPlayingFrom = open },
                    // The header shows on the open sheet.
                    nowPlayingOpen = remember { mutableStateOf(true) }
                )
            }
        }
        compose.waitForIdle()
        // Opened while the Games tab shows.
        compose.runOnUiThread { openPlayingFrom(PlayingFromUi.Playlist(1, "Favorites")) }
        compose.onNodeWithTag(PLAYLIST_SCREEN_TAG).assertIsDisplayed()
    }

    @Test
    fun `the playlist opens in the Playlists tab`() {
        compose.onNodeWithTag(playlists.navItemTag).assertIsSelected()
    }

    @Test
    fun `tapping the Playlists tab then returns to the playlist list`() {
        compose.onNodeWithTag(playlists.navItemTag).performClick()

        compose.onNodeWithTag(playlists.screenTag).assertIsDisplayed()
        compose.onNodeWithTag(PLAYLIST_SCREEN_TAG).assertDoesNotExist()
    }

    @Test
    fun `back from the playlist goes to the playlist list`() {
        compose.runOnUiThread { navController.popBackStack() }

        compose.onNodeWithTag(playlists.screenTag).assertIsDisplayed()
    }
}
