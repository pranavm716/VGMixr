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
    fun `a long press on Search opens Settings, and every other tab plays`() {
        assertEquals(
            mapOf(
                TopLevelDestination.GAMES to LongPress.Play,
                TopLevelDestination.SEARCH to LongPress.Open(SettingsRoute),
                TopLevelDestination.PLAYLISTS to LongPress.Play,
                TopLevelDestination.REMIXES to LongPress.Play
            ),
            TopLevelDestination.entries.associateWith { it.longPress }
        )
    }

    @Test
    fun `every tab has its own route`() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }
}
