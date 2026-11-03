package io.github.pranavm716.vgmixr.ui.search

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A left swipe on a result plays it now and puts the keyboard away. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SearchSwipeTest {
    @get:Rule
    val compose = createComposeRule()

    // Robolectric has no keyboard to look at, so the screen gets one that records what it's told.
    private class RecordingKeyboard : SoftwareKeyboardController {
        val calls = mutableListOf<String>()

        override fun show() {
            calls += "show"
        }

        override fun hide() {
            calls += "hide"
        }
    }

    @Test
    fun `a left swipe plays the result now and hides the keyboard`() {
        val keyboard = RecordingKeyboard()
        val playedNow = mutableListOf<String>()
        compose.setContent {
            VGMixrTheme {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    SearchScreen(
                        state = SearchUiState(listOf(SearchRowUi("a", "Route 101", emptyList()))),
                        query = "route",
                        reselects = 0,
                        onQueryChange = {},
                        onTrackClick = {},
                        onQueueTrack = {},
                        onPlayNow = { playedNow += it },
                        onTrackLongClick = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        keyboard.calls.clear() // The field brings it up on opening.

        compose.onNodeWithText("Route 101").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertEquals(listOf("a"), playedNow)
        assertEquals(listOf("hide"), keyboard.calls)
    }
}
