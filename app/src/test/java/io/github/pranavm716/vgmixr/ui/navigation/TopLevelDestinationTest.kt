package io.github.pranavm716.vgmixr.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun `the navbar has four tabs, the first being the start tab`() {
        assertEquals(4, TopLevelDestination.entries.size)
        assertEquals(TopLevelDestination.entries.first(), TopLevelDestination.start)
    }

    @Test
    fun `games is the start tab`() {
        assertEquals(TopLevelDestination.GAMES, TopLevelDestination.start)
    }

    @Test
    fun `only a long press on Remixes opens a page, Settings`() {
        val longPresses = TopLevelDestination.entries
            .mapNotNull { tab -> tab.longPressRoute?.let { tab to it } }
            .toMap()

        assertEquals(mapOf(TopLevelDestination.REMIXES to SettingsRoute), longPresses)
    }

    @Test
    fun `every tab has its own route`() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }
}
