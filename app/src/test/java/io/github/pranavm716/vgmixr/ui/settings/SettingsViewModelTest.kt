package io.github.pranavm716.vgmixr.ui.settings

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.domain.settings.Accent
import io.github.pranavm716.vgmixr.testing.FakeAccentSetting
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `offers every accent, in order, with the saved one picked`() = runTest {
        val viewModel = SettingsViewModel(FakeAccentSetting(Accent.BLUE))

        viewModel.uiState.test {
            assertEquals(SettingsUiState(Accent.entries, Accent.BLUE), awaitItem())
        }
    }

    @Test
    fun `picking an accent saves it and shows it picked`() = runTest {
        val setting = FakeAccentSetting()
        val viewModel = SettingsViewModel(setting)

        viewModel.uiState.test {
            assertEquals(Accent.DEFAULT, awaitItem().accent)
            viewModel.onAccentClick(Accent.GREEN)

            assertEquals(Accent.GREEN, awaitItem().accent)
            assertEquals(Accent.GREEN, setting.accent.value)
        }
    }
}
