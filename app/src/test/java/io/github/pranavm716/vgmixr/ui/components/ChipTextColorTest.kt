package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ChipTextColorTest {
    @Test
    fun `light colors get black text`() {
        assertEquals(Color.Black, chipTextColor("#FFFFFF"))
        assertEquals(Color.Black, chipTextColor("#FFD733")) // Yellow
        assertEquals(Color.Black, chipTextColor("#DD7CB1")) // Pearl
        assertEquals(Color.Black, chipTextColor("#009652")) // Emerald
    }

    @Test
    fun `dark colors get white text`() {
        assertEquals(Color.White, chipTextColor("#000000"))
        assertEquals(Color.White, chipTextColor("#444444")) // Black
        assertEquals(Color.White, chipTextColor("#3D51A7")) // Sapphire
    }

    @Test
    fun `hex case does not matter`() {
        assertEquals(chipTextColor("#FFD733"), chipTextColor("#ffd733"))
    }

    @Test
    fun `chip background is the game's color`() {
        assertEquals(Color(0xFF3D51A7), chipColor("#3D51A7"))
        assertEquals(Color(0xFFFFD733), chipColor("#ffd733"))
    }
}
