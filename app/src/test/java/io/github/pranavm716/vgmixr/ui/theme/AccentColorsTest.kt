package io.github.pranavm716.vgmixr.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.pranavm716.vgmixr.domain.settings.Accent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentColorsTest {
    @Test
    fun `the default accent is the red the app started with`() {
        assertEquals(Color(0xFFF44336), Accent.DEFAULT.color)
    }

    @Test
    fun `every accent has its own color and name`() {
        assertEquals(Accent.entries.size, Accent.entries.map { it.color }.toSet().size)
        assertEquals(Accent.entries.size, Accent.entries.map { it.label }.toSet().size)
    }

    // White icons sit on the accent (the Favorites tile, the swipe strip, the play button), so no
    // accent may be so light that they fade into it.
    @Test
    fun `white on every accent stays readable`() {
        Accent.entries.forEach {
            val contrast = contrastWithWhite(it.color)
            assertTrue("$it: $contrast", contrast >= 2f)
        }
    }

    // Accent text and icons sit on the near-black background too (the selected tab, the check).
    @Test
    fun `every accent stands out on the background`() {
        Accent.entries.forEach {
            val contrast = (it.color.luminance() + 0.05f) / (Background.luminance() + 0.05f)
            assertTrue("$it: $contrast", contrast >= 3f)
        }
    }

    private fun contrastWithWhite(color: Color) = 1.05f / (color.luminance() + 0.05f)
}
