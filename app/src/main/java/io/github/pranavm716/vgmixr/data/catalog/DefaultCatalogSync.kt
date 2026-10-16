package io.github.pranavm716.vgmixr.data.catalog

import android.util.Log
import io.github.pranavm716.vgmixr.data.manifest.ManifestParser
import io.github.pranavm716.vgmixr.di.ApplicationScope
import io.github.pranavm716.vgmixr.di.IoDispatcher
import io.github.pranavm716.vgmixr.domain.catalog.CatalogImportResult
import io.github.pranavm716.vgmixr.domain.catalog.CatalogSync
import io.github.pranavm716.vgmixr.domain.catalog.ImportError
import io.github.pranavm716.vgmixr.domain.catalog.ManifestSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Runs the catalog import on app start, on the application scope. */
@Singleton
class DefaultCatalogSync @Inject constructor(
    private val source: ManifestSource,
    private val parser: ManifestParser,
    private val importer: CatalogImporter,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher
) : CatalogSync {
    private val _status = MutableStateFlow<CatalogImportResult?>(null)
    override val status: StateFlow<CatalogImportResult?> = _status.asStateFlow()

    /** Imports the bundled manifest in the background, publishing the result to [status]. */
    fun start() {
        scope.launch(io) {
            val result = sync()
            if (result is CatalogImportResult.Failed) {
                Log.w(TAG, "Catalog import failed: ${result.errors}")
            }
            _status.value = result
        }
    }

    private suspend fun sync(): CatalogImportResult {
        val text = source.read()
            ?: return CatalogImportResult.Failed(listOf(ImportError.ManifestMissing))
        return importer.import(parser.parse(text))
    }

    private companion object {
        const val TAG = "CatalogSync"
    }
}
