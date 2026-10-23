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
import io.github.pranavm716.vgmixr.ui.family.TrackRow
import io.github.pranavm716.vgmixr.ui.family.TrackRowUi
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A long press on a track row runs its action with a haptic tick; rows without one ignore it. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class TrackLongPressTest {
    @get:Rule
    val compose = createComposeRule()

    private val haptics = RecordingHaptics()
    private val longPresses = mutableListOf<String>()

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                VGMixrTheme { content() }
            }
        }
    }

    @Test
    fun `a long press on a family row runs its action and ticks`() {
        show {
            TrackRow(
                TrackRowUi("rse-a", 1, "Route 101", emptyList()),
                playing = false,
                loading = false,
                onClick = {},
                onLongClick = { longPresses += "rse-a" }
            )
        }

        compose.onNodeWithText("Route 101").performTouchInput { longClick() }

        assertEquals(listOf("rse-a"), longPresses)
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
    }

    @Test
    fun `a long press on a search row runs its action and ticks`() {
        show {
            TrackTileRow(
                "Route 101",
                emptyList(),
                unplayable = false,
                current = false,
                onClick = {},
                onLongClick = { longPresses += "rse-a" }
            )
        }

        compose.onNodeWithText("Route 101").performTouchInput { longClick() }

        assertEquals(listOf("rse-a"), longPresses)
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.performed)
    }

    // The queue's rows and Edit playlist's.
    @Test
    fun `a row without a long-press action doesn't tick`() {
        show {
            TrackTileRow(
                "Route 101",
                emptyList(),
                unplayable = false,
                current = false,
                onClick = {}
            )
        }

        compose.onNodeWithText("Route 101").performTouchInput { longClick() }

        assertEquals(emptyList<HapticFeedbackType>(), haptics.performed)
    }

    private class RecordingHaptics : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }
}
