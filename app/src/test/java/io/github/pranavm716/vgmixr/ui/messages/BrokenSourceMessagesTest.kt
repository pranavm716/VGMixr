package io.github.pranavm716.vgmixr.ui.messages

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.PlaybackEvent
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BrokenSourceMessagesTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val playback = FakePlaybackController()
    private val viewModel = MessagesViewModel(playback, UserMessages())

    @Test
    fun `a source failing just now says what happened to its track`() = runTest {
        viewModel.messages.test {
            playback.events.emit(
                PlaybackEvent.SourceFailed(
                    "oras-battle-elite-four",
                    "Battle! (Elite Four)",
                    BrokenReason.NO_AUDIO,
                    trackSkipped = true
                )
            )

            assertEquals(
                UserMessage.SourceFailed(
                    "Battle! (Elite Four)",
                    BrokenReason.NO_AUDIO,
                    trackSkipped = true
                ),
                awaitItem()
            )
        }
    }

    @Test
    fun `Try again on an unplayable track clears its marks`() {
        viewModel.onTryAgain("oras-battle-elite-four")

        assertEquals(
            listOf(FakePlaybackController.TryAgain("oras-battle-elite-four")),
            playback.calls
        )
    }
}
