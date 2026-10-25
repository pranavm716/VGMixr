package io.github.pranavm716.vgmixr.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A snackbar's action takes the tap, never the screen under it. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SnackbarTouchTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tapping a snackbar's action doesn't reach the screen under it`() {
        val snackbars = SnackbarHostState()
        var screenTaps = 0
        var result: SnackbarResult? = null
        lateinit var scope: CoroutineScope
        compose.setContent {
            scope = rememberCoroutineScope()
            VGMixrTheme {
                VGMixrApp(
                    rememberNavController(),
                    screen = { _, _, _, _ ->
                        Box(Modifier.fillMaxSize().clickable { screenTaps++ })
                    },
                    snackbars = snackbars
                )
            }
        }
        compose.runOnUiThread {
            scope.launch {
                result = snackbars.showSnackbar(
                    "Deleted Stow-on-Side",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Long
                )
            }
        }
        compose.waitForIdle()

        val undo = compose.onNodeWithText("Undo").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { click(undo.center) }
        compose.waitForIdle()

        assertEquals(SnackbarResult.ActionPerformed, result)
        assertEquals(0, screenTaps)
    }
}
