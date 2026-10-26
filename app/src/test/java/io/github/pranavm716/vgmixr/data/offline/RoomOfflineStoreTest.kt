package io.github.pranavm716.vgmixr.data.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.data.db.inMemoryDatabase
import io.github.pranavm716.vgmixr.domain.offline.OfflineEntry
import io.github.pranavm716.vgmixr.domain.offline.OfflineKind.CACHED
import io.github.pranavm716.vgmixr.domain.offline.OfflineKind.DOWNLOADED
import io.github.pranavm716.vgmixr.domain.offline.OfflinePlan
import io.github.pranavm716.vgmixr.domain.offline.OfflineUsage
import io.github.pranavm716.vgmixr.domain.offline.OfflineWant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomOfflineStoreTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = inMemoryDatabase(dispatcher)
    private val store = RoomOfflineStore(db.offlineDao())

    @After
    fun closeDb() = db.close()

    @Test
    fun `a planned video is stored unfinished with its tracks`() = runTest(dispatcher) {
        store.apply(
            OfflinePlan(listOf(OfflineWant("va", DOWNLOADED, listOf("a", "b"))), emptyList())
        )

        assertEquals(
            mapOf("va" to OfflineEntry("va", DOWNLOADED, false, setOf("a", "b"))),
            store.entries.first()
        )
    }

    @Test
    fun `a finished video plays offline and counts toward its kind's space`() =
        runTest(dispatcher) {
            store.apply(
                OfflinePlan(
                    listOf(
                        OfflineWant("va", DOWNLOADED, listOf("a")),
                        OfflineWant("vc", CACHED, listOf("c")),
                        OfflineWant("vd", CACHED, listOf("d"))
                    ),
                    emptyList()
                )
            )
            store.setComplete("va", 3_000_000)
            store.setComplete("vc", 2_000_000)

            assertEquals(setOf("a", "c"), store.playableOffline.first())
            assertEquals(OfflineUsage(3_000_000, 2_000_000), store.usage.first())
        }

    @Test
    fun `changing a finished video's kind or tracks keeps it finished`() = runTest(dispatcher) {
        store.apply(OfflinePlan(listOf(OfflineWant("vc", CACHED, listOf("c"))), emptyList()))
        store.setComplete("vc", 1_000)

        store.apply(
            OfflinePlan(listOf(OfflineWant("vc", DOWNLOADED, listOf("c", "e"))), emptyList())
        )

        assertEquals(
            mapOf("vc" to OfflineEntry("vc", DOWNLOADED, true, setOf("c", "e"))),
            store.entries.first()
        )
    }

    @Test
    fun `a removed video and its tracks are gone`() = runTest(dispatcher) {
        store.apply(OfflinePlan(listOf(OfflineWant("va", DOWNLOADED, listOf("a"))), emptyList()))
        store.setComplete("va", 1_000)

        store.apply(OfflinePlan(emptyList(), listOf("va")))

        assertEquals(emptyMap<String, OfflineEntry>(), store.entries.first())
        assertEquals(emptySet<String>(), store.playableOffline.first())
        assertEquals(OfflineUsage(0, 0), store.usage.first())
    }
}
