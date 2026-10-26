package io.github.pranavm716.vgmixr.domain.offline

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineCatalogTest {
    private fun track(id: String, vararg videoIds: String, unplayable: BrokenReason? = null) =
        CatalogTrack(
            id,
            "fam",
            emptyList(),
            id,
            0,
            videoIds.map { Source.Stream(it, 0, null) },
            unplayable = unplayable
        )

    private fun Catalog.offlineFlags() = (tracks + userTracks).associate { it.id to it.offline }

    @Test
    fun `offline, a track plays when the video it would play is on the phone`() {
        val catalog = Catalog(
            games = emptyList(),
            families = emptyList(),
            tracks = listOf(
                track("kept", "v-kept"),
                track("streamed", "v-streamed"),
                track("gone", "v-gone", unplayable = BrokenReason.NO_AUDIO)
            ),
            userTracks = listOf(track("yt:a", "v-yt-a"), track("yt:b", "v-yt-b"))
        )

        val offline = catalog.offline(onPhone = setOf("v-kept", "v-yt-a"), broken = setOf("v-gone"))

        assertEquals(
            mapOf(
                "kept" to false,
                "streamed" to true,
                "gone" to false,
                "yt:a" to false,
                "yt:b" to true
            ),
            offline.offlineFlags()
        )
        assertEquals(BrokenReason.NO_AUDIO, offline.track("gone")?.unplayable)
    }

    @Test
    fun `every slice of a downloaded album plays, kept in a playlist or not`() {
        val catalog = Catalog(
            games = emptyList(),
            families = emptyList(),
            tracks = listOf(track("sv-a", "album"), track("sv-b", "album"), track("sv-c", "album"))
        )

        val offline = catalog.offline(onPhone = setOf("album"), broken = emptySet())

        assertEquals(
            mapOf("sv-a" to false, "sv-b" to false, "sv-c" to false),
            offline.offlineFlags()
        )
    }

    @Test
    fun `a track plays from the source playback would pick, past a broken one`() {
        val catalog = Catalog(
            games = emptyList(),
            families = emptyList(),
            tracks = listOf(
                // Its first video is broken, so it plays the second, which is on the phone.
                track("fallback", "v-broken", "v-second"),
                // Its first video plays online, so offline it would try that one, not the album.
                track("first-wins", "v-single", "album")
            )
        )

        val offline = catalog.offline(
            onPhone = setOf("v-second", "album"),
            broken = setOf("v-broken")
        )

        assertEquals(mapOf("fallback" to false, "first-wins" to true), offline.offlineFlags())
    }
}
