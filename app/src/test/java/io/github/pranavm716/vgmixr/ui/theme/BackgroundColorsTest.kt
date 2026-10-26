package io.github.pranavm716.vgmixr.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.pranavm716.vgmixr.domain.settings.Accent
import io.github.pranavm716.vgmixr.domain.settings.Background as BackgroundChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundColorsTest {
    @Test
    fun `the default background is the near-black the app started with`() {
        assertEquals(BackgroundChoice.BLACK, BackgroundChoice.DEFAULT)
        assertEquals(Color(0xFF111111), BackgroundChoice.DEFAULT.color)
        assertEquals(Background, BackgroundChoice.DEFAULT.color)
    }

    @Test
    fun `every background has its own color and name`() {
        val all = BackgroundChoice.entries
        assertEquals(all.size, all.map { it.color }.toSet().size)
        assertEquals(all.size, all.map { it.label }.toSet().size)
    }

    // The accent draws the selected tab, progress and the download circle on the background.
    @Test
    fun `every accent stands out on every background`() {
        BackgroundChoice.entries.forEach { background ->
            Accent.entries.forEach { accent ->
                val contrast = contrast(accent.color, background.color)
                assertTrue("$accent on $background: $contrast", contrast >= 2f)
            }
        }
    }

    // Light text and the white icons stay readable: every background is dark.
    @Test
    fun `every background keeps light text readable`() {
        BackgroundChoice.entries.forEach {
            val contrast = contrast(TextPrimary, it.color)
            assertTrue("$it: $contrast", contrast >= 7f)
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }
}
