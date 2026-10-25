package io.github.pranavm716.vgmixr.ui.components

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A long press on a family's or playlist's row runs its action (play it all) with a haptic tick;
 * a row without one ignores it.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class CollectionRowLongPressTest {
    @get:Rule
    val compose = createComposeRule()

    private val haptics = RecordingHaptics()
    private var longPresses = 0

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                VGMixrTheme { content() }
            }
        }
    }

    @Test
    fun `a long press runs the row's action and ticks`() {
        show {
            CollectionRow(
                "Favorites",
                3,
                art = {},
                onClick = {},
                onLongClick = { longPresses++ }
            )
        }

        compose.onNodeWithText("Favorites").performTouchInput { longClick() }

        assertEquals(1, longPresses)
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
    }

    @Test
    fun `a row without a long-press action doesn't tick`() {
        show { CollectionRow("Favorites", 3, art = {}, onClick = {}) }

        compose.onNodeWithText("Favorites").performTouchInput { longClick() }

        assertEquals(emptyList<HapticFeedbackType>(), haptics.performed)
    }

    private class RecordingHaptics : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }
}
