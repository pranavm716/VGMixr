package io.github.pranavm716.vgmixr.ui.player

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playback.PlayQueue
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.QueueEntry
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakePlaylistRepository
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The title and chips swipe to the next or previous track, as the art does; a tap still opens. */
// Each swipe starts on the title and runs past a third of the screen, which a skip needs.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NowPlayingSwipeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun track(id: String, title: String) =
        CatalogTrack(id, "rse", emptyList(), title, 0, emptyList())

    private val playback = FakePlaybackController()
    private val opened = mutableListOf<String>()

    @Before
    fun setUp() {
        val catalog = Catalog(
            games = emptyList(),
            families = listOf(Family("rse", "RSE", "RSE")),
            tracks = listOf(track("a", "Route 101"), track("b", "Littleroot"), track("c", "Oldale"))
        )
        val family = QueueContext.Family("rse")
        playback.state.value = PlaybackState(
            queue = PlayQueue(listOf("a", "b", "c").map { QueueEntry(it, family) }, current = 1),
            trackId = "b",
            isPlaying = true
        )
        val viewModel = NowPlayingViewModel(
            FakeCatalogRepository(catalog),
            playback,
            FakePlaylistRepository()
        )
        compose.setContent {
            VGMixrTheme {
                NowPlayingScreen({
                }, {}, onOpenFamily = { opened += it }, onOpenRemixes = {}, viewModel)
            }
        }
    }

    @Test
    fun `swiping the title left plays the next track`() {
        compose.onNodeWithText("Littleroot").performTouchInput {
            swipe(center, Offset(center.x - 600f, center.y))
        }
        compose.waitForIdle()

        assertEquals(listOf(FakePlaybackController.SkipToNext), playback.calls)
    }

    @Test
    fun `swiping the title right plays the previous track`() {
        compose.onNodeWithText("Littleroot").performTouchInput {
            swipe(center, Offset(center.x + 600f, center.y))
        }
        compose.waitForIdle()

        assertEquals(listOf(FakePlaybackController.SkipToPrevious), playback.calls)
    }

    @Test
    fun `tapping the title still opens its family`() {
        compose.onNodeWithText("Littleroot").performClick()

        assertEquals(listOf("rse"), opened)
        assertEquals(emptyList<Any>(), playback.calls)
    }
}
