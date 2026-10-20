package io.github.pranavm716.vgmixr.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
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

    override fun play(trackIds: List<String>, startIndex: Int) = withController {
        it.setMediaItems(
            trackIds.map { id ->
                MediaItem.Builder().setMediaId(id).build()
            },
            startIndex,
            0
        )
        it.prepare()
        it.play()
    }

    private fun withController(block: (MediaController) -> Unit) =
        controller.addListener({ block(controller.get()) }, main)

    private fun update(controller: MediaController) {
        _state.value = PlaybackState(
            trackId = controller.currentMediaItem?.mediaId,
            isPlaying = controller.isPlaying,
            stoppedBy = controller.sessionExtras.getString(STOPPED_BY)?.let(ResolveFailure::valueOf)
        )
    }
}
