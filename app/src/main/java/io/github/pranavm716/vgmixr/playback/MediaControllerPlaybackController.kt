package io.github.pranavm716.vgmixr.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pranavm716.vgmixr.domain.playback.PlayQueue
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueEdit
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [PlaybackController] over a Media3 [MediaController] connected to [PlaybackService]. Commands
 * given before the connection is up run once it is. Create on the main thread.
 */
@Singleton
class MediaControllerPlaybackController @Inject constructor(@ApplicationContext context: Context) :
    PlaybackController {
    private val main = ContextCompat.getMainExecutor(context)
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val controller = MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, PlaybackService::class.java))
    )
        .setListener(object : MediaController.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: Bundle) =
                update(controller)
        })
        .buildAsync()

    init {
        withController { controller ->
            controller.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = update(controller)
            })
            update(controller)
        }
    }

    override fun playFrom(trackIds: List<String>, index: Int) = withController {
        it.apply(it.queue().playFrom(trackIds, index))
    }

    override fun addToQueue(trackIds: List<String>) = withController {
        it.apply(it.queue().add(trackIds, hasEnded = it.playbackState == Player.STATE_ENDED))
    }

    private fun withController(block: (MediaController) -> Unit) =
        controller.addListener({ block(controller.get()) }, main)

    private fun update(controller: MediaController) {
        _state.value = PlaybackState(
            queue = controller.queue(),
            trackId = controller.currentMediaItem?.mediaId,
            isPlaying = controller.isPlaying,
            isLoading = controller.playWhenReady &&
                controller.playbackState == Player.STATE_BUFFERING,
            stoppedBy = controller.sessionExtras.getString(STOPPED_BY)?.let(ResolveFailure::valueOf)
        )
    }
}

private fun Player.queue(): PlayQueue = PlayQueue(
    entries = (0 until mediaItemCount).map { getMediaItemAt(it).queueEntry() },
    current = if (mediaItemCount == 0) -1 else currentMediaItemIndex
)

private fun Player.apply(edit: QueueEdit) {
    when (edit) {
        is QueueEdit.Replace -> {
            setMediaItems(edit.entries.map(::queueRequest), edit.startIndex, 0)
            prepare()
            play()
        }

        is QueueEdit.Insert -> addMediaItems(edit.index, edit.entries.map(::queueRequest))
    }
}
