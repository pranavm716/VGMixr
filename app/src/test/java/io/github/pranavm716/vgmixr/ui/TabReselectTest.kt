package io.github.pranavm716.vgmixr.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.ui.navigation.FamilyRoute
import io.github.pranavm716.vgmixr.ui.navigation.TopLevelDestination
import io.github.pranavm716.vgmixr.ui.navigation.navItemTag
import io.github.pranavm716.vgmixr.ui.navigation.screenTag
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// A plain Application, so the Hilt app's catalog import doesn't run.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class TabReselectTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController
    private val games = TopLevelDestination.GAMES

    @Before
    fun setUp() {
        compose.setContent {
            navController = rememberNavController()
            VGMixrTheme { VGMixrApp(navController) }
        }
        compose.runOnUiThread { navController.navigate(FamilyRoute("rse")) }
        compose.onNodeWithTag(FAMILY_SCREEN_TAG).assertIsDisplayed()
    }

    @Test
    fun `a screen opened inside a tab keeps the tab selected`() {
        compose.onNodeWithTag(games.navItemTag).assertIsSelected()
    }

    @Test
    fun `tapping the selected tab returns to its first screen`() {
        compose.onNodeWithTag(games.navItemTag).performClick()

        compose.onNodeWithTag(games.screenTag).assertIsDisplayed()
        compose.onNodeWithTag(FAMILY_SCREEN_TAG).assertDoesNotExist()
        compose.onNodeWithTag(games.navItemTag).assertIsSelected()
    }

    @Test
    fun `leaving and coming back to a tab keeps its screen`() {
        compose.onNodeWithTag(TopLevelDestination.entries[1].navItemTag).performClick()
        compose.onNodeWithTag(games.navItemTag).performClick()

        compose.onNodeWithTag(FAMILY_SCREEN_TAG).assertIsDisplayed()
    }
}
