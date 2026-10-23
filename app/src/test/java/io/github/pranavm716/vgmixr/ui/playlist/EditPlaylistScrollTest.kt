package io.github.pranavm716.vgmixr.ui.playlist

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * New results start at the top, even when the row at the top of the old ones is further down in
 * the new ones; a row taken out under the same query keeps the list where it is.
 */
// A plain Application, so the Hilt app's catalog import doesn't run.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class EditPlaylistScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(
        EditPlaylistUiState("Hype", rows(0 until 40), resultsQuery = "r")
    )

    @Before
    fun setUp() {
        compose.setContent {
            VGMixrTheme { EditPlaylistScreen(state, state.resultsQuery, {}, {}, {}) }
        }
        compose.onNode(hasScrollAction()).performScrollToIndex(10)
        compose.onNodeWithText("Track 10").assertIsDisplayed()
    }

    @Test
    fun `results for a new query start at the top`() {
        // Track 10, at the top before, is now 30 rows down.
        state =
            EditPlaylistUiState(
                "Hype",
                rows(100 until 130) + rows(10 until 11),
                resultsQuery = "ro"
            )

        compose.onNodeWithText("Track 100").assertIsDisplayed()
    }

    @Test
    fun `taking a row out keeps the list where it is`() {
        state = state.copy(results = state.results.filterNot { it.id == "t3" })

        compose.onNodeWithText("Track 10").assertIsDisplayed()
        compose.onNodeWithText("Track 0").assertDoesNotExist()
    }

    private fun rows(numbers: IntRange) =
        numbers.map { EditPlaylistRowUi("t$it", "Track $it", emptyList()) }
}
