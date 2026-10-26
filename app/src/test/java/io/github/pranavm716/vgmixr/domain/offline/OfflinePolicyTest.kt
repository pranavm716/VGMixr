package io.github.pranavm716.vgmixr.domain.offline

import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.offline.OfflineKind.CACHED
import io.github.pranavm716.vgmixr.domain.offline.OfflineKind.DOWNLOADED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePolicyTest {
    private fun track(id: String, vararg sources: Source) =
        CatalogTrack(id, "fam", emptyList(), id, 0, sources.toList())

    private fun stream(videoId: String, startMs: Long = 0, endMs: Long? = null) =
        Source.Stream(videoId, startMs, endMs)

    private val a = track("a", stream("va"))
    private val b = track("b", stream("vb"), stream("vb2"))
    private val c = track("c", stream("vc"))
    private val d = track("d", stream("vd"))

    // Two tracks sliced from one album video.
    private val s1 = track("s1", stream("album", 0, 60_000))
    private val s2 = track("s2", stream("album", 60_000, 120_000))

    private val all = listOf(a, b, c, d, s1, s2).associateBy { it.id }

    private fun inputs(
        downloadOrder: List<String> = emptyList(),
        recentlyPlayed: List<String> = emptyList(),
        next: String? = null,
        broken: Set<String> = emptySet(),
        hasLocal: (CatalogTrack) -> Boolean = { false }
    ) = OfflineInputs(all, downloadOrder, recentlyPlayed, next, broken, hasLocal)

    private fun entry(
        videoId: String,
        kind: OfflineKind,
        complete: Boolean,
        vararg trackIds: String
    ) = OfflineEntry(videoId, kind, complete, trackIds.toSet())

    @Test
    fun `playlist tracks are downloaded in their order, each once`() {
        val wanted = wantedOffline(inputs(downloadOrder = listOf("c", "a", "c")), emptyMap())

        assertEquals(
            listOf(
                OfflineWant("vc", DOWNLOADED, listOf("c")),
                OfflineWant("va", DOWNLOADED, listOf("a"))
            ),
            wanted
        )
    }

    @Test
    fun `the next track comes first and recent plays last, both cached`() {
        val wanted = wantedOffline(
            inputs(downloadOrder = listOf("a"), recentlyPlayed = listOf("d", "c"), next = "b"),
            emptyMap()
        )

        assertEquals(
            listOf(
                OfflineWant("vb", CACHED, listOf("b")),
                OfflineWant("va", DOWNLOADED, listOf("a")),
                OfflineWant("vd", CACHED, listOf("d")),
                OfflineWant("vc", CACHED, listOf("c"))
            ),
            wanted
        )
    }

    @Test
    fun `a track both in a playlist and recently played counts as downloaded`() {
        val wanted = wantedOffline(
            inputs(downloadOrder = listOf("a"), recentlyPlayed = listOf("a"), next = "a"),
            emptyMap()
        )

        assertEquals(listOf(OfflineWant("va", DOWNLOADED, listOf("a"))), wanted)
    }

    @Test
    fun `only the last 200 distinct tracks played are cached`() {
        val many = (1..250).map { track("t$it", stream("v$it")) }.associateBy { it.id }
        val played = (1..250).map { "t$it" }.flatMap { listOf(it, it) }

        val wanted = wantedOffline(
            OfflineInputs(many, emptyList(), played, null, emptySet()) { false },
            emptyMap()
        )

        assertEquals(RECENT_LIMIT, wanted.size)
        assertEquals("v1", wanted.first().videoId)
        assertEquals("v200", wanted.last().videoId)
    }

    @Test
    fun `slices of an album video share one download, after other playlist tracks`() {
        val wanted = wantedOffline(inputs(downloadOrder = listOf("s1", "a", "s2")), emptyMap())

        assertEquals(
            listOf(
                OfflineWant("va", DOWNLOADED, listOf("a")),
                OfflineWant("album", DOWNLOADED, listOf("s1", "s2"))
            ),
            wanted
        )
    }

    @Test
    fun `a broken source is passed over for the track's next one`() {
        val wanted =
            wantedOffline(inputs(downloadOrder = listOf("b"), broken = setOf("vb")), emptyMap())

        assertEquals(listOf(OfflineWant("vb2", DOWNLOADED, listOf("b"))), wanted)
    }

    @Test
    fun `a broken source that is already downloaded stays, since it still plays`() {
        val existing = mapOf("vb" to entry("vb", DOWNLOADED, true, "b"))

        val wanted =
            wantedOffline(inputs(downloadOrder = listOf("b"), broken = setOf("vb")), existing)

        assertEquals(listOf(OfflineWant("vb", DOWNLOADED, listOf("b"))), wanted)
    }

    @Test
    fun `a track with every source broken or with a local file is not downloaded`() {
        val wanted = wantedOffline(
            inputs(
                downloadOrder = listOf("a", "c"),
                broken = setOf("va"),
                hasLocal = { it.id == "c" }
            ),
            emptyMap()
        )

        assertTrue(wanted.isEmpty())
    }

    @Test
    fun `new wants start, finished ones that changed kind are updated and unwanted ones go`() {
        val existing = mapOf(
            "va" to entry("va", DOWNLOADED, true, "a"),
            "vc" to entry("vc", CACHED, true, "c"),
            "vd" to entry("vd", CACHED, true, "d")
        )
        val wanted = listOf(
            OfflineWant("va", DOWNLOADED, listOf("a")),
            OfflineWant("vc", DOWNLOADED, listOf("c")),
            OfflineWant("vb", CACHED, listOf("b"))
        )

        val plan = planOffline(wanted, existing)

        assertEquals(
            listOf(
                OfflineWant("vc", DOWNLOADED, listOf("c")),
                OfflineWant("vb", CACHED, listOf("b"))
            ),
            plan.upsert
        )
        assertEquals(listOf("vd"), plan.remove)
    }

    @Test
    fun `an unfinished download is queued again`() {
        val existing = mapOf("va" to entry("va", DOWNLOADED, false, "a"))
        val wanted = listOf(OfflineWant("va", DOWNLOADED, listOf("a")))

        assertEquals(wanted, planOffline(wanted, existing).upsert)
    }

    @Test
    fun `an old video keeps playing until the track's new one is downloaded`() {
        val wanted = listOf(OfflineWant("new", DOWNLOADED, listOf("a")))
        val old = entry("old", DOWNLOADED, true, "a")

        val pending = planOffline(
            wanted,
            mapOf(
                "old" to old,
                "new" to entry("new", DOWNLOADED, false, "a")
            )
        )
        val done = planOffline(
            wanted,
            mapOf(
                "old" to old,
                "new" to entry("new", DOWNLOADED, true, "a")
            )
        )

        assertTrue(pending.remove.isEmpty())
        assertEquals(listOf("old"), done.remove)
    }
}
