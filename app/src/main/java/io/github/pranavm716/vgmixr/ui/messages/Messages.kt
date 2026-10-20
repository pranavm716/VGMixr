package io.github.pranavm716.vgmixr.ui.messages

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** Short messages shown in the app's snackbar. */
sealed interface UserMessage {
    data class PlaybackStopped(val failure: ResolveFailure) : UserMessage

    data class AddedToQueue(val count: Int) : UserMessage
}

/** Where ViewModels post messages for the app's snackbar. */
@Singleton
class UserMessages @Inject constructor() {
    private val posted = MutableSharedFlow<UserMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<UserMessage> = posted.asSharedFlow()

    fun post(message: UserMessage) {
        posted.tryEmit(message)
    }
}

@HiltViewModel
class MessagesViewModel @Inject constructor(playback: PlaybackController, posted: UserMessages) :
    ViewModel() {
    val messages: Flow<UserMessage> = merge(
        playback.state
            .map { it.stoppedBy }
            .distinctUntilChanged()
            .filterNotNull()
            .map { UserMessage.PlaybackStopped(it) },
        posted.messages
    )
}

/** Shows each message in [snackbars] while the app is on screen. */
@Composable
fun MessagesEffect(snackbars: SnackbarHostState, viewModel: MessagesViewModel = hiltViewModel()) {
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is UserMessage.AddedToQueue ->
                    resources.getQuantityString(
                        R.plurals.added_to_queue,
                        message.count,
                        message.count
                    )

                is UserMessage.PlaybackStopped -> resources.getString(
                    when (message.failure) {
                        ResolveFailure.NETWORK -> R.string.playback_stopped_network
                        ResolveFailure.BOT_CHECK -> R.string.playback_stopped_bot_check
                        ResolveFailure.EXTRACTOR -> R.string.playback_stopped_extractor
                    }
                )
            }
            // A newer message replaces the one showing, so quick swipes don't queue up snackbars.
            snackbars.currentSnackbarData?.dismiss()
            launch { snackbars.showSnackbar(text) }
        }
    }
}
