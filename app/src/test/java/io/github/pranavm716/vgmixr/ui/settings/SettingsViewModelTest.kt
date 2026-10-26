package io.github.pranavm716.vgmixr.ui.settings

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.domain.offline.OfflineUsage
import io.github.pranavm716.vgmixr.domain.settings.Accent
import io.github.pranavm716.vgmixr.testing.FakeAccentSetting
import io.github.pranavm716.vgmixr.testing.FakeOfflineStore
import io.github.pranavm716.vgmixr.testing.FakeRecentCutoff
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import java.time.Instant
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

    @Test
    fun `storage shows the space taken, and clearing recent plays moves the cutoff`() = runTest {
        val cutoff = FakeRecentCutoff()
        val viewModel = SettingsViewModel(
            FakeAccentSetting(),
            FakeOfflineStore(usage = OfflineUsage(1_200_000_000, 200_000_000)),
            cutoff
        )

        viewModel.storage.test {
            assertEquals(OfflineUsage(1_200_000_000, 200_000_000), awaitItem())
        }
        viewModel.onClearRecent()
        assertEquals(Instant.parse("2026-10-26T12:00:00Z"), cutoff.since.value)
    }
}
