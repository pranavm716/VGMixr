package io.github.pranavm716.vgmixr.domain.search

import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.model.Game
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** A small catalog shaped like the real one: same-named tracks across families, real titles. */
class SearchTest {
    private val ruby = game("ruby", "rse", "Ruby", "R", 3)
    private val sapphire = game("sapphire", "rse", "Sapphire", "S", 3)
    private val emerald = game("emerald", "rse", "Emerald", "E", 3)
    private val diamond = game("diamond", "dppt", "Diamond", "D", 4)
    private val pearl = game("pearl", "dppt", "Pearl", "P", 4)
    private val platinum = game("platinum", "dppt", "Platinum", "Pt", 4)
    private val heartGold = game("heartgold", "hgss", "HeartGold", "HG", 4)
    private val soulSilver = game("soulsilver", "hgss", "SoulSilver", "SS", 4)
    private val omegaRuby = game("omega-ruby", "oras", "Omega Ruby", "OR", 6)
    private val alphaSapphire = game("alpha-sapphire", "oras", "Alpha Sapphire", "AS", 6)
    private val sun = game("sun", "sm", "Sun", "S", 7)
    private val moon = game("moon", "sm", "Moon", "M", 7)
    private val brilliantDiamond = game("brilliant-diamond", "bdsp", "Brilliant Diamond", "BD", 8)
    private val shiningPearl = game("shining-pearl", "bdsp", "Shining Pearl", "SP", 8)
    private val arceus = game("pla", "pla", "Legends: Arceus", "PLA", 8)

    private val rse = listOf(ruby, sapphire, emerald)
    private val dppt = listOf(diamond, pearl, platinum)
    private val hgss = listOf(heartGold, soulSilver)
    private val oras = listOf(omegaRuby, alphaSapphire)
    private val sm = listOf(sun, moon)
    private val bdsp = listOf(brilliantDiamond, shiningPearl)
    private val games = rse + dppt + hgss + oras + sm + bdsp + arceus

    private val tracks = listOf(
        track("rse-littleroot-town", rse, "Littleroot Town", 1),
        track("rse-route-101", rse, "Route 101", 2),
        track("rse-route-110", rse, "Route 110", 3),
        track("rse-pokemon-center", rse, "Pokémon Center", 4),
        track("rse-battle-elite-four", rse, "Battle! (Elite Four)", 5),
        track("rse-opening-movie-emerald", listOf(emerald), "Opening Movie (Emerald)", 6),
        track("rse-opening-movie-rs", listOf(ruby, sapphire), "Opening Movie (Ruby & Sapphire)", 7),
        track("dppt-route-201", dppt, "Route 201", 1),
        track("dppt-pokemon-center", dppt, "Pokémon Center", 2),
        track("dppt-pokemon-center-night", dppt, "Pokémon Center (Night)", 3),
        track("dppt-battle-elite-four", dppt, "Battle! (Elite Four)", 4),
        track("hgss-route-29", hgss, "Route 29", 1),
        track("hgss-pokegear-radio-route-101", hgss, "Pokégear Radio: Route 101", 2),
        track("hgss-pokemon-center", hgss, "Pokémon Center", 3),
        track("oras-route-101", oras, "Route 101", 1),
        track("oras-pokemon-center", oras, "Pokémon Center", 2),
        track("oras-the-elite-four-appear", oras, "The Elite Four Appear!", 3),
        track("oras-battle-elite-four", oras, "Battle! (Elite Four)", 4),
        track(
            "oras-battle-wild-pokemon",
            oras,
            "Battle! (Wild Pokémon)",
            5,
            unplayable = BrokenReason.NO_AUDIO
        ),
        track("sm-necrozma-vs", sm, "Necrozma vs Solgaleo / Lunala", 1),
        track("sm-battle-trainer", sm, "Battle! (Trainer)", 2),
        track("bdsp-route-201", bdsp, "Route 201", 1),
        track("pla-jubilife-village", listOf(arceus), "Jubilife Village", 1)
    )

    // Family order is the order of each family's first game; tracks are stored shuffled.
    private val catalog = Catalog(
        games = games,
        families = listOf(
            Family("rse", "Ruby / Sapphire / Emerald", "RSE"),
            Family("dppt", "Diamond / Pearl / Platinum", "DPPt"),
            Family("hgss", "HeartGold / SoulSilver", "HGSS"),
            Family("oras", "Omega Ruby / Alpha Sapphire", "ORAS"),
            Family("sm", "Sun / Moon", "SM"),
            Family("bdsp", "Brilliant Diamond / Shining Pearl", "BDSP"),
            Family("pla", "Legends: Arceus", "PLA")
        ),
        tracks = tracks.reversed()
    )
    private val index = SearchIndex(catalog)
    private val now = Instant.parse("2026-10-22T12:00:00Z")

    private fun ids(query: String) = search(query, index, emptyList(), now).map { it.id }

    @Test
    fun `a blank query finds nothing`() {
        assertEquals(emptyList<String>(), ids(""))
        assertEquals(emptyList<String>(), ids("   "))
    }

    @Test
    fun `pokemon centre finds every Pokemon Center in family order, the exact title first`() {
        assertEquals(
            listOf(
                "rse-pokemon-center",
                "dppt-pokemon-center",
                "hgss-pokemon-center",
                "oras-pokemon-center",
                "dppt-pokemon-center-night"
            ),
            ids("pokemon centre")
        )
    }

    @Test
    fun `a family label narrows same-named tracks to that family`() {
        assertEquals(listOf("hgss-pokemon-center"), ids("pokemon center hgss"))
    }

    @Test
    fun `eliet four finds the Elite Four tracks`() {
        assertEquals(
            listOf(
                "rse-battle-elite-four",
                "dppt-battle-elite-four",
                "oras-battle-elite-four",
                "oras-the-elite-four-appear"
            ),
            ids("eliet four")
        )
    }

    @Test
    fun `oras e4 finds only ORAS's Elite Four tracks`() {
        assertEquals(
            listOf("oras-battle-elite-four", "oras-the-elite-four-appear"),
            ids("oras e4")
        )
    }

    @Test
    fun `rt 101 finds Route 101 in every family, then longer titles, never Route 110`() {
        assertEquals(
            listOf("rse-route-101", "oras-route-101", "hgss-pokegear-radio-route-101"),
            ids("rt 101")
        )
    }

    @Test
    fun `gen 4 route finds only routes in generation 4 games`() {
        val expected = listOf("dppt-route-201", "hgss-route-29", "hgss-pokegear-radio-route-101")
        assertEquals(expected, ids("gen 4 route"))
        assertEquals(expected, ids("gen4 route"))
    }

    @Test
    fun `a title match outranks a game match`() {
        assertEquals(
            listOf(
                "rse-opening-movie-emerald",
                "rse-littleroot-town",
                "rse-route-101",
                "rse-route-110",
                "rse-pokemon-center",
                "rse-battle-elite-four"
            ),
            ids("emerald")
        )
    }

    @Test
    fun `vs finds battles through its alias and a title that says vs`() {
        assertEquals(
            setOf(
                "rse-battle-elite-four",
                "dppt-battle-elite-four",
                "oras-battle-elite-four",
                "oras-battle-wild-pokemon",
                "sm-battle-trainer",
                "sm-necrozma-vs"
            ),
            ids("vs").toSet()
        )
    }

    @Test
    fun `the start of a word finds the track while typing`() {
        assertEquals(listOf("rse-littleroot-town"), ids("litt"))
    }

    @Test
    fun `accents in the query don't matter`() {
        assertEquals(ids("pokemon center"), ids("Pokémon Center"))
    }

    @Test
    fun `every query word must match`() {
        assertEquals(emptyList<String>(), ids("route zzzz"))
    }

    @Test
    fun `greyed tracks are found too`() {
        assertEquals(listOf("oras-battle-wild-pokemon"), ids("wild"))
    }

    @Test
    fun `a region finds its families' tracks`() {
        assertEquals(
            tracks.filter { it.familyId == "rse" || it.familyId == "oras" }.map { it.id }.toSet(),
            ids("hoenn").toSet()
        )
        assertEquals(setOf("dppt", "bdsp", "pla"), familiesOf(ids("sinnoh")))
        assertEquals(listOf("pla-jubilife-village"), ids("hisui"))
    }

    @Test
    fun `a region combines with title words`() {
        assertEquals(listOf("hgss-route-29", "hgss-pokegear-radio-route-101"), ids("johto route"))
    }

    private fun familiesOf(ids: List<String>) =
        ids.map { id -> tracks.first { it.id == id }.familyId }.toSet()

    private fun track(
        id: String,
        games: List<Game>,
        title: String,
        order: Int,
        unplayable: BrokenReason? = null
    ) = CatalogTrack(
        id = id,
        familyId = games.first().familyId,
        games = games,
        title = title,
        order = order,
        sources = listOf(Source.Stream("AAAAAAAAAAA", 0, null)),
        unplayable = unplayable
    )
}
