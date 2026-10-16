package io.github.pranavm716.vgmixr.data.catalog

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.data.db.inMemoryDatabase
import io.github.pranavm716.vgmixr.data.manifest.ManifestFixtures
import io.github.pranavm716.vgmixr.data.manifest.ManifestParser
import io.github.pranavm716.vgmixr.domain.catalog.CatalogImportResult
import io.github.pranavm716.vgmixr.domain.catalog.ImportError
import io.github.pranavm716.vgmixr.domain.catalog.ManifestSource
import java.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultCatalogSyncTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = inMemoryDatabase(dispatcher)

    @After
    fun closeDb() = db.close()

    private fun TestScope.sync(source: ManifestSource) = DefaultCatalogSync(
        source = source,
        parser = ManifestParser(),
        importer = TestCatalog.importer(db),
        scope = this,
        io = dispatcher
    )

    @Test
    fun `status is null until the import finishes, then Imported`() = runTest(dispatcher) {
        val sync = sync { ManifestFixtures.read("valid.json") }

        sync.start()
        assertNull(sync.status.value)
        advanceUntilIdle()

        assertEquals(
            CatalogImportResult.Imported(Instant.parse("2026-10-14T08:00:00Z")),
            sync.status.value
        )
        assertEquals(6, db.catalogDao().tracks().size)
    }

    @Test
    fun `a second start with the same manifest is Unchanged`() = runTest(dispatcher) {
        sync { ManifestFixtures.read("valid.json") }.apply { start() }
        advanceUntilIdle()
        val second = sync { ManifestFixtures.read("valid.json") }

        second.start()
        advanceUntilIdle()

        assertEquals(CatalogImportResult.Unchanged, second.status.value)
    }

    @Test
    fun `a missing manifest is Failed without crashing`() = runTest(dispatcher) {
        val sync = sync { null }

        sync.start()
        advanceUntilIdle()

        assertEquals(
            CatalogImportResult.Failed(listOf(ImportError.ManifestMissing)),
            sync.status.value
        )
    }

    @Test
    fun `an invalid manifest is Failed with its validation errors`() = runTest(dispatcher) {
        val sync = sync {
            ManifestFixtures.read("invalid/tracks.title-empty.json")
        }

        sync.start()
        advanceUntilIdle()

        val status = sync.status.value
        check(status is CatalogImportResult.Failed) { "Expected Failed, got $status" }
        assertEquals(
            listOf("track-title-empty"),
            status.errors.map { (it as ImportError.Invalid).error.code }
        )
        assertEquals(0, db.catalogDao().tracks().size)
    }
}
