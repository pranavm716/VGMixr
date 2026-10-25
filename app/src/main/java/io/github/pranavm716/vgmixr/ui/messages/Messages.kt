package io.github.pranavm716.vgmixr.ui.messages

import android.content.res.Resources
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.PlaybackEvent
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.playback.TryAgainResult
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrack
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrackRepository
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

    /**
     * A source of [title] failed just now, broken for [reason] (null when not marked);
     * [trackSkipped] when no source was left, else another upload plays.
     */
    data class SourceFailed(
        val title: String,
        val reason: BrokenReason?,
        val trackSkipped: Boolean
    ) : UserMessage

    /** A tap on [title], whose sources are all broken (the first for [reason]); offers Try again. */
    data class TrackUnplayable(val trackId: String, val title: String, val reason: BrokenReason) :
        UserMessage

    /** Try again found [title] working: it is no longer greyed. */
    data class PlaysAgain(val title: String) : UserMessage

    /** Try again couldn't ask YouTube about [title]; nothing changed. */
    data class Unchecked(val title: String) : UserMessage

    /** User track [track], shown as [title], was deleted; offers Undo. */
    data class RemixDeleted(val track: UserTrack, val title: String) : UserMessage
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
class MessagesViewModel @Inject constructor(
    private val playback: PlaybackController,
    private val posted: UserMessages,
    private val userTracks: UserTrackRepository
) : ViewModel() {
    val messages: Flow<UserMessage> = merge(
        playback.state
            .map { it.stoppedBy }
            .distinctUntilChanged()
            .filterNotNull()
            .map { UserMessage.PlaybackStopped(it) },
        playback.events.map { event ->
            when (event) {
                is PlaybackEvent.SourceFailed ->
                    UserMessage.SourceFailed(event.title, event.reason, event.trackSkipped)
            }
        },
        posted.messages
    )

    /** Undo on [message]: puts the deleted user track back as it was, in its place. */
    fun onUndoDelete(message: UserMessage.RemixDeleted) {
        viewModelScope.launch { userTracks.add(message.track) }
    }

    /** Try again on [message]: checks the track's videos and says what it found. */
    fun onTryAgain(message: UserMessage.TrackUnplayable) {
        viewModelScope.launch {
            val reply = when (val result = playback.tryAgain(message.trackId)) {
                TryAgainResult.Back -> UserMessage.PlaysAgain(message.title)
                is TryAgainResult.StillBroken -> message.copy(reason = result.reason)
                TryAgainResult.Unchecked -> UserMessage.Unchecked(message.title)
            }
            posted.post(reply)
        }
    }
}

/** Shows each message in [snackbars] while the app is on screen. */
@Composable
fun MessagesEffect(snackbars: SnackbarHostState, viewModel: MessagesViewModel = hiltViewModel()) {
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = message.text(resources)
            // A newer message replaces the one showing, so quick swipes don't queue up snackbars.
            snackbars.currentSnackbarData?.dismiss()
            launch {
                when (message) {
                    is UserMessage.TrackUnplayable -> {
                        val result = snackbars.showSnackbar(
                            text,
                            actionLabel = resources.getString(R.string.try_again),
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.onTryAgain(message)
                        }
                    }

                    is UserMessage.RemixDeleted -> {
                        val result = snackbars.showSnackbar(
                            text,
                            actionLabel = resources.getString(R.string.undo),
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.onUndoDelete(message)
                        }
                    }

                    else -> snackbars.showSnackbar(text)
                }
            }
        }
    }
}

private fun UserMessage.text(resources: Resources): String = when (this) {
    is UserMessage.AddedToQueue ->
        resources.getQuantityString(R.plurals.added_to_queue, count, count)

    is UserMessage.PlaybackStopped -> resources.getString(
        when (failure) {
            ResolveFailure.NETWORK -> R.string.playback_stopped_network
            ResolveFailure.BOT_CHECK -> R.string.playback_stopped_bot_check
            ResolveFailure.EXTRACTOR -> R.string.playback_stopped_extractor
        }
    )

    is UserMessage.SourceFailed -> resources.getString(
        when {
            !trackSkipped -> R.string.source_failed_other_upload
            reason == BrokenReason.NO_AUDIO -> R.string.source_failed_skipped_no_audio
            reason == BrokenReason.UNAVAILABLE -> R.string.source_failed_skipped_unavailable
            else -> R.string.source_failed_skipped
        },
        title
    )

    is UserMessage.PlaysAgain -> resources.getString(R.string.plays_again, title)

    is UserMessage.Unchecked -> resources.getString(R.string.try_again_unchecked, title)

    is UserMessage.RemixDeleted -> resources.getString(R.string.remix_deleted, title)

    is UserMessage.TrackUnplayable -> resources.getString(
        when (reason) {
            BrokenReason.NO_AUDIO -> R.string.track_unplayable_no_audio
            BrokenReason.UNAVAILABLE -> R.string.track_unplayable_unavailable
        },
        title
    )
}
