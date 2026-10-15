package io.github.pranavm716.vgmixr.data.manifest

import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the README tables that drive the fixture tests against drifting from the files. */
class ManifestFixturesTableTest {
    @Test
    fun `fixture table lists exactly the files in invalid`() {
        assertEquals(ManifestFixtures.invalidFiles, ManifestFixtures.expectedCodes.keys)
    }

    @Test
    fun `fixture table uses every error code and no others`() {
        assertEquals(ManifestFixtures.errorCodes, ManifestFixtures.expectedCodes.values.toSet())
    }

    @Test
    fun `error code table lists 26 codes`() {
        assertEquals(26, ManifestFixtures.errorCodes.size)
    }
}
