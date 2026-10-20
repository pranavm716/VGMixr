package io.github.pranavm716.vgmixr.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.pranavm716.vgmixr.data.stream.CachingStreamResolver
import io.github.pranavm716.vgmixr.data.stream.NewPipeStreamResolver
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.StreamResolver
import io.github.pranavm716.vgmixr.playback.MediaControllerPlaybackController
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {
    @Provides
    @Singleton
    fun provideCachingStreamResolver(resolver: NewPipeStreamResolver, clock: Clock) =
        CachingStreamResolver(resolver, clock)

    @Provides
    fun provideStreamResolver(resolver: CachingStreamResolver): StreamResolver = resolver

    @Provides
    fun providePlaybackController(
        controller: MediaControllerPlaybackController
    ): PlaybackController = controller
}
