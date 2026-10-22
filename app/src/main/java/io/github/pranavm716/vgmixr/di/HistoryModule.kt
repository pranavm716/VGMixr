package io.github.pranavm716.vgmixr.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.pranavm716.vgmixr.data.history.RoomPlayHistory
import io.github.pranavm716.vgmixr.domain.history.PlayHistory

@Module
@InstallIn(SingletonComponent::class)
object HistoryModule {
    @Provides
    fun providePlayHistory(history: RoomPlayHistory): PlayHistory = history
}
