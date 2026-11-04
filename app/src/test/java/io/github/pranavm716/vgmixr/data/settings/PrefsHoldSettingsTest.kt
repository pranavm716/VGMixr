package io.github.pranavm716.vgmixr.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrefsHoldSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `no family is excluded and no playlist chosen at first`() {
        val settings = PrefsHoldSettings(context)

        assertEquals(emptySet<String>(), settings.excludedFamilies.value)
        assertEquals(emptySet<Long>(), settings.playlists.value)
    }

    @Test
    fun `choices show at once and outlive their reader, as across launches`() {
        val settings = PrefsHoldSettings(context)

        settings.setExcludedFamilies(setOf("rse", "dp"))
        settings.setPlaylists(setOf(2, 6))

        assertEquals(setOf("rse", "dp"), settings.excludedFamilies.value)
        assertEquals(setOf(2L, 6L), PrefsHoldSettings(context).playlists.value)
        assertEquals(setOf("rse", "dp"), PrefsHoldSettings(context).excludedFamilies.value)
    }
}
