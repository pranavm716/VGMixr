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
import io.github.pranavm716.vgmixr.domain.playback.BrokenMark
import io.github.pranavm716.vgmixr.domain.playback.BrokenSources
import io.github.pranavm716.vgmixr.domain.playback.FailedSource
import io.github.pranavm716.vgmixr.domain.playback.FailoverAction
import io.github.pranavm716.vgmixr.domain.playback.NewMark
import io.github.pranavm716.vgmixr.domain.playback.PlaybackDiagnostics
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.QueueEntry
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.playback.SourceFailure
import io.github.pranavm716.vgmixr.domain.playback.nextSource
import io.github.pranavm716.vgmixr.domain.playback.onSourceFailure
import io.github.pranavm716.vgmixr.domain.playback.playableStreams
import java.time.Clock
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
 * become stream items from the catalog here; a failing source moves on to the next one. A video
 * that is dead for the app is marked broken and skipped from then on.
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

    @Inject
    lateinit var brokenSources: BrokenSources

    @Inject
    lateinit var clock: Clock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val catalog = MutableStateFlow<Catalog?>(null)
    private val marks = MutableStateFlow<Map<String, BrokenMark>>(emptyMap())
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch { catalogRepository.catalog.collect { catalog.value = it } }
        scope.launch { brokenSources.marks.collect { marks.value = it } }

        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        // The resolver runs on the loading thread; a StateFlow's value is safe to read there.
        val streams = ResolvingDataSource.Factory(
            http,
            StreamUriResolver(resolver, diagnostics) { marks.value[it] }
        )
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(streams)
                    .setLoadErrorHandlingPolicy(FailFastLoadErrorPolicy())
            )
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
            // Opens now playing; SINGLE_TOP hands it to the running activity (onNewIntent).
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_NOW_PLAYING)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
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
                // Read fresh, so a track whose marks were just cleared (Try again) plays its sources.
                val fresh = brokenSources.marks.first().also { marks.value = it }
                mediaSession.setStoppedBy(null)
                items.set(
                    mediaItems.map { catalog.startItem(it.mediaId, it.context, fresh) }
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
            // A failed or refused URL is never reused: the next try looks the video up again.
            if (failure !is SourceFailure.Everything) item.videoId?.let(resolver::forget)
            val track = catalog.tracks.firstOrNull { it.id == item.mediaId }
            val streams = track?.let { playableStreams(it.sources) }.orEmpty()
            val failed = FailedSource(
                trackId = item.mediaId,
                title = track?.title.orEmpty(),
                sourceIndex = item.sourceIndex,
                videoId = item.videoId,
                retried = item.retried
            )
            val outcome = onSourceFailure(failure, failed, streams, marks.value)
            val action = outcome.action
            diagnostics.log(
                "error ${item.mediaId} source ${item.sourceIndex}/${streams.size}: " +
                    "${error.errorCodeName} ${error.describe()} -> $failure, $action"
            )
            outcome.mark?.let { mark(it, item.mediaId) }
            outcome.event?.toCommand()?.let { (command, args) ->
                session.broadcastCustomCommand(command, args)
            }
            when (action) {
                FailoverAction.RetrySource -> {
                    val again = catalog.itemFor(
                        item.mediaId,
                        item.context,
                        item.sourceIndex,
                        retried = true
                    )
                    player.replaceMediaItem(player.currentMediaItemIndex, again)
                    player.prepare()
                    player.play()
                }

                is FailoverAction.TrySource -> {
                    val next = catalog.itemFor(item.mediaId, item.context, action.index)
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

        /**
         * Skips [mark]'s video at once, before the write to Room lands, so items opened meanwhile
         * never ask YouTube for it again; Room's copy replaces this one when it arrives.
         */
        private fun mark(mark: NewMark, trackId: String) {
            marks.value += mark.videoId to BrokenMark(
                mark.videoId,
                trackId,
                mark.reason,
                mark.detail,
                clock.instant(),
                null
            )
            scope.launch {
                brokenSources.mark(mark.videoId, trackId, mark.reason, mark.detail)
                diagnostics.log("mark ${mark.videoId} broken: ${mark.reason.key} ($trackId)")
            }
        }
    }
}

private fun MediaSession.setStoppedBy(failure: ResolveFailure?) {
    sessionExtras = Bundle().apply { failure?.let { putString(STOPPED_BY, it.name) } }
}

/**
 * The item a queued [trackId] starts as: its first source whose video isn't in [marks]. A track
 * with every source marked gets an item that is skipped.
 */
private fun Catalog.startItem(
    trackId: String,
    context: QueueContext,
    marks: Map<String, BrokenMark>
): MediaItem {
    val streams = tracks.firstOrNull { it.id == trackId }
        ?.let { playableStreams(it.sources) }
        .orEmpty()
    return itemFor(trackId, context, nextSource(streams, 0, marks.keys) ?: streams.size)
}

/**
 * The item for [trackId] playing source [sourceIndex] (none past the last one, so it is
 * skipped); a track gone from the catalog gets an item that is skipped.
 */
private fun Catalog.itemFor(
    trackId: String,
    context: QueueContext,
    sourceIndex: Int,
    retried: Boolean = false
): MediaItem {
    val track = tracks.firstOrNull { it.id == trackId }
        ?: return queueRequest(
            QueueEntry(trackId, context)
        ).buildUpon().setUri(NO_SOURCE_URI).build()
    val familyName = families.firstOrNull { it.id == track.familyId }?.name.orEmpty()
    return trackMediaItem(track, familyName, sourceIndex, context, retried)
}

private val MediaItem.videoId: String?
    get() = localConfiguration?.uri?.toString()
        ?.takeIf { it.startsWith(STREAM_URI_PREFIX) }
        ?.removePrefix(STREAM_URI_PREFIX)
