package io.github.pranavm716.vgmixr.data.catalog

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.data.manifest.ManifestParser
import io.github.pranavm716.vgmixr.domain.manifest.ManifestParseResult
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetsManifestSourceTest {
    @Test
    fun `a missing asset reads as null`() {
        val source =
            AssetsManifestSource(ApplicationProvider.getApplicationContext(), "missing.json")

        assertNull(source.read())
    }

    @Test
    fun `the bundled seed manifest is valid`() {
        val text = AssetsManifestSource(
            ApplicationProvider.getApplicationContext(),
            "manifest.json"
        )
            .read()

        val result = text?.let { ManifestParser().parse(it) }

        assertTrue("$result", result is ManifestParseResult.Valid)
    }
}
