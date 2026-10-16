package io.github.pranavm716.vgmixr.data.catalog

import android.content.Context
import io.github.pranavm716.vgmixr.domain.catalog.ManifestSource
import java.io.FileNotFoundException

/** Reads the manifest bundled in the app's assets. A missing asset reads as null. */
class AssetsManifestSource(private val context: Context, private val fileName: String) :
    ManifestSource {
    override fun read(): String? = try {
        context.assets.open(fileName).bufferedReader().use { it.readText() }
    } catch (_: FileNotFoundException) {
        null
    }
}
