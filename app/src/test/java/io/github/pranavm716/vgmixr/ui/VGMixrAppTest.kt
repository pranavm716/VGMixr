package io.github.pranavm716.vgmixr.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

// A plain Application, so the Hilt app's catalog import doesn't run.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class VGMixrAppTest {
    @get:Rule
    val compose = createComposeRule()

    private val second = TopLevelDestination.entries[1]

    @Before
    fun setUp() {
        compose.setContent { VGMixrTheme { VGMixrApp() } }
    }

    @Test
    fun `starts on the start tab`() {
        compose.onNodeWithTag(TopLevelDestination.start.navItemTag).assertIsSelected()
        compose.onNodeWithTag(TopLevelDestination.start.screenTag).assertIsDisplayed()
    }

    @Test
    fun `every tab is in the navbar`() {
        TopLevelDestination.entries.forEach {
            compose.onNodeWithTag(it.navItemTag).assertIsDisplayed()
        }
    }

    @Test
    fun `tapping a tab shows its screen and selects it`() {
        compose.onNodeWithTag(second.navItemTag).performClick()

        compose.onNodeWithTag(second.navItemTag).assertIsSelected()
        compose.onNodeWithTag(TopLevelDestination.start.navItemTag).assertIsNotSelected()
        compose.onNodeWithTag(second.screenTag).assertIsDisplayed()
    }

    @Test
    fun `tapping the start tab again returns to it`() {
        compose.onNodeWithTag(second.navItemTag).performClick()
        compose.onNodeWithTag(TopLevelDestination.start.navItemTag).performClick()

        compose.onNodeWithTag(TopLevelDestination.start.navItemTag).assertIsSelected()
        compose.onNodeWithTag(TopLevelDestination.start.screenTag).assertIsDisplayed()
    }
}
