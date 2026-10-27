package io.github.pranavm716.vgmixr.domain.sort

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.catalog.dimmed
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionSortTest {
    private fun track(
        id: String,
        title: String,
        order: Int,
        familyId: String? = "rse",
        broken: Boolean = false,
        channel: String? = null,
        added: Long? = null
    ) = CatalogTrack(
        id = id,
        familyId = familyId,
        games = emptyList(),
        title = title,
        order = order,
        sources = emptyList(),
        unplayable = if (broken) BrokenReason.UNAVAILABLE else null,
        channel = channel,
        addedAt = added?.let(Instant::ofEpochSecond)
    )

    private val catalog = Catalog(
        games = emptyList(),
        families = listOf(Family("rse", "RSE", "RSE"), Family("dp", "DPPt", "DPPt")),
        tracks = listOf(
            track("rse-b", "Battle", 1),
            track("rse-z", "Zinnia", 2, broken = true),
            track("rse-a", "Abandoned Ship", 3),
            track("dp-x", "Other family", 1, familyId = "dp")
        ),
        userTracks = listOf(
            track("yt:new", "New", 0, familyId = null, channel = "Zed", added = 20),
            track("yt:old", "Old", 0, familyId = null, channel = "Amy", added = 10)
        )
    )
    private val byTitle = SortSetting(SortKey.Title, SortDirection.Ascending)

    @Test
    fun `a family page lists its tracks in its sort`() {
        assertEquals(
            listOf("rse-a", "rse-b", "rse-z"),
            catalog.familyTracks("rse", byTitle).map { it.id }
        )
        assertEquals(
            listOf("rse-b", "rse-z", "rse-a"),
            catalog.familyTracks("rse", SortKeys.familyDefault).map { it.id }
        )
    }

    @Test
    fun `a family plays in its sort without its greyed tracks`() {
        assertEquals(listOf("rse-a", "rse-b"), catalog.playableFamilyTrackIds("rse", byTitle))
    }

    @Test
    fun `remixes sort by when their links were pasted, or their channel`() {
        assertEquals(
            listOf("yt:new", "yt:old"),
            catalog.sortedUserTracks(SortKeys.remixesDefault).map { it.id }
        )
        assertEquals(
            listOf("yt:old", "yt:new"),
            catalog.sortedUserTracks(SortSetting(SortKey.Channel, SortDirection.Ascending))
                .map { it.id }
        )
        assertEquals(
            listOf("yt:new", "yt:old"),
            catalog.playableUserTrackIds(SortKeys.remixesDefault)
        )
    }

    @Test
    fun `a playlist lists the tracks it can find in its own sort, by when each went in`() {
        val playlist = Playlist(
            id = 7,
            name = "Mix",
            isFavorites = false,
            trackIds = listOf("rse-b", "gone", "yt:old", "rse-z"),
            addedAt = mapOf(
                "rse-b" to Instant.ofEpochSecond(3),
                "gone" to Instant.ofEpochSecond(2),
                "yt:old" to Instant.ofEpochSecond(1),
                "rse-z" to Instant.ofEpochSecond(4)
            ),
            sort = SortSetting(SortKey.RecentlyAdded, SortDirection.Ascending)
        )

        assertEquals(
            listOf("yt:old", "rse-b", "rse-z"),
            catalog.playlistTracks(playlist).map { it.id }
        )
        assertEquals(listOf("yt:old", "rse-b"), catalog.playablePlaylistTrackIds(playlist))
    }

    @Test
    fun `a playlist lists its retired tracks greyed, and doesn't play them`() {
        val retired = track("rse-old", "Old Theme", 4).copy(retired = true)
        val withRetired = catalog.copy(retiredTracks = listOf(retired))
        val playlist = Playlist(
            id = 7,
            name = "Mix",
            isFavorites = false,
            trackIds = listOf("rse-b", "rse-old"),
            sort = byTitle
        )

        assertEquals(
            listOf("rse-b", "rse-old"),
            withRetired.playlistTracks(playlist).map { it.id }
        )
        assertEquals(true, withRetired.playlistTracks(playlist).last().dimmed)
        assertEquals(listOf("rse-b"), withRetired.playablePlaylistTrackIds(playlist))
        // Everything outside playlists still doesn't know it.
        assertEquals(null, withRetired.track("rse-old"))
    }
}
