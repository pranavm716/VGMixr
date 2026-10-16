package io.github.pranavm716.vgmixr.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.pranavm716.vgmixr.data.db.CatalogDao
import io.github.pranavm716.vgmixr.data.db.ImportStateDao
import io.github.pranavm716.vgmixr.data.db.UserDataDao
import io.github.pranavm716.vgmixr.data.db.VGMixrDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VGMixrDatabase =
        Room.databaseBuilder(context, VGMixrDatabase::class.java, "vgmixr.db").build()

    @Provides
    fun provideCatalogDao(db: VGMixrDatabase): CatalogDao = db.catalogDao()

    @Provides
    fun provideUserDataDao(db: VGMixrDatabase): UserDataDao = db.userDataDao()

    @Provides
    fun provideImportStateDao(db: VGMixrDatabase): ImportStateDao = db.importStateDao()
}
