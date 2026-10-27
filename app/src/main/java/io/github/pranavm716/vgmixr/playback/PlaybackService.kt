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
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.github.pranavm716.vgmixr.MainActivity
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.data.stream.CachingStreamResolver
import io.github.pranavm716.vgmixr.data.stream.describe
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.dimmed
import io.github.pranavm716.vgmixr.domain.history.PlayCounter
import io.github.pranavm716.vgmixr.domain.history.PlayHistory
import io.github.pranavm716.vgmixr.domain.history.RecentPlays
import io.github.pranavm716.vgmixr.domain.offline.Connectivity
import io.github.pranavm716.vgmixr.domain.offline.OfflineStore
import io.github.pranavm716.vgmixr.domain.playback.BrokenMark
import io.github.pranavm716.vgmixr.domain.playback.BrokenSources
import io.github.pranavm716.vgmixr.domain.playback.FailedSource
import io.github.pranavm716.vgmixr.domain.playback.FailoverAction
import io.github.pranavm716.vgmixr.domain.playback.NewMark
import io.github.pranavm716.vgmixr.domain.playback.PlaybackDiagnostics
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.QueueEdit
import io.github.pranavm716.vgmixr.domain.playback.QueueEntry
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.playback.ShuffleSetting
import io.github.pranavm716.vgmixr.domain.playback.SourceFailure
import io.github.pranavm716.vgmixr.domain.playback.leftCatalog
import io.github.pranavm716.vgmixr.domain.playback.nextSource
import io.github.pranavm716.vgmixr.domain.playback.onSourceFailure
import io.github.pranavm716.vgmixr.domain.playback.playableStreams
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.ui.components.coverFamilies
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
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

    @Inject
    lateinit var playHistory: PlayHistory

    @Inject
    lateinit var shuffleSetting: ShuffleSetting

    @Inject
    lateinit var recentPlays: RecentPlays

    @Inject
    lateinit var playlists: PlaylistRepository

    @Inject
    lateinit var offline: OfflineStore

    @Inject
    lateinit var mediaCache: Cache

    @Inject
    lateinit var connectivity: Connectivity

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val artwork by lazy {
        NotificationArtwork(File(cacheDir, "artwork"), coverFamilies(assets))
    }
    private val catalog = MutableStateFlow<Catalog?>(null)
    private val marks = MutableStateFlow<Map<String, BrokenMark>>(emptyMap())
    private val favorites = MutableStateFlow<Set<String>>(emptySet())
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch { catalogRepository.catalog.collect { catalog.value = it } }
        // A downloaded video plays from the phone whatever its mark says.
        scope.launch {
            combine(brokenSources.marks, offline.onPhone) { marks, onPhone -> marks - onPhone }
                .collect { marks.value = it }
        }

        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        // The resolver runs on the loading thread; a StateFlow's value is safe to read there.
        val streams = ResolvingDataSource.Factory(
            http,
            StreamUriResolver(resolver, diagnostics) { marks.value[it] }
        )
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    // Downloaded and cached audio first; streams only for what isn't on the phone.
                    CacheDataSource.Factory()
                        .setCache(mediaCache)
                        .setCacheKeyFactory { streamCacheKey(it.uri.toString()) }
                        .setUpstreamDataSourceFactory(streams)
                        // Read only: downloads alone write, so the player never holds a video a
                        // download is waiting for (recent plays are downloaded as cached entries).
                        .setCacheWriteDataSinkFactory(null)
                        // Wait for a download writing the same video rather than streaming it twice.
                        .setFlags(
                            CacheDataSource.FLAG_BLOCK_ON_CACHE or
                                CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
                        )
                )
                    .setLoadErrorHandlingPolicy(
                        FailFastLoadErrorPolicy {
                            connectivity.online.value
                        }
                    )
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
        // Shuffle rearranges the items themselves (Shuffling), so the queue screen shows what
        // plays next; the player's own shuffle order stays the item order.
        player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(0))
        player.shuffleModeEnabled = shuffleSetting.isOn()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            // Opens now playing; SINGLE_TOP hands it to the running activity (onNewIntent).
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_NOW_PLAYING)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                // The status bar chip's icon: the app icon's D-pad.
                setSmallIcon(R.drawable.ic_notification)
            }
        )
        val session = MediaSession.Builder(this, player)
            .setCallback(Callback())
            .setSessionActivity(openApp)
            .setMediaButtonPreferences(
                mediaButtons(this, player.shuffleModeEnabled, favorite = false)
            )
            .build()
        player.addListener(Failover(player, session))
        // Queued tracks that leave the catalog (a new manifest, a deleted remix) become items
        // with no source, so the player skips them; the playing one plays to its end.
        scope.launch {
            catalog.filterNotNull().collect { catalog ->
                for (index in catalog.leftCatalog(player.queue())) {
                    val item = player.getMediaItemAt(index)
                    if (item.localConfiguration?.uri?.toString() == NO_SOURCE_URI) continue
                    player.replaceMediaItem(
                        index,
                        catalog.startItem(
                            item.mediaId,
                            item.context,
                            item.listIndex,
                            marks.value,
                            artwork
                        )
                    )
                }
            }
        }
        player.addListener(PlayRecording(player))
        player.addListener(Shuffling(player, shuffleSetting, recentPlays))
        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.containsAny(
                            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                            Player.EVENT_MEDIA_ITEM_TRANSITION
                        )
                    ) {
                        updateButtons(session)
                    }
                }
            }
        )
        scope.launch {
            playlists.favorites.collect {
                favorites.value = it
                updateButtons(session)
            }
        }
        this.session = session
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** The notification's shuffle and favorite buttons, showing the player's state. */
    private fun updateButtons(session: MediaSession) {
        val player = session.player
        val favorite = player.currentMediaItem?.mediaId in favorites.value
        session.setMediaButtonPreferences(mediaButtons(this, player.shuffleModeEnabled, favorite))
    }

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
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(toggleShuffle)
                        .add(toggleFavorite)
                        .add(resortCommand)
                        .build()
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val player = session.player
            when (customCommand.customAction) {
                TOGGLE_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled

                RESORT -> resortOf(args)?.let { (trackIds, context) ->
                    val edits = player.queue().resort(context, trackIds, player.shuffleModeEnabled)
                    if (edits.isNotEmpty()) scope.launch { player.applyResolving(edits) }
                }

                TOGGLE_FAVORITE -> player.currentMediaItem?.mediaId?.let { trackId ->
                    val favorite = trackId in favorites.value
                    scope.launch { playlists.setFavorite(trackId, !favorite) }
                }

                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val items = SettableFuture.create<MutableList<MediaItem>>()
            scope.launch {
                val catalog = catalog.filterNotNull().first()
                // Read fresh, so a track whose marks were just cleared (Try again) plays its sources.
                val fresh = freshMarks().also { marks.value = it }
                mediaSession.setStoppedBy(null)
                items.set(
                    mediaItems.map {
                        catalog.startItem(it.mediaId, it.context, it.listIndex, fresh, artwork)
                    }.toMutableList()
                )
            }
            return items
        }
    }

    /** The marks as they are now, minus downloaded videos (which play whatever their mark). */
    private suspend fun freshMarks(): Map<String, BrokenMark> =
        brokenSources.marks.first() - offline.onPhone.first()

    /**
     * Applies [edits] to the session's own player. Its new entries are built here, as
     * [Callback.onAddMediaItems] builds the app's; the rest keep their items, so nothing playing
     * restarts.
     */
    private suspend fun Player.applyResolving(edits: List<QueueEdit>) {
        val catalog = catalog.filterNotNull().first()
        val fresh = freshMarks().also { marks.value = it }
        edits.forEach { edit ->
            if (edit is QueueEdit.Insert) {
                addMediaItems(
                    edit.index,
                    edit.entries.map {
                        catalog.startItem(it.trackId, it.context, it.listIndex, fresh, artwork)
                    }
                )
            } else {
                apply(edit)
            }
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
            // A retired track still playing keeps its sources for retries and other uploads.
            val track = catalog.trackOrRetired(item.mediaId)
            val streams = track?.let { playableStreams(it.sources) }.orEmpty()
            val failed = FailedSource(
                trackId = item.mediaId,
                title = track?.title.orEmpty(),
                sourceIndex = item.sourceIndex,
                videoId = item.videoId,
                retried = item.retried
            )
            val outcome = onSourceFailure(
                failure,
                failed,
                streams,
                marks.value,
                offline = !connectivity.online.value
            )
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
                        item.listIndex,
                        item.sourceIndex,
                        artwork,
                        retried = true
                    )
                    player.replaceMediaItem(player.currentMediaItemIndex, again)
                    player.prepare()
                    player.play()
                }

                is FailoverAction.TrySource -> {
                    val next = catalog.itemFor(
                        item.mediaId,
                        item.context,
                        item.listIndex,
                        action.index,
                        artwork
                    )
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

    /**
     * Writes a play to the history when [PlayCounter] says a visit counts: the position is checked
     * once a second while the player plays.
     */
    private inner class PlayRecording(private val player: Player) : Player.Listener {
        private val counter = PlayCounter()
        private var ticker: Job? = null

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) counter.onEnded()?.let(::record)
            counter.onTransition(mediaItem?.mediaId, reason.toTransition())
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) counter.onEnded()?.let(::record)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            ticker?.cancel()
            ticker = if (isPlaying) {
                scope.launch {
                    while (isActive) {
                        counter.onPlaying(player.currentPosition)?.let(::record)
                        delay(PLAY_CHECK_INTERVAL_MS)
                    }
                }
            } else {
                null
            }
        }

        private fun record(trackId: String) {
            scope.launch {
                playHistory.record(trackId)
                diagnostics.log("play $trackId")
            }
        }
    }
}

/**
 * Turning shuffle on shuffles "Next from"; turning it off puts it back in list order. Here, so a
 * flip from the app, the notification or the lock screen all rearrange the same way. The setting
 * is saved for the next launch. Tracks heard in the last hour stay out of the first 10.
 */
private class Shuffling(
    private val player: Player,
    private val setting: ShuffleSetting,
    private val recentPlays: RecentPlays
) : Player.Listener {
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        setting.set(shuffleModeEnabled)
        val queue = player.queue()
        player.apply(
            if (shuffleModeEnabled) {
                queue.shuffle(
                    Random.Default,
                    recentPlays.trackIds()
                )
            } else {
                queue.unshuffle()
            }
        )
    }
}

private const val PLAY_CHECK_INTERVAL_MS = 1_000L

private fun Int.toTransition(): PlayCounter.Transition = when (this) {
    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> PlayCounter.Transition.AUTO
    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> PlayCounter.Transition.SKIP
    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> PlayCounter.Transition.REPEAT
    else -> PlayCounter.Transition.QUEUE_CHANGED
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
    listIndex: Int?,
    marks: Map<String, BrokenMark>,
    artwork: NotificationArtwork
): MediaItem {
    // A retired track is dimmed, so it gets no source either.
    val track = trackOrRetired(trackId)
    val streams = track?.let { playableStreams(it.sources) }.orEmpty()
    // A dimmed track (broken, or offline and not on the phone) gets no source, so it is skipped.
    val sourceIndex = if (track?.dimmed == true) {
        streams.size
    } else {
        nextSource(streams, 0, marks.keys) ?: streams.size
    }
    return itemFor(trackId, context, listIndex, sourceIndex, artwork)
}

/**
 * The item for [trackId] playing source [sourceIndex] (none past the last one, so it is
 * skipped); a deleted remix gets an item that is skipped.
 */
private fun Catalog.itemFor(
    trackId: String,
    context: QueueContext,
    listIndex: Int?,
    sourceIndex: Int,
    artwork: NotificationArtwork,
    retried: Boolean = false
): MediaItem {
    val track = trackOrRetired(trackId)
        ?: return queueRequest(
            QueueEntry(trackId, context, listIndex)
        ).buildUpon().setUri(NO_SOURCE_URI).build()
    val familyName = families.firstOrNull { it.id == track.familyId }?.name.orEmpty()
    val art = artwork.uriFor(track)
    return trackMediaItem(track, familyName, sourceIndex, context, retried, art, listIndex)
}

private val MediaItem.videoId: String?
    get() = localConfiguration?.uri?.toString()
        ?.takeIf { it.startsWith(STREAM_URI_PREFIX) }
        ?.removePrefix(STREAM_URI_PREFIX)
