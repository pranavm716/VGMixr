package io.github.pranavm716.vgmixr.ui.components

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.data.catalog.AssetsManifestSource
import io.github.pranavm716.vgmixr.data.manifest.ManifestParser
import io.github.pranavm716.vgmixr.domain.manifest.ManifestParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FamilyCoversTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `every bundled cover is a WebP named after a family in the manifest`() {
        val text = AssetsManifestSource(context, "manifest.json").read()
        val result = text?.let { ManifestParser().parse(it) }
        assertTrue("$result", result is ManifestParseResult.Valid)
        val families = (result as ManifestParseResult.Valid).manifest.tracks
            .map { it.familyId }
            .toSet()

        val files = context.assets.list(COVERS_DIR).orEmpty().toList()

        assertEquals(emptyList<String>(), files.filterNot { it.endsWith(".webp") })
        assertEquals(emptyList<String>(), files.map { it.removeSuffix(".webp") } - families)
    }

    @Test
    fun `the families with covers are read from the file names`() {
        val files = context.assets.list(COVERS_DIR).orEmpty()

        assertEquals(
            files.map { it.removeSuffix(".webp") }.toSet(),
            coverFamilies(context.assets)
        )
    }

    @Test
    fun `a family's cover is its webp in the covers folder`() {
        assertEquals("covers/rse.webp", coverAsset("rse"))
    }
}
