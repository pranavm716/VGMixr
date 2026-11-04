package io.github.pranavm716.vgmixr.ui.components

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A track row swipes right to queue and left to play now. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SwipeToQueueOrPlayNowTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<String>()

    @Before
    fun setUp() {
        compose.setContent {
            VGMixrTheme {
                SwipeToQueueOrPlayNow(onQueue = { actions += "queue" }, onPlayNow = {
                    actions +=
                        "now"
                }) {
                    Text("Route 101", Modifier.fillMaxWidth().height(64.dp))
                }
            }
        }
    }

    @Test
    fun `a left swipe plays now`() {
        compose.onNodeWithText("Route 101").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertEquals(listOf("now"), actions)
    }

    @Test
    fun `a right swipe still queues`() {
        compose.onNodeWithText("Route 101").performTouchInput { swipeRight() }
        compose.waitForIdle()

        assertEquals(listOf("queue"), actions)
    }
}
