package io.github.pranavm716.vgmixr.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.pranavm716.vgmixr.data.offline.RoomOfflineStore
import io.github.pranavm716.vgmixr.data.stream.CachingStreamResolver
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.history.PlayHistory
import io.github.pranavm716.vgmixr.domain.offline.LookupPacer
import io.github.pranavm716.vgmixr.domain.offline.OfflineDownloader
import io.github.pranavm716.vgmixr.domain.offline.OfflineStore
import io.github.pranavm716.vgmixr.domain.offline.OfflineSync
import io.github.pranavm716.vgmixr.domain.offline.PacedStreamResolver
import io.github.pranavm716.vgmixr.domain.offline.RECENT_LIMIT
import io.github.pranavm716.vgmixr.domain.playback.BrokenSources
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.PlaybackDiagnostics
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.settings.SortSettings
import io.github.pranavm716.vgmixr.playback.ChunkedDataSource
import io.github.pranavm716.vgmixr.playback.MediaDownloader
import io.github.pranavm716.vgmixr.playback.StreamUriResolver
import java.time.Clock
import java.util.concurrent.Executors
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object OfflineModule {
    @Provides
    fun provideOfflineStore(store: RoomOfflineStore): OfflineStore = store

    @Provides
    fun provideOfflineDownloader(downloader: MediaDownloader): OfflineDownloader = downloader

    /** Media3's own database, for the cache's index and the download list. */
    @Provides
    @Singleton
    fun provideMediaDatabase(@ApplicationContext context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        database: DatabaseProvider,
        cache: Cache,
        resolver: CachingStreamResolver,
        diagnostics: PlaybackDiagnostics,
        clock: Clock
    ): DownloadManager {
        val paced = PacedStreamResolver(resolver, LookupPacer()) { clock.instant() }
        val streams = ResolvingDataSource.Factory(
            // In byte ranges: YouTube slows open-ended requests to playback speed.
            ChunkedDataSource.Factory(
                DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
            ),
            StreamUriResolver(paced, diagnostics)
        )
        return DownloadManager(
            context,
            database,
            cache,
            streams,
            Executors.newSingleThreadExecutor()
        ).apply { maxParallelDownloads = 1 }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    fun provideOfflineSync(
        catalog: CatalogRepository,
        playlists: PlaylistRepository,
        sortSettings: SortSettings,
        brokenSources: BrokenSources,
        store: OfflineStore,
        downloader: OfflineDownloader,
        history: PlayHistory,
        playback: PlaybackController,
        diagnostics: PlaybackDiagnostics
    ) = OfflineSync(
        catalog = catalog.catalog,
        // Every playlist in full, in the Playlists tab's order.
        playlists = playlists.playlists.flatMapLatest { summaries ->
            if (summaries.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(summaries.map { playlists.playlist(it.id) }) { it.filterNotNull() }
            }
        },
        remixSort = sortSettings.remixes,
        brokenVideos = brokenSources.marks.map { it.keys },
        store = store,
        downloader = downloader,
        recentlyPlayed = history.plays
            .map { plays -> plays.asReversed().map { it.trackId }.distinct().take(RECENT_LIMIT) }
            .distinctUntilChanged(),
        next = playback.state
            .map { it.queue.entries.getOrNull(it.queue.current + 1)?.trackId }
            .distinctUntilChanged()
            .onEach { diagnostics.log("offline: next $it") }
    )
}
