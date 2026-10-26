package io.github.pranavm716.vgmixr.domain.sort

import io.github.pranavm716.vgmixr.domain.sort.SortDirection.Ascending
import io.github.pranavm716.vgmixr.domain.sort.SortDirection.Descending
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SortTest {
    private fun track(id: String, family: Int, order: Int, title: String = id, added: Long = 0) =
        SortRow(id, title, family, order, addedAt = Instant.ofEpochSecond(added))

    private fun remix(
        id: String,
        family: Int?,
        order: Int = 0,
        title: String = id,
        channel: String = "",
        added: Long = 0
    ) = SortRow(
        id,
        title,
        family,
        order,
        isUserTrack = true,
        channel = channel,
        addedAt = Instant.ofEpochSecond(added)
    )

    private fun List<SortRow>.ids(key: SortKey, direction: SortDirection = Ascending) =
        sortedBy(SortSetting(key, direction)).map { it.id }

    @Test
    fun `order sorts by track order and descending is the plain reverse`() {
        val rows = listOf(track("b", 0, 2), track("a", 0, 1), track("c", 0, 3))
        assertEquals(listOf("a", "b", "c"), rows.ids(SortKey.Order))
        assertEquals(listOf("c", "b", "a"), rows.ids(SortKey.Order, Descending))
    }

    @Test
    fun `title ignores case and accents`() {
        val rows = listOf(
            track("1", 0, 1, "zebra"),
            track("2", 0, 2, "Élite Four"),
            track("3", 0, 3, "battle")
        )
        assertEquals(listOf("3", "2", "1"), rows.ids(SortKey.Title))
        assertEquals(listOf("1", "2", "3"), rows.ids(SortKey.Title, Descending))
    }

    @Test
    fun `title ties break by game ascending then newest, in either direction`() {
        val rows = listOf(
            remix("new", 0, order = 1, title = "Route", added = 20),
            track("late", 1, 1, "Route"),
            remix("old", 0, order = 1, title = "Route", added = 10),
            track("early", 0, 1, "Route")
        )
        val expected = listOf("early", "new", "old", "late")
        assertEquals(expected, rows.ids(SortKey.Title))
        assertEquals(expected, rows.ids(SortKey.Title, Descending))
    }

    @Test
    fun `game sorts by family then track order`() {
        val rows =
            listOf(track("b1", 1, 1), track("a2", 0, 2), track("b0", 1, 0), track("a1", 0, 1))
        assertEquals(listOf("a1", "a2", "b0", "b1"), rows.ids(SortKey.Game))
    }

    @Test
    fun `game descending reverses families but keeps each family's track order`() {
        val rows =
            listOf(track("a1", 0, 1), track("a2", 0, 2), track("b1", 1, 1), track("b2", 1, 2))
        assertEquals(listOf("b1", "b2", "a1", "a2"), rows.ids(SortKey.Game, Descending))
    }

    @Test
    fun `a linked remix follows its original, newest remix first`() {
        val rows = listOf(
            remix("old", 0, order = 1, added = 10),
            track("next", 0, 2),
            remix("new", 0, order = 1, added = 20),
            track("original", 0, 1)
        )
        assertEquals(listOf("original", "new", "old", "next"), rows.ids(SortKey.Game))
    }

    @Test
    fun `unlinked remixes come last by title in both directions`() {
        val rows = listOf(
            remix("zz", null, title = "Zz"),
            track("a", 0, 1),
            remix("aa", null, title = "Aa"),
            track("b", 1, 1)
        )
        assertEquals(listOf("a", "b", "aa", "zz"), rows.ids(SortKey.Game))
        assertEquals(listOf("b", "a", "aa", "zz"), rows.ids(SortKey.Game, Descending))
    }

    @Test
    fun `channel ties break by game, a channel's unlinked remixes after its linked ones`() {
        val rows = listOf(
            remix("u", null, title = "U", channel = "Beta"),
            remix("b1", 1, channel = "Beta"),
            remix("a", 0, channel = "alpha"),
            remix("b0", 0, channel = "Beta")
        )
        assertEquals(listOf("a", "b0", "b1", "u"), rows.ids(SortKey.Channel))
        assertEquals(listOf("b0", "b1", "u", "a"), rows.ids(SortKey.Channel, Descending))
    }

    @Test
    fun `recently added sorts by when added, ties by game then unlinked`() {
        val rows = listOf(
            remix("u", null, added = 5),
            track("old", 0, 1, added = 1),
            track("b", 1, 1, added = 5),
            track("a", 0, 2, added = 5)
        )
        assertEquals(listOf("old", "a", "b", "u"), rows.ids(SortKey.RecentlyAdded))
        assertEquals(listOf("a", "b", "u", "old"), rows.ids(SortKey.RecentlyAdded, Descending))
    }

    @Test
    fun `a playlist with an original and its remixes sorts them together by game`() {
        val rows = listOf(
            remix("r1", 0, order = 3, added = 1),
            track("other", 0, 1),
            remix("r2", 0, order = 3, added = 2),
            track("orig", 0, 3)
        )
        assertEquals(listOf("other", "orig", "r2", "r1"), rows.ids(SortKey.Game))
    }

    @Test
    fun `picking a key selects it ascending and the arrow reverses`() {
        val setting = SortSetting(SortKey.RecentlyAdded, Descending)
        assertEquals(SortSetting(SortKey.Title, Ascending), setting.withKey(SortKey.Title))
        assertEquals(SortSetting(SortKey.RecentlyAdded, Ascending), setting.reversed())
    }
}
