package io.github.pranavm716.vgmixr.data.settings

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.domain.sort.SortDirection
import io.github.pranavm716.vgmixr.domain.sort.SortKey
import io.github.pranavm716.vgmixr.domain.sort.SortKeys
import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrefsSortSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `both start as today's order`() {
        val settings = PrefsSortSettings(context)
        assertEquals(SortKeys.familyDefault, settings.games.value)
        assertEquals(SortKeys.remixesDefault, settings.remixes.value)
    }

    @Test
    fun `each outlives its reader, apart from the other`() {
        val title = SortSetting(SortKey.Title, SortDirection.Descending)
        val channel = SortSetting(SortKey.Channel, SortDirection.Ascending)
        PrefsSortSettings(context).setGames(title)
        PrefsSortSettings(context).setRemixes(channel)

        val settings = PrefsSortSettings(context)
        assertEquals(title, settings.games.value)
        assertEquals(channel, settings.remixes.value)
    }

    @Test
    fun `a stored key the collection doesn't offer falls back to the default`() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
            putString("gamesSortKey", SortKey.Channel.name)
            putString("gamesSortDirection", SortDirection.Descending.name)
        }

        assertEquals(SortKeys.familyDefault, PrefsSortSettings(context).games.value)
    }
}
