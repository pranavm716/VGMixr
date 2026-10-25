package io.github.pranavm716.vgmixr.ui.messages

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrack
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakeUserTrackRepository
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MessagesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val playback = FakePlaybackController()
    private val posted = UserMessages()
    private val userTracks = FakeUserTrackRepository()
    private val viewModel = MessagesViewModel(playback, posted, userTracks)

    @Test
    fun `playback stopping by a failure shows why, once`() = runTest {
        viewModel.messages.test {
            playback.state.value = PlaybackState(stoppedBy = ResolveFailure.NETWORK)
            assertEquals(UserMessage.PlaybackStopped(ResolveFailure.NETWORK), awaitItem())

            playback.state.value =
                PlaybackState(isPlaying = false, stoppedBy = ResolveFailure.NETWORK, trackId = "a")
            expectNoEvents()
        }
    }

    @Test
    fun `the same failure after playback recovered shows again`() = runTest {
        viewModel.messages.test {
            playback.state.value = PlaybackState(stoppedBy = ResolveFailure.BOT_CHECK)
            awaitItem()
            playback.state.value = PlaybackState(isPlaying = true)
            playback.state.value = PlaybackState(stoppedBy = ResolveFailure.BOT_CHECK)

            assertEquals(UserMessage.PlaybackStopped(ResolveFailure.BOT_CHECK), awaitItem())
        }
    }

    @Test
    fun `messages posted by screens show too`() = runTest {
        viewModel.messages.test {
            posted.post(UserMessage.AddedToQueue(107))

            assertEquals(UserMessage.AddedToQueue(107), awaitItem())
        }
    }

    @Test
    fun `undo puts a deleted remix back as it was`() = runTest {
        val remix = UserTrack(
            videoId = "CCCCCCCCCCC",
            title = "Some Remix",
            channel = "GlitchxCity",
            durationMs = null,
            addedAt = Instant.ofEpochMilli(1_000),
            remixOf = "rse-route-101"
        )

        viewModel.onUndoDelete(UserMessage.RemixDeleted(remix, "Route 101"))

        assertEquals(listOf(remix), userTracks.userTracks.first())
    }
}
