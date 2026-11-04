package io.github.pranavm716.vgmixr.ui.queue

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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
import io.github.pranavm716.vgmixr.ui.components.TileRowHeight
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Queued rows drag by their handle within "Next in queue"; other rows have no handle. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class QueueReorderTest {
    @get:Rule
    val compose = createComposeRule()

    private val playback = FakePlaybackController()

    private val rse = QueueContext.Family("rse")

    private fun queue(vararg queued: String) = PlaybackState(
        queue = PlayQueue(
            listOf(QueueEntry("c", rse)) +
                queued.map { QueueEntry(it, QueueContext.Queue) } +
                listOf("x", "y").map { QueueEntry(it, rse) },
            current = 0
        ),
        trackId = "c",
        isPlaying = true
    )

    @Before
    fun setUp() {
        val ids = listOf("c", "q1", "q2", "q3", "x", "y")
        val catalog = Catalog(
            games = emptyList(),
            families = listOf(Family("rse", "RSE", "RSE")),
            tracks = ids.map { CatalogTrack(it, "rse", emptyList(), "Title $it", 0, emptyList()) }
        )
        val rse = QueueContext.Family("rse")
        // [c], q1, q2, q3 queued, then x, y from the family.
        val entries = listOf(QueueEntry("c", rse)) +
            listOf("q1", "q2", "q3").map { QueueEntry(it, QueueContext.Queue) } +
            listOf("x", "y").map { QueueEntry(it, rse) }
        playback.state.value =
            PlaybackState(queue = PlayQueue(entries, current = 0), trackId = "c", isPlaying = true)
        val viewModel = QueueViewModel(
            FakeCatalogRepository(catalog),
            playback,
            UserMessages(),
            FakePlaylistRepository()
        )
        compose.setContent { VGMixrTheme { QueueScreen(onClose = {}, viewModel = viewModel) } }
    }

    private val handles get() = compose.onAllNodesWithContentDescription(
        "Drag to reorder",
        useUnmergedTree = true
    )

    @Test
    fun `only the queued rows have a handle`() {
        handles.assertCountEquals(3)
    }

    @Test
    fun `dragging the first queued row down two rows moves it to the end of the queue`() {
        handles[0].performTouchInput {
            val rowHeight = TileRowHeight.toPx()
            down(center)
            repeat(22) { moveBy(Offset(0f, rowHeight * 2 / 20)) }
            up()
        }
        compose.waitForIdle()

        assertEquals(listOf(FakePlaybackController.MoveInQueue(1, 3)), playback.calls)
    }

    @Test
    fun `dragging past the queue stops at its last place`() {
        handles[0].performTouchInput {
            down(center)
            repeat(40) { moveBy(Offset(0f, 30.dp.toPx())) }
            up()
        }
        compose.waitForIdle()

        assertEquals(listOf(FakePlaybackController.MoveInQueue(1, 3)), playback.calls)
    }

    @Test
    fun `after a move, a row's handle drags it from its new place`() {
        handles[0].performTouchInput {
            val rowHeight = TileRowHeight.toPx()
            down(center)
            repeat(22) { moveBy(Offset(0f, rowHeight / 10)) }
            up()
        }
        compose.waitForIdle()
        // The player moves q1 to the end of the queue: q2, q3, q1.
        playback.state.value = queue("q2", "q3", "q1")
        compose.waitForIdle()

        // q1's handle, third now, dragged back up to the top moves entry 3 to 1.
        handles[2].performTouchInput {
            val rowHeight = TileRowHeight.toPx()
            down(center)
            repeat(22) { moveBy(Offset(0f, -rowHeight / 10)) }
            up()
        }
        compose.waitForIdle()

        assertEquals(
            listOf(
                FakePlaybackController.MoveInQueue(1, 3),
                FakePlaybackController.MoveInQueue(3, 1)
            ),
            playback.calls
        )
    }
}
