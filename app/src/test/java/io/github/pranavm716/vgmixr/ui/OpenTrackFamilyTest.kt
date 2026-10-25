package io.github.pranavm716.vgmixr.ui

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.ui.navigation.FamilyRoute
import io.github.pranavm716.vgmixr.ui.navigation.TopLevelDestination
import io.github.pranavm716.vgmixr.ui.navigation.navItemTag
import io.github.pranavm716.vgmixr.ui.navigation.screenTag
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tapping now playing's title opens the track's own family in the Games tab, as "Playing from"
 * does for a family, from any tab and whatever the track plays from.
 */
// A plain Application, so the Hilt app's catalog import doesn't run.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class OpenTrackFamilyTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController
    private lateinit var openFamily: (String) -> Unit
    private val nowPlayingOpen: MutableState<Boolean> = mutableStateOf(false)

    @Before
    fun setUp() {
        compose.setContent {
            navController = rememberNavController()
            VGMixrTheme {
                VGMixrApp(
                    navController,
                    nowPlaying = { _, _, open, _ -> openFamily = open },
                    nowPlayingOpen = nowPlayingOpen
                )
            }
        }
        // Opened while the Playlists tab shows, as when playing from a playlist.
        compose.onNodeWithTag(TopLevelDestination.PLAYLISTS.navItemTag).performClick()
        compose.runOnUiThread { nowPlayingOpen.value = true }
        compose.waitForIdle()
        compose.runOnUiThread { openFamily("rse") }
        compose.waitForIdle()
    }

    @Test
    fun `the family opens in the Games tab, scrolled to the track, and now playing closes`() {
        compose.onNodeWithTag(FAMILY_SCREEN_TAG).assertIsDisplayed()
        compose.onNodeWithTag(TopLevelDestination.GAMES.navItemTag).assertIsSelected()
        val route = checkNotNull(navController.currentBackStackEntry).toRoute<FamilyRoute>()
        assertEquals(FamilyRoute("rse", fromNowPlaying = true), route)
        assertFalse(nowPlayingOpen.value)
    }

    @Test
    fun `back from the family goes to the family list`() {
        compose.runOnUiThread { navController.popBackStack() }

        compose.onNodeWithTag(TopLevelDestination.GAMES.screenTag).assertIsDisplayed()
    }
}
