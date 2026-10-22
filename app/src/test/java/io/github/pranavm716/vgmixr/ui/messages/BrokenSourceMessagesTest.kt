package io.github.pranavm716.vgmixr.ui.messages

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.PlaybackEvent
import io.github.pranavm716.vgmixr.domain.playback.TryAgainResult
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
    private val viewModel by lazy { MessagesViewModel(playback, UserMessages()) }

    private val eliteFour =
        UserMessage.TrackUnplayable("oras-b", "Battle! (Elite Four)", BrokenReason.NO_AUDIO)

    @Test
    fun `a source failing just now says what happened to its track`() = runTest {
        viewModel.messages.test {
            playback.events.emit(
                PlaybackEvent.SourceFailed(
                    "oras-b",
                    "Battle! (Elite Four)",
                    BrokenReason.NO_AUDIO,
                    trackSkipped = true
                )
            )

            assertEquals(
                UserMessage.SourceFailed("Battle! (Elite Four)", BrokenReason.NO_AUDIO, true),
                awaitItem()
            )
        }
    }

    @Test
    fun `Try again checks the track and says it can play again`() = runTest {
        playback.tryAgainResult = TryAgainResult.Back

        viewModel.messages.test {
            viewModel.onTryAgain(eliteFour)

            assertEquals(UserMessage.PlaysAgain("Battle! (Elite Four)"), awaitItem())
        }
        assertEquals(listOf(FakePlaybackController.TryAgain("oras-b")), playback.calls)
    }

    @Test
    fun `a track still dead says so again, with the fresh reason`() = runTest {
        playback.tryAgainResult = TryAgainResult.StillBroken(BrokenReason.UNAVAILABLE)

        viewModel.messages.test {
            viewModel.onTryAgain(eliteFour)

            assertEquals(
                UserMessage.TrackUnplayable(
                    "oras-b",
                    "Battle! (Elite Four)",
                    BrokenReason.UNAVAILABLE
                ),
                awaitItem()
            )
        }
    }

    @Test
    fun `a check that couldn't run says so`() = runTest {
        playback.tryAgainResult = TryAgainResult.Unchecked

        viewModel.messages.test {
            viewModel.onTryAgain(eliteFour)

            assertEquals(UserMessage.Unchecked("Battle! (Elite Four)"), awaitItem())
        }
    }
}
