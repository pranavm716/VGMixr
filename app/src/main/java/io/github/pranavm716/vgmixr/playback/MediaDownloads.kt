package io.github.pranavm716.vgmixr.playback

import android.app.Notification
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.di.ApplicationScope
import io.github.pranavm716.vgmixr.di.IoDispatcher
import io.github.pranavm716.vgmixr.domain.offline.OfflineDownloader
import io.github.pranavm716.vgmixr.domain.offline.OfflineStore
import io.github.pranavm716.vgmixr.domain.offline.OfflineSync
import io.github.pranavm716.vgmixr.domain.playback.BrokenSources
import io.github.pranavm716.vgmixr.domain.playback.PlaybackDiagnostics
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Downloads with Media3's download manager into the shared media cache, one video at a time,
 * resuming and retrying on its own. The manager runs on the main thread, so every call to it is
 * posted there.
 */
@UnstableApi
@Singleton
class MediaDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: DownloadManager,
    private val cache: Cache,
    private val store: OfflineStore,
    private val brokenSources: BrokenSources,
    private val diagnostics: PlaybackDiagnostics,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher
) : OfflineDownloader {
    private val main = Handler(Looper.getMainLooper())

    init {
        main.post { manager.addListener(Listener()) }
    }

    /**
     * Only the first of [videoIds] may run; the rest wait, held with their bytes kept, so the fill
     * order holds (the manager runs downloads in the order they were added) and a prefetch can
     * overtake a long album download without losing it.
     */
    override fun download(videoIds: List<String>) {
        main.post {
            val first = videoIds.firstOrNull()
            manager.currentDownloads
                .filter { it.request.id != first && it.stopReason == Download.STOP_REASON_NONE }
                .forEach { manager.setStopReason(it.request.id, WAITING) }
            videoIds.forEach {
                manager.addDownload(
                    DownloadRequest.Builder(it, Uri.parse(STREAM_URI_PREFIX + it))
                        .setCustomCacheKey(it)
                        .build(),
                    if (it == first) Download.STOP_REASON_NONE else WAITING
                )
            }
            startService()
        }
    }

    override fun remove(videoIds: List<String>) {
        main.post { videoIds.forEach(manager::removeDownload) }
        // What playback stored of a video has no download to remove, so the cache is cleared too.
        scope.launch(io) { videoIds.forEach(cache::removeResource) }
        diagnostics.log("offline: remove ${videoIds.joinToString()}")
    }

    /**
     * Brings Room in line with the downloads after a crash or a cleared folder: a video Room thinks
     * finished whose download isn't is fetched again, a finished download Room missed is recorded,
     * and a download Room doesn't know is removed, as is audio playback stored that nothing keeps.
     */
    suspend fun reconcile() {
        val entries = store.entries.first()
        val downloads = withContext(io) {
            buildMap {
                manager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) put(cursor.download.request.id, cursor.download)
                }
            }
        }
        entries.values.forEach { entry ->
            val download = downloads[entry.videoId]
            val done = download?.state == Download.STATE_COMPLETED
            if (entry.complete && !done) store.setIncomplete(entry.videoId)
            if (!entry.complete && done) store.setComplete(entry.videoId, download.bytesDownloaded)
        }
        val unknown = downloads.keys - entries.keys
        if (unknown.isNotEmpty()) remove(unknown.toList())
        // Audio playback stored for videos nothing keeps (a track played under 10 s, or skipped).
        val stray = withContext(io) { cache.keys } - entries.keys - unknown
        if (stray.isNotEmpty()) remove(stray.toList())
    }

    private fun startService() {
        try {
            DownloadService.start(context, VGMixrDownloadService::class.java)
        } catch (e: IllegalStateException) {
            // Started from the background (Android 12+ forbids it): the downloads still run while
            // the app's process lives, and the service starts with the next call from the app.
            diagnostics.log("offline: service not started (${e.message})")
        }
    }

    private inner class Listener : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            val videoId = download.request.id
            when (download.state) {
                Download.STATE_COMPLETED -> scope.launch {
                    store.setComplete(videoId, download.bytesDownloaded)
                    diagnostics.log(
                        "offline: downloaded $videoId (${download.bytesDownloaded / 1024} KB)"
                    )
                }

                Download.STATE_FAILED -> scope.launch {
                    diagnostics.log("offline: download $videoId failed: $finalException")
                    val broken = generateSequence<Throwable>(finalException) { it.cause }
                        .filterIsInstance<BrokenStreamException>()
                        .firstOrNull() ?: return@launch
                    val trackId = store.entries.first()[videoId]?.trackIds?.minOrNull()
                        ?: return@launch
                    // The sync then picks the track's next source.
                    brokenSources.mark(videoId, trackId, broken.reason, broken.detail)
                }

                else -> Unit
            }
        }
    }
}

/** The stop reason of a download held back for an earlier one in the fill order. */
private const val WAITING = 1

/** Starts the offline sync once the downloads and Room agree. */
@UnstableApi
class OfflineStarter @Inject constructor(
    private val downloader: MediaDownloader,
    private val sync: OfflineSync,
    @ApplicationScope private val scope: CoroutineScope
) {
    fun start() {
        scope.launch {
            downloader.reconcile()
            sync.run()
        }
    }
}

/**
 * Keeps the app alive while downloads run, with a progress notification. It has no scheduler, so
 * downloads only resume while the app runs, which it does whenever music plays.
 */
@UnstableApi
@AndroidEntryPoint
class VGMixrDownloadService :
    DownloadService(
        NOTIFICATION_ID,
        DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
        CHANNEL_ID,
        R.string.downloads_channel,
        0
    ) {
    @Inject
    lateinit var manager: DownloadManager

    override fun getDownloadManager(): DownloadManager = manager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification = DownloadNotificationHelper(this, CHANNEL_ID).buildProgressNotification(
        this,
        R.drawable.ic_download,
        null,
        null,
        downloads,
        notMetRequirements
    )

    private companion object {
        const val NOTIFICATION_ID = 2
        const val CHANNEL_ID = "downloads"
    }
}
