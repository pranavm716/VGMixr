package io.github.pranavm716.vgmixr.ui

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.ui.navigation.FamilyRoute
import io.github.pranavm716.vgmixr.ui.navigation.TopLevelDestination
import io.github.pranavm716.vgmixr.ui.navigation.navItemTag
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * "Add to playlist" opens over the app for a track long-pressed in a family or in search, and for
 * the playing track from now playing.
 */
// A plain Application, so the Hilt app's catalog import doesn't run.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class OpenAddToPlaylistTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController
    private lateinit var fromFamily: (String) -> Unit
    private lateinit var fromSearch: (String) -> Unit
    private lateinit var fromNowPlaying: () -> Unit
    private val nowPlayingOpen: MutableState<Boolean> = mutableStateOf(false)

    @Before
    fun setUp() {
        compose.setContent {
            navController = rememberNavController()
            VGMixrTheme {
                VGMixrApp(
                    navController,
                    screen = { destination, _, _, add ->
                        if (destination == TopLevelDestination.SEARCH) fromSearch = add
                        Text(destination.label)
                    },
                    familyScreen = { add -> fromFamily = add },
                    nowPlaying = { _, add, _, _ -> fromNowPlaying = add },
                    addToPlaylist = { trackId, _ -> Text("Add ${trackId ?: "the playing track"}") },
                    nowPlayingOpen = nowPlayingOpen
                )
            }
        }
    }

    @Test
    fun `a long press in a family opens it for that track`() {
        compose.runOnUiThread { navController.navigate(FamilyRoute("rse")) }
        compose.waitForIdle()

        compose.runOnUiThread { fromFamily("rse-a") }

        compose.onNodeWithText("Add rse-a").assertIsDisplayed()
    }

    @Test
    fun `a long press in search opens it for that track`() {
        compose.onNodeWithTag(TopLevelDestination.SEARCH.navItemTag).performClick()
        compose.waitForIdle()

        compose.runOnUiThread { fromSearch("rse-b") }

        compose.onNodeWithText("Add rse-b").assertIsDisplayed()
    }

    @Test
    fun `now playing opens it for the playing track`() {
        compose.runOnUiThread { nowPlayingOpen.value = true }
        compose.waitForIdle()

        compose.runOnUiThread { fromNowPlaying() }

        compose.onNodeWithText("Add the playing track").assertIsDisplayed()
    }
}
