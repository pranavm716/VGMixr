package io.github.pranavm716.vgmixr.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.pranavm716.vgmixr.data.playlist.RoomPlaylistRepository
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository

@Module
@InstallIn(SingletonComponent::class)
object PlaylistModule {
    @Provides
    fun providePlaylistRepository(repository: RoomPlaylistRepository): PlaylistRepository =
        repository
}
