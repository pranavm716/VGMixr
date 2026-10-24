package io.github.pranavm716.vgmixr.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.pranavm716.vgmixr.data.usertrack.RoomUserTrackRepository
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrackRepository

@Module
@InstallIn(SingletonComponent::class)
object UserTrackModule {
    @Provides
    fun provideUserTrackRepository(repository: RoomUserTrackRepository): UserTrackRepository =
        repository
}
