package io.github.pranavm716.vgmixr.data.catalog

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
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
}
