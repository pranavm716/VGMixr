package io.github.pranavm716.vgmixr.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SnackbarLiftTest {
    @Test
    fun `an open keyboard lifts messages by how far it reaches above the bottom bar`() {
        assertEquals(700, snackbarLift(imeBottom = 900, bottomBarHeight = 200))
    }

    @Test
    fun `with the keyboard closed or lower than the bottom bar, messages stay put`() {
        assertEquals(0, snackbarLift(imeBottom = 0, bottomBarHeight = 200))
        assertEquals(0, snackbarLift(imeBottom = 150, bottomBarHeight = 200))
    }
}
