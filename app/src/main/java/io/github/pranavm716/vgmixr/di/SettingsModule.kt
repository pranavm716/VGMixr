package io.github.pranavm716.vgmixr.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.pranavm716.vgmixr.data.settings.PrefsAccentSetting
import io.github.pranavm716.vgmixr.data.settings.PrefsBackgroundSetting
import io.github.pranavm716.vgmixr.data.settings.PrefsSortSettings
import io.github.pranavm716.vgmixr.domain.settings.AccentSetting
import io.github.pranavm716.vgmixr.domain.settings.BackgroundSetting
import io.github.pranavm716.vgmixr.domain.settings.SortSettings

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    fun provideAccentSetting(setting: PrefsAccentSetting): AccentSetting = setting

    @Provides
    fun provideBackgroundSetting(setting: PrefsBackgroundSetting): BackgroundSetting = setting

    @Provides
    fun provideSortSettings(settings: PrefsSortSettings): SortSettings = settings
}
