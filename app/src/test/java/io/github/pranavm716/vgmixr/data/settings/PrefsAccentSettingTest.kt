package io.github.pranavm716.vgmixr.data.settings

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.domain.settings.Accent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrefsAccentSettingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the accent starts as the default`() {
        assertEquals(Accent.DEFAULT, PrefsAccentSetting(context).accent.value)
    }

    @Test
    fun `a new accent shows at once`() {
        val setting = PrefsAccentSetting(context)

        setting.set(Accent.TEAL)

        assertEquals(Accent.TEAL, setting.accent.value)
    }

    @Test
    fun `the setting outlives its reader, as across launches`() {
        PrefsAccentSetting(context).set(Accent.PURPLE)

        assertEquals(Accent.PURPLE, PrefsAccentSetting(context).accent.value)
    }

    @Test
    fun `a stored accent the app no longer offers falls back to the default`() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit { putString("accent", "CHARTREUSE") }

        assertEquals(Accent.DEFAULT, PrefsAccentSetting(context).accent.value)
    }
}
