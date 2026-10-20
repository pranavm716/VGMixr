package io.github.pranavm716.vgmixr.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.github.pranavm716.vgmixr.MainActivity
import io.github.pranavm716.vgmixr.data.stream.CachingStreamResolver
import io.github.pranavm716.vgmixr.data.stream.describe
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.playback.FailoverAction
import io.github.pranavm716.vgmixr.domain.playback.PlaybackDiagnostics
import io.github.pranavm716.vgmixr.domain.playback.QueueEntry
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.playback.SourceFailure
import io.github.pranavm716.vgmixr.domain.playback.failover
import io.github.pranavm716.vgmixr.domain.playback.playableStreams
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Session extra naming the [ResolveFailure] that stopped playback, if any. */
const val STOPPED_BY = "stoppedBy"

/**
 * Plays in the background with a media notification. Queue items arrive as bare track ids and
 * become stream items from the catalog here; a failing source moves on to the next one.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject
    lateinit var catalogRepository: CatalogRepository

    @Inject
    lateinit var resolver: CachingStreamResolver

    @Inject
    lateinit var diagnostics: PlaybackDiagnostics

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val catalog = MutableStateFlow<Catalog?>(null)
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch { catalogRepository.catalog.collect { catalog.value = it } }

        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        val streams = ResolvingDataSource.Factory(http, StreamUriResolver(resolver, diagnostics))
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(streams))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val session = MediaSession.Builder(this, player)
            .setCallback(Callback())
            .setSessionActivity(openApp)
            .build()
        player.addListener(Failover(player, session))
        this.session = session
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private inner class Callback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val items = SettableFuture.create<MutableList<MediaItem>>()
            scope.launch {
                val catalog = catalog.filterNotNull().first()
                mediaSession.setStoppedBy(null)
                items.set(
                    mediaItems.map { catalog.itemFor(it.mediaId, queued = it.isQueued) }
                        .toMutableList()
                )
            }
            return items
        }
    }

    private inner class Failover(private val player: Player, private val session: MediaSession) :
        Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val why = when (reason) {
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "previous ended"
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "seek or skip"
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "queue changed"
                else -> "repeat"
            }
            diagnostics.log(
                "now ${mediaItem?.mediaId} source ${mediaItem?.sourceIndex} " +
                    "(#${player.currentMediaItemIndex} of ${player.mediaItemCount}, $why)"
            )
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) session.setStoppedBy(null)
        }

        override fun onPlayerError(error: PlaybackException) {
            val item = player.currentMediaItem ?: return
            val catalog = catalog.value ?: return
            val failure = error.sourceFailure()
            if (failure == SourceFailure.ThisSource) item.videoId?.let(resolver::forget)
            val track = catalog.tracks.firstOrNull { it.id == item.mediaId }
            val sourceCount = track?.let { playableStreams(it.sources).size } ?: 0
            val action = failover(failure, item.sourceIndex, sourceCount)
            diagnostics.log(
                "error ${item.mediaId} source ${item.sourceIndex}/$sourceCount: " +
                    "${error.errorCodeName} ${error.describe()} -> $failure, $action"
            )
            when (action) {
                is FailoverAction.TrySource -> {
                    val next = catalog.itemFor(item.mediaId, action.index, item.isQueued)
                    player.replaceMediaItem(player.currentMediaItemIndex, next)
                    player.prepare()
                    player.play()
                }

                FailoverAction.SkipTrack -> if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }

                is FailoverAction.Stop -> session.setStoppedBy(action.failure)
            }
        }
    }
}

private fun MediaSession.setStoppedBy(failure: ResolveFailure?) {
    sessionExtras = Bundle().apply { failure?.let { putString(STOPPED_BY, it.name) } }
}

/** The item for [trackId]; a track gone from the catalog gets an item that is skipped. */
private fun Catalog.itemFor(trackId: String, sourceIndex: Int = 0, queued: Boolean): MediaItem {
    val track = tracks.firstOrNull { it.id == trackId }
        ?: return queueRequest(
            QueueEntry(trackId, queued)
        ).buildUpon().setUri(NO_SOURCE_URI).build()
    val familyName = families.firstOrNull { it.id == track.familyId }?.name.orEmpty()
    return trackMediaItem(track, familyName, sourceIndex, queued)
}

private val MediaItem.videoId: String?
    get() = localConfiguration?.uri?.toString()
        ?.takeIf { it.startsWith(STREAM_URI_PREFIX) }
        ?.removePrefix(STREAM_URI_PREFIX)
