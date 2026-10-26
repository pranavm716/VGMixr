package io.github.pranavm716.vgmixr.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.pranavm716.vgmixr.data.catalog.AssetsManifestSource
import io.github.pranavm716.vgmixr.data.catalog.DefaultCatalogSync
import io.github.pranavm716.vgmixr.data.offline.OfflineCatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogSync
import io.github.pranavm716.vgmixr.domain.catalog.ManifestSource

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {
    @Provides
    fun provideManifestSource(@ApplicationContext context: Context): ManifestSource =
        AssetsManifestSource(context, "manifest.json")

    @Provides
    fun provideCatalogSync(sync: DefaultCatalogSync): CatalogSync = sync

    @Provides
    fun provideCatalogRepository(repository: OfflineCatalogRepository): CatalogRepository =
        repository
}
