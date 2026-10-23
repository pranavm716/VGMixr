package io.github.pranavm716.vgmixr.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrefsShuffleSettingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `shuffle starts off`() {
        assertEquals(false, PrefsShuffleSetting(context).isOn())
    }

    @Test
    fun `the setting outlives its reader, as across launches`() {
        PrefsShuffleSetting(context).set(true)

        assertEquals(true, PrefsShuffleSetting(context).isOn())

        PrefsShuffleSetting(context).set(false)

        assertEquals(false, PrefsShuffleSetting(context).isOn())
    }
}
