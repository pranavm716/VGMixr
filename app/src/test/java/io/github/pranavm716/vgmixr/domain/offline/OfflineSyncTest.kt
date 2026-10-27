package io.github.pranavm716.vgmixr.domain.offline

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
import io.github.pranavm716.vgmixr.domain.sort.SortDirection
import io.github.pranavm716.vgmixr.domain.sort.SortKey
import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineSyncTest {
    private fun track(id: String, title: String = id, addedAt: Instant? = null) = CatalogTrack(
        id,
        "fam",
        emptyList(),
        title,
        0,
        listOf(Source.Stream("v-$id", 0, null)),
        addedAt = addedAt
    )

    private val byTitle = SortSetting(SortKey.Title, SortDirection.Ascending)

    private val catalog = Catalog(
        games = emptyList(),
        families = emptyList(),
        tracks = listOf(track("a", "Zeta"), track("b", "Alpha"), track("c"), track("d")),
        userTracks = listOf(
            track("yt:new", addedAt = Instant.ofEpochSecond(2)),
            track("yt:old", addedAt = Instant.ofEpochSecond(1))
        )
    )

    private fun playlist(id: Long, favorites: Boolean, vararg trackIds: String) =
        Playlist(id, "p$id", favorites, trackIds.toList(), sort = byTitle)

    @Test
    fun `the fill order is Favorites, then Remixes, then the other playlists, each in its sort`() {
        val order = downloadOrder(
            catalog,
            playlists = listOf(
                playlist(2, false, "d", "c"),
                playlist(1, true, "a", "b")
            ),
            remixSort = SortSetting(SortKey.RecentlyAdded, SortDirection.Ascending)
        )

        assertEquals(listOf("b", "a", "yt:old", "yt:new", "c", "d"), order)
    }

    @Test
    fun `retired tracks in a playlist aren't downloaded`() {
        val withRetired = catalog.copy(retiredTracks = listOf(track("old").copy(retired = true)))

        val order = downloadOrder(
            withRetired,
            playlists = listOf(playlist(1, true, "old", "a")),
            remixSort = byTitle
        )

        assertEquals(listOf("a", "yt:new", "yt:old"), order)
    }

    private class FakeStore : OfflineStore {
        val state = MutableStateFlow<Map<String, OfflineEntry>>(emptyMap())
        override val entries: Flow<Map<String, OfflineEntry>> = state
        override val playableOffline: Flow<Set<String>> =
            state.map { m -> m.values.filter { it.complete }.flatMap { it.trackIds }.toSet() }
        override val usage: Flow<OfflineUsage> = state.map { OfflineUsage(0, 0) }
        override val onPhone: Flow<Set<String>> =
            state.map { m -> m.values.filter { it.complete }.map { it.videoId }.toSet() }

        override suspend fun apply(plan: OfflinePlan) {
            val next = state.value - plan.remove.toSet()
            state.value = next + plan.upsert.associate {
                val done = next[it.videoId]?.complete == true
                it.videoId to OfflineEntry(it.videoId, it.kind, done, it.trackIds.toSet())
            }
        }

        override suspend fun setIncomplete(videoId: String) {
            state.value =
                state.value.mapValues { (id, e) ->
                    if (id ==
                        videoId
                    ) {
                        e.copy(complete = false)
                    } else {
                        e
                    }
                }
        }

        override suspend fun setComplete(videoId: String, bytes: Long) {
            state.value =
                state.value.mapValues { (id, e) ->
                    if (id ==
                        videoId
                    ) {
                        e.copy(complete = true)
                    } else {
                        e
                    }
                }
        }
    }

    private class FakeDownloader : OfflineDownloader {
        val downloads = mutableListOf<List<String>>()
        val removals = mutableListOf<List<String>>()
        override fun download(videoIds: List<String>) {
            downloads += videoIds
        }

        override fun remove(videoIds: List<String>) {
            removals += videoIds
        }
    }

    @Test
    fun `playlist tracks are stored and downloaded in fill order, and leave when removed`() =
        runTest {
            val store = FakeStore()
            val downloader = FakeDownloader()
            val playlists = MutableStateFlow(listOf(playlist(1, true, "a", "b")))
            val sync = OfflineSync(
                catalog = MutableStateFlow(catalog),
                playlists = playlists,
                remixSort = MutableStateFlow(byTitle),
                brokenVideos = MutableStateFlow(emptySet()),
                store = store,
                downloader = downloader
            )
            backgroundScope.launch { sync.run() }
            runCurrent()

            assertEquals(
                listOf("v-b", "v-a", "v-yt:new", "v-yt:old"),
                downloader.downloads.last()
            )
            assertEquals(setOf("v-a", "v-b", "v-yt:new", "v-yt:old"), store.state.value.keys)

            store.setComplete("v-a", 1)
            playlists.value = listOf(playlist(1, true, "b"))
            runCurrent()

            assertEquals(listOf("v-a"), downloader.removals.last())
            assertEquals(setOf("v-b", "v-yt:new", "v-yt:old"), store.state.value.keys)
        }

    @Test
    fun `the next track and recent plays are cached around the playlist downloads`() = runTest {
        val store = FakeStore()
        val downloader = FakeDownloader()
        val sync = OfflineSync(
            catalog = MutableStateFlow(catalog),
            playlists = MutableStateFlow(listOf(playlist(1, true, "a"))),
            remixSort = MutableStateFlow(byTitle),
            brokenVideos = MutableStateFlow(emptySet()),
            store = store,
            downloader = downloader,
            recentlyPlayed = MutableStateFlow(listOf("d", "a")),
            next = MutableStateFlow("c")
        )
        backgroundScope.launch { sync.run() }
        runCurrent()

        assertEquals(
            listOf("v-c", "v-a", "v-yt:new", "v-yt:old", "v-d"),
            downloader.downloads.last()
        )
        assertEquals(OfflineKind.CACHED, store.state.value.getValue("v-c").kind)
        assertEquals(OfflineKind.CACHED, store.state.value.getValue("v-d").kind)
        assertEquals(OfflineKind.DOWNLOADED, store.state.value.getValue("v-a").kind)
    }
}
