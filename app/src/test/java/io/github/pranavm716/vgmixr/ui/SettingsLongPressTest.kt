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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A long press on the Search tab opens Settings inside that tab; Games and Playlists play. */
// A plain Application, so the Hilt app's catalog import doesn't run.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SettingsLongPressTest {
    @get:Rule
    val compose = createComposeRule()

    private val search = TopLevelDestination.SEARCH
    private val played = mutableListOf<TopLevelDestination>()

    @Before
    fun setUp() {
        compose.setContent { VGMixrTheme { VGMixrApp(onPlayTab = { played += it }) } }
        compose.onNodeWithTag(search.navItemTag).performClick()
        // Pressed while the Search tab shows.
        compose.onNodeWithTag(search.navItemTag).performTouchInput { longClick() }
    }

    @Test
    fun `settings opens in the Search tab, under its header`() {
        compose.onNodeWithTag(SETTINGS_SCREEN_TAG).assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithTag(search.navItemTag).assertIsSelected()
    }

    @Test
    fun `back from settings goes to the Search tab`() {
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithTag(search.screenTag).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_SCREEN_TAG).assertDoesNotExist()
    }

    @Test
    fun `tapping the Search tab then returns to its first screen`() {
        compose.onNodeWithTag(search.navItemTag).performClick()

        compose.onNodeWithTag(search.screenTag).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_SCREEN_TAG).assertDoesNotExist()
    }

    @Test
    fun `a second long press doesn't open settings twice`() {
        compose.onNodeWithTag(search.navItemTag).performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithTag(search.screenTag).assertIsDisplayed()
    }

    @Test
    fun `a long press on Games or Playlists plays it and stays on the tab shown`() {
        for (tab in listOf(TopLevelDestination.GAMES, TopLevelDestination.PLAYLISTS)) {
            compose.onNodeWithTag(tab.navItemTag).performTouchInput { longClick() }
        }

        assertEquals(listOf(TopLevelDestination.GAMES, TopLevelDestination.PLAYLISTS), played)
        compose.onNodeWithTag(SETTINGS_SCREEN_TAG).assertIsDisplayed()
    }
}
