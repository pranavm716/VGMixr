package io.github.pranavm716.vgmixr.domain.search

import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.history.Play
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrecencyWeightTest {
    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val rseRoute = track("rse-route-101", "rse")
    private val orasRoute = track("oras-route-101", "oras")

    private fun track(id: String, familyId: String) =
        CatalogTrack(id, familyId, emptyList(), id, 1, emptyList())

    private fun daysAgo(days: Long) = now - Duration.ofDays(days)

    private fun weightOf(
        track: CatalogTrack,
        plays: List<Play> = emptyList(),
        inPlaylists: Set<String> = emptySet(),
        liftedFamilies: Set<String> = emptySet()
    ) = frecencyWeight(RankingSignals(plays, inPlaylists, liftedFamilies), now).weight(track)

    @Test
    fun `a track with no plays, playlists or family weighs nothing`() {
        assertEquals(0.0, weightOf(rseRoute), 0.0)
    }

    @Test
    fun `a play counts less as it ages, halving every two weeks`() {
        assertEquals(1.0, weightOf(rseRoute, listOf(Play(rseRoute.id, now))), 1e-9)
        assertEquals(0.5, weightOf(rseRoute, listOf(Play(rseRoute.id, daysAgo(14)))), 1e-9)
        assertEquals(0.25, weightOf(rseRoute, listOf(Play(rseRoute.id, daysAgo(28)))), 1e-9)
    }

    @Test
    fun `plays add up`() {
        val plays = listOf(Play(rseRoute.id, now), Play(rseRoute.id, daysAgo(14)))

        assertEquals(1.5, weightOf(rseRoute, plays), 1e-9)
    }

    @Test
    fun `only the track's own plays count`() {
        assertEquals(0.0, weightOf(rseRoute, listOf(Play(orasRoute.id, now))), 0.0)
    }

    @Test
    fun `a play stamped after now counts as a fresh one`() {
        val plays = listOf(Play(rseRoute.id, now + Duration.ofDays(3)))

        assertEquals(1.0, weightOf(rseRoute, plays), 1e-9)
    }

    @Test
    fun `a track in a playlist or favorites gets a fixed bonus`() {
        assertEquals(3.0, weightOf(rseRoute, inPlaylists = setOf(rseRoute.id)), 0.0)
        assertEquals(0.0, weightOf(orasRoute, inPlaylists = setOf(rseRoute.id)), 0.0)
    }

    @Test
    fun `a track of a lifted family gets a small lift`() {
        val lifted = setOf("rse", "hgss")

        assertEquals(0.5, weightOf(rseRoute, liftedFamilies = lifted), 0.0)
        assertEquals(0.0, weightOf(orasRoute, liftedFamilies = lifted), 0.0)
    }

    @Test
    fun `plays, playlists and the family lift add up`() {
        val weight = weightOf(
            rseRoute,
            plays = listOf(Play(rseRoute.id, now)),
            inPlaylists = setOf(rseRoute.id),
            liftedFamilies = setOf("rse")
        )

        assertEquals(4.5, weight, 1e-9)
    }

    @Test
    fun `a playlist track beats one played twice today`() {
        val inPlaylist = weightOf(orasRoute, inPlaylists = setOf(orasRoute.id))
        val playedTwice = weightOf(rseRoute, List(2) { Play(rseRoute.id, now) })

        assertTrue(inPlaylist > playedTwice)
    }

    @Test
    fun `the family lift beats a play three weeks old but not one a week old`() {
        val lifted = weightOf(orasRoute, liftedFamilies = setOf("oras"))

        assertTrue(lifted > weightOf(rseRoute, listOf(Play(rseRoute.id, daysAgo(21)))))
        assertTrue(lifted < weightOf(rseRoute, listOf(Play(rseRoute.id, daysAgo(7)))))
    }
}
