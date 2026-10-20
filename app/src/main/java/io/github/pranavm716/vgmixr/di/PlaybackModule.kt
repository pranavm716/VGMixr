package io.github.pranavm716.vgmixr.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.pranavm716.vgmixr.data.stream.NewPipeStreamResolver
import io.github.pranavm716.vgmixr.domain.playback.StreamResolver

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {
    @Provides
    fun provideStreamResolver(resolver: NewPipeStreamResolver): StreamResolver = resolver
}
