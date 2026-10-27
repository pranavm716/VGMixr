package io.github.pranavm716.vgmixr.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.cache.Cache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.pranavm716.vgmixr.data.diagnostics.BrokenMarksMirror
import io.github.pranavm716.vgmixr.data.diagnostics.FileDiagnostics
import io.github.pranavm716.vgmixr.data.settings.PrefsShuffleSetting
import io.github.pranavm716.vgmixr.data.stream.CachingStreamResolver
import io.github.pranavm716.vgmixr.data.stream.NewPipeStreamResolver
import io.github.pranavm716.vgmixr.data.stream.RoomBrokenSources
import io.github.pranavm716.vgmixr.domain.history.PlayHistory
import io.github.pranavm716.vgmixr.domain.history.RecentPlays
import io.github.pranavm716.vgmixr.domain.playback.BrokenSources
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.PlaybackDiagnostics
import io.github.pranavm716.vgmixr.domain.playback.ShuffleSetting
import io.github.pranavm716.vgmixr.domain.playback.StreamResolver
import io.github.pranavm716.vgmixr.playback.MediaControllerPlaybackController
import io.github.pranavm716.vgmixr.playback.mediaCache
import java.io.File
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {
    @Provides
    @Singleton
    fun provideCachingStreamResolver(resolver: NewPipeStreamResolver, clock: Clock) =
        CachingStreamResolver(resolver, clock)

    @Provides
    @Singleton
    fun providePlaybackDiagnostics(
        @ApplicationContext context: Context,
        clock: Clock
    ): PlaybackDiagnostics =
        FileDiagnostics(File(diagnosticsFolder(context), "playback.log"), clock)

    @Provides
    @Singleton
    fun provideBrokenMarksMirror(
        @ApplicationContext context: Context,
        sources: BrokenSources,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher io: CoroutineDispatcher
    ) = BrokenMarksMirror(File(diagnosticsFolder(context), "broken.json"), sources, scope, io)

    /** The app's external files folder, which adb can read from a release build. */
    private fun diagnosticsFolder(context: Context): File =
        context.getExternalFilesDir(null) ?: context.filesDir

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideMediaCache(@ApplicationContext context: Context, database: DatabaseProvider): Cache =
        mediaCache(context, database)

    @Provides
    fun provideStreamResolver(resolver: CachingStreamResolver): StreamResolver = resolver

    @Provides
    fun provideBrokenSources(sources: RoomBrokenSources): BrokenSources = sources

    @Provides
    @Singleton
    fun provideRecentPlays(
        history: PlayHistory,
        clock: Clock,
        @ApplicationScope scope: CoroutineScope
    ) = RecentPlays(history, clock, scope)

    @Provides
    fun provideShuffleSetting(setting: PrefsShuffleSetting): ShuffleSetting = setting

    @Provides
    fun providePlaybackController(
        controller: MediaControllerPlaybackController
    ): PlaybackController = controller
}
