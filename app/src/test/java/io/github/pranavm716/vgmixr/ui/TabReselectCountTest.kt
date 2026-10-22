package io.github.pranavm716.vgmixr.ui

import android.app.Application
import androidx.compose.material3.Text
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

// A plain Application, so the Hilt app's catalog import doesn't run.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class TabReselectCountTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController
    private val games = TopLevelDestination.GAMES

    @Before
    fun setUp() {
        compose.setContent {
            navController = rememberNavController()
            VGMixrTheme {
                VGMixrApp(
                    navController,
                    screen = { destination, _, reselects ->
                        Text("${destination.name} reselected $reselects")
                    }
                )
            }
        }
    }

    @Test
    fun `tapping the selected tab on its first screen counts as a reselect`() {
        compose.onNodeWithText("GAMES reselected 0").assertIsDisplayed()

        compose.onNodeWithTag(games.navItemTag).performClick()

        compose.onNodeWithText("GAMES reselected 1").assertIsDisplayed()
    }

    @Test
    fun `tapping the selected tab inside it only goes back to its first screen`() {
        compose.runOnUiThread { navController.navigate(FamilyRoute("rse")) }
        compose.onNodeWithTag(FAMILY_SCREEN_TAG).assertIsDisplayed()

        compose.onNodeWithTag(games.navItemTag).performClick()

        compose.onNodeWithText("GAMES reselected 0").assertIsDisplayed()
    }
}
