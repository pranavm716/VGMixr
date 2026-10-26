package io.github.pranavm716.vgmixr.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.pranavm716.vgmixr.data.offline.RoomOfflineStore
import io.github.pranavm716.vgmixr.domain.offline.OfflineStore

@Module
@InstallIn(SingletonComponent::class)
abstract class OfflineModule {
    @Binds
    abstract fun bindOfflineStore(store: RoomOfflineStore): OfflineStore
}
