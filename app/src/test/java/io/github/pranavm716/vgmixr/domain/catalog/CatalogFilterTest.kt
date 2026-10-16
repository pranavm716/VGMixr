package io.github.pranavm716.vgmixr.domain.catalog

import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.model.Category
import io.github.pranavm716.vgmixr.domain.model.Game
import io.github.pranavm716.vgmixr.domain.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogFilterTest {
    private val ruby = game("ruby")
    private val sapphire = game("sapphire")
    private val emerald = game("emerald")
    private val pla = game("pla", familyId = "pla", generation = 8)
    private val botw = game("botw", familyId = "botw", generation = null)
    private val rse = listOf(ruby, sapphire, emerald)

    private val shared = track("rse-route-101", "rse", rse, category = Category.ROUTE, order = 1)
    private val rubyOnly = track("rse-ruby-only", "rse", listOf(ruby), Category.TOWN, order = 2)
    private val sapphireOnly =
        track("rse-sapphire-only", "rse", listOf(sapphire), Category.BATTLE, order = 3)
    private val plaTrack = track("pla-village", "pla", listOf(pla), Category.TOWN, order = 0)
    private val botwTrack = track("botw-field", "botw", listOf(botw), Category.ROUTE, order = 0)

    private val catalog = Catalog(
        games = rse + pla + botw,
        families = listOf(
            Family("rse", "ruby / sapphire / emerald", "RSE"),
            Family("pla", "pla", "P"),
            Family("botw", "botw", "B")
        ),
        tracks = listOf(botwTrack, sapphireOnly, plaTrack, rubyOnly, shared)
    )

    @Test
    fun `empty filter returns every track`() {
        assertEquals(
            listOf(shared, rubyOnly, sapphireOnly, plaTrack, botwTrack),
            catalog.filter(TrackFilter())
        )
    }

    @Test
    fun `game filter matches shared tracks and that game's exclusives`() {
        assertEquals(listOf(shared, rubyOnly), catalog.filter(TrackFilter(game = "ruby")))
    }

    @Test
    fun `family filter matches the track's family`() {
        assertEquals(listOf(plaTrack), catalog.filter(TrackFilter(family = "pla")))
    }

    @Test
    fun `generation filter matches chip games with that generation`() {
        assertEquals(
            listOf(shared, rubyOnly, sapphireOnly),
            catalog.filter(TrackFilter(generation = 3))
        )
    }

    @Test
    fun `generation filter skips games without a generation`() {
        // botw has no generation, so no generation value reaches its track.
        assertEquals(listOf(plaTrack), catalog.filter(TrackFilter(generation = 8)))
        assertEquals(emptyList<CatalogTrack>(), catalog.filter(TrackFilter(generation = 0)))
    }

    @Test
    fun `category filter matches the track's category`() {
        assertEquals(
            listOf(rubyOnly, plaTrack),
            catalog.filter(TrackFilter(category = Category.TOWN))
        )
    }

    @Test
    fun `filters combine with AND`() {
        assertEquals(
            listOf(rubyOnly),
            catalog.filter(TrackFilter(game = "ruby", category = Category.TOWN))
        )
        assertEquals(
            emptyList<CatalogTrack>(),
            catalog.filter(TrackFilter(family = "pla", generation = 3))
        )
        assertEquals(
            listOf(sapphireOnly),
            catalog.filter(
                TrackFilter(
                    game = "sapphire",
                    family = "rse",
                    generation = 3,
                    category = Category.BATTLE
                )
            )
        )
    }

    @Test
    fun `sorts by family position, then order, then title`() {
        val b = track("rse-b", "rse", rse, title = "B", order = 5)
        val a = track("rse-a", "rse", rse, title = "A", order = 5)
        val first = track("rse-first", "rse", rse, title = "Z", order = 0)
        val pla0 = track("pla-0", "pla", listOf(pla), title = "A", order = 0)
        val sorting = catalog.copy(tracks = listOf(pla0, b, a, first))

        assertEquals(listOf(first, a, b, pla0), sorting.filter(TrackFilter()))
    }

    @Test
    fun `filter options list games, families, generations and categories`() {
        val options = catalog.filterOptions()

        assertEquals(catalog.games, options.games)
        assertEquals(catalog.families, options.families)
        assertEquals(listOf(3, 8), options.generations)
        assertEquals(Category.entries, options.categories)
    }

    @Test
    fun `filter generations are distinct and ascending`() {
        val unordered = catalog.copy(
            games = listOf(pla, ruby, game("gen1", generation = 1), sapphire)
        )

        assertEquals(listOf(1, 3, 8), unordered.filterOptions().generations)
    }

    @Test
    fun `filter generations are empty when no game has one`() {
        val none = Catalog(listOf(botw), listOf(Family("botw", "botw", "B")), listOf(botwTrack))

        assertEquals(emptyList<Int>(), none.filterOptions().generations)
    }

    private fun track(
        id: String,
        familyId: String,
        games: List<Game>,
        category: Category = Category.ROUTE,
        order: Int = 0,
        title: String = id
    ) = CatalogTrack(
        id = id,
        familyId = familyId,
        games = games,
        title = title,
        category = category,
        order = order,
        sources = listOf(Source.Stream("AAAAAAAAAAA", 0, null))
    )
}
