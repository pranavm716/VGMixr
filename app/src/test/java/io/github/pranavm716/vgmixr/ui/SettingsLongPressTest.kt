package io.github.pranavm716.vgmixr.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.ui.navigation.TopLevelDestination
import io.github.pranavm716.vgmixr.ui.navigation.navItemTag
import io.github.pranavm716.vgmixr.ui.navigation.screenTag
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A long press on the Remixes tab opens Settings inside that tab, from any tab. */
// A plain Application, so the Hilt app's catalog import doesn't run.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SettingsLongPressTest {
    @get:Rule
    val compose = createComposeRule()

    private val remixes = TopLevelDestination.REMIXES

    @Before
    fun setUp() {
        compose.setContent { VGMixrTheme { VGMixrApp() } }
        // Pressed while the Games tab shows.
        compose.onNodeWithTag(remixes.navItemTag).performTouchInput { longClick() }
    }

    @Test
    fun `settings opens in the Remixes tab, under its header`() {
        compose.onNodeWithTag(SETTINGS_SCREEN_TAG).assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithTag(remixes.navItemTag).assertIsSelected()
    }

    @Test
    fun `back from settings goes to the Remixes tab`() {
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithTag(remixes.screenTag).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_SCREEN_TAG).assertDoesNotExist()
    }

    @Test
    fun `tapping the Remixes tab then returns to its first screen`() {
        compose.onNodeWithTag(remixes.navItemTag).performClick()

        compose.onNodeWithTag(remixes.screenTag).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_SCREEN_TAG).assertDoesNotExist()
    }

    @Test
    fun `a second long press doesn't open settings twice`() {
        compose.onNodeWithTag(remixes.navItemTag).performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithTag(remixes.screenTag).assertIsDisplayed()
    }

    @Test
    fun `a long press on another tab only switches to it`() {
        val search = TopLevelDestination.SEARCH
        compose.onNodeWithTag(search.navItemTag).performTouchInput { longClick() }

        compose.onNodeWithTag(search.navItemTag).assertIsSelected()
        compose.onNodeWithTag(search.screenTag).assertIsDisplayed()
    }
}
