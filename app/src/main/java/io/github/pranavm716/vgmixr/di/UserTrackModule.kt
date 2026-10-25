package io.github.pranavm716.vgmixr.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.pranavm716.vgmixr.data.stream.NewPipeVideoLookup
import io.github.pranavm716.vgmixr.data.stream.youTubeClient
import io.github.pranavm716.vgmixr.data.usertrack.FileThumbnailStore
import io.github.pranavm716.vgmixr.data.usertrack.RoomUserTrackRepository
import io.github.pranavm716.vgmixr.domain.usertrack.ThumbnailStore
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrackRepository
import io.github.pranavm716.vgmixr.domain.usertrack.VideoLookup
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
object UserTrackModule {
    @Provides
    fun provideUserTrackRepository(repository: RoomUserTrackRepository): UserTrackRepository =
        repository

    @Provides
    fun provideVideoLookup(lookup: NewPipeVideoLookup): VideoLookup = lookup

    @Provides
    @Singleton
    fun provideThumbnailStore(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher
    ): ThumbnailStore =
        FileThumbnailStore(File(context.filesDir, "thumbnails"), youTubeClient(), io)
}
