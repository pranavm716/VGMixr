package io.github.pranavm716.vgmixr.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.pranavm716.vgmixr.data.settings.PrefsAccentSetting
import io.github.pranavm716.vgmixr.domain.settings.AccentSetting

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    fun provideAccentSetting(setting: PrefsAccentSetting): AccentSetting = setting
}
