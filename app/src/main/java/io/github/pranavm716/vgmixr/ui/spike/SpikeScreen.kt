package io.github.pranavm716.vgmixr.ui.spike

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pranavm716.vgmixr.domain.playback.StreamResolution
import io.github.pranavm716.vgmixr.domain.playback.StreamResolver
import io.github.pranavm716.vgmixr.ui.ScreenHeader
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Throwaway spike (step 8): resolve a video id with NewPipe and play it with a bare ExoPlayer.
// Removed before the PR; the real player goes behind PlaybackController.

private data class SpikeClip(
    val label: String,
    val videoId: String,
    val startMs: Long,
    val endMs: Long?
)

private val clips = listOf(
    SpikeClip("Route 101 (RSE), whole video", "WMj9b4vYTXU", 0, null),
    SpikeClip("SV slice: Across the Skies of Paldea, 2:13-3:29", "VzG7oP2oSUQ", 133_000, 209_000)
)

@HiltViewModel
class SpikeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: StreamResolver
) : ViewModel() {
    private val _log = MutableStateFlow(listOf("Tap a clip."))
    val log: StateFlow<List<String>> = _log
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val player = ExoPlayer.Builder(context).build()
    private var startedAt = 0L
    private var job: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && startedAt != 0L) {
                    log(
                        "first audio after ${SystemClock.elapsedRealtime() - startedAt} ms (since resolved)"
                    )
                    startedAt = 0L
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                val name = when (state) {
                    Player.STATE_IDLE -> "idle"
                    Player.STATE_BUFFERING -> "buffering"
                    Player.STATE_READY -> "ready"
                    else -> "ended"
                }
                log("state: $name at ${player.currentPosition.seconds()}")
            }

            override fun onPlayerError(error: PlaybackException) {
                log(
                    "player error: ${error.errorCodeName}: ${error.cause?.message ?: error.message}"
                )
            }
        })
        viewModelScope.launch {
            while (isActive) {
                // A clipped item's position starts at 0, so add the clip start back.
                val offset = player.currentMediaItem?.clippingConfiguration?.startPositionMs ?: 0
                _status.value = "position ${(offset + player.currentPosition).seconds()}, " +
                    "buffered ${(player.bufferedPosition - player.currentPosition).seconds()} ahead"
                delay(500)
            }
        }
    }

    // URLs resolved on the PC over IPv4 (YouTube bot-checks this network's IPv6), pushed with adb
    // to Android/data/<package>/files/<videoId>.txt.
    private fun pcResolved(videoId: String): StreamResolution {
        val file = java.io.File(context.getExternalFilesDir(null), "$videoId.txt")
        return if (file.exists()) {
            StreamResolution.Resolved(file.readText().trim(), "from PC", 0)
        } else {
            StreamResolution.Unavailable("no ${file.path}")
        }
    }

    fun play(index: Int, fromPc: Boolean = false) {
        val clip = clips[index]
        job?.cancel()
        player.stop()
        _log.value = listOf(clip.label)
        job = viewModelScope.launch {
            val t0 = SystemClock.elapsedRealtime()
            val result = if (fromPc) pcResolved(clip.videoId) else resolver.resolve(clip.videoId)
            log("resolve: ${SystemClock.elapsedRealtime() - t0} ms")
            when (result) {
                is StreamResolution.Resolved -> {
                    log("${result.mimeType}, ${result.bitrate} kbps")
                    val clipping = MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs)
                        .apply { clip.endMs?.let { setEndPositionMs(it) } }
                        .build()
                    player.setMediaItem(
                        MediaItem.Builder()
                            .setUri(result.url)
                            .setClippingConfiguration(clipping)
                            .build()
                    )
                    startedAt = SystemClock.elapsedRealtime()
                    player.prepare()
                    player.play()
                }

                else -> log("not resolved: $result")
            }
        }
    }

    fun stop() {
        job?.cancel()
        player.stop()
        log("stopped")
    }

    private fun log(line: String) {
        Log.i("VGMixrSpike", line)
        _log.update { it + line }
    }

    override fun onCleared() = player.release()
}

private fun Long.seconds(): String = "%d:%02d".format(this / 60_000, this / 1000 % 60)

@Composable
fun SpikeScreen(viewModel: SpikeViewModel = hiltViewModel()) {
    val log by viewModel.log.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ScreenHeader("Playback spike", Modifier.padding(start = 0.dp))
        clips.forEachIndexed { i, clip ->
            Text(clip.label)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.play(i) }) { Text("Resolve on phone") }
                OutlinedButton(onClick = { viewModel.play(i, fromPc = true) }) { Text("PC URL") }
            }
        }
        OutlinedButton(onClick = viewModel::stop) { Text("Stop") }
        Text(status, fontFamily = FontFamily.Monospace)
        log.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}
