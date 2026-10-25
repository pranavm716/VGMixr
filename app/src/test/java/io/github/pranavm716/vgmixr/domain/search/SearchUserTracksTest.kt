package io.github.pranavm716.vgmixr.domain.search

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.model.Game
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrack
import io.github.pranavm716.vgmixr.domain.usertrack.toCatalogTrack
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tracks added from YouTube in search: linked to their original, or not. */
class SearchUserTracksTest {
    private val gold = game("gold", "gsc", "Gold", "G", 2)
    private val silver = game("silver", "gsc", "Silver", "S", 2)
    private val crystal = game("crystal", "gsc", "Crystal", "C", 2)
    private val ruby = game("ruby", "rse", "Ruby", "R", 3)
    private val sapphire = game("sapphire", "rse", "Sapphire", "S", 3)
    private val gsc = listOf(gold, silver, crystal)
    private val rse = listOf(ruby, sapphire)

    private val catalogTracks = listOf(
        track("gsc-route-29", gsc, "Route 29", 10),
        track("gsc-olivine-lighthouse", gsc, "Olivine Lighthouse", 40),
        track("rse-lofi-lounge", rse, "Lofi Lounge", 20),
        track("rse-mt-pyre-summit", rse, "Mt. Pyre Summit", 50)
    )
    private val catalogOnly = Catalog(
        games = gsc + rse,
        families = listOf(
            Family("gsc", "Gold / Silver / Crystal", "GSC"),
            Family("rse", "Ruby / Sapphire", "RS")
        ),
        tracks = catalogTracks
    )

    private fun remix(videoId: String, title: String, channel: String, remixOf: String?) =
        UserTrack(
            videoId = videoId,
            title = title,
            channel = channel,
            durationMs = null,
            addedAt = Instant.EPOCH,
            remixOf = remixOf
        ).toCatalogTrack(catalogOnly, unplayable = null)

    // Newest first, as the catalog lists them.
    private val catalog = catalogOnly.copy(
        userTracks = listOf(
            remix(
                "AAAAAAAAAAA",
                "Pokémon Gold and Silver - Olivine Lighthouse [Lofi Remix]",
                "GlitchxCity",
                remixOf = "gsc-olivine-lighthouse"
            ),
            remix(
                "BBBBBBBBBBB",
                "Pokémon Ruby and Sapphire - Mt. Pyre Summit (Remix)",
                "GlitchxCity",
                remixOf = null
            ),
            remix("CCCCCCCCCCC", "Route 29 Orchestrated", "SquashiOrchestral", "gsc-route-29")
        )
    )
    private val index = SearchIndex(catalog)

    private fun ids(query: String) = search(query, index).map { it.id }

    @Test
    fun `a linked remix is found by its original's title, right after the original`() {
        assertEquals(listOf("gsc-olivine-lighthouse", "yt:AAAAAAAAAAA"), ids("olivine lighthouse"))
    }

    @Test
    fun `a linked remix is found by its YouTube title, below a title match`() {
        assertEquals(listOf("rse-lofi-lounge", "yt:AAAAAAAAAAA"), ids("lofi"))
    }

    @Test
    fun `a channel finds its tracks, whole or split at its capitals`() {
        assertEquals(listOf("yt:AAAAAAAAAAA", "yt:BBBBBBBBBBB"), ids("glitchxcity"))
        assertEquals(listOf("yt:AAAAAAAAAAA", "yt:BBBBBBBBBBB"), ids("glitch city"))
        assertEquals(listOf("yt:CCCCCCCCCCC"), ids("squashi"))
    }

    @Test
    fun `remix finds only remixes, also ones whose title doesn't say so`() {
        assertEquals(listOf("yt:AAAAAAAAAAA"), ids("olivine remix"))
        assertEquals(listOf("yt:CCCCCCCCCCC"), ids("route 29 remix"))
    }

    @Test
    fun `a linked remix is found by its original's family and games`() {
        assertEquals(listOf("gsc-olivine-lighthouse", "yt:AAAAAAAAAAA"), ids("gsc olivine"))
        assertEquals(listOf("gsc-route-29", "yt:CCCCCCCCCCC"), ids("gen 2 route"))
    }

    @Test
    fun `an unlinked remix is found by its YouTube title`() {
        assertEquals(listOf("rse-mt-pyre-summit", "yt:BBBBBBBBBBB"), ids("mt pyre summit"))
        assertEquals(listOf("yt:BBBBBBBBBBB"), ids("pyre remix"))
    }

    private fun game(id: String, family: String, name: String, short: String, gen: Int) =
        Game(id, name, short, "#336699", gen, "GB", family)

    private fun track(id: String, games: List<Game>, title: String, order: Int) = CatalogTrack(
        id = id,
        familyId = games.first().familyId,
        games = games,
        title = title,
        order = order,
        sources = listOf(Source.Stream("ZZZZZZZZZZZ", 0, null))
    )
}
