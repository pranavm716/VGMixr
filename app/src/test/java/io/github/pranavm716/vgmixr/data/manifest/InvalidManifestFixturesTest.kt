package io.github.pranavm716.vgmixr.data.manifest

import io.github.pranavm716.vgmixr.domain.manifest.ManifestParseResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Each fixture in invalid/ fails with exactly the error code the README's table lists for it. */
@RunWith(Parameterized::class)
class InvalidManifestFixturesTest(private val file: String, private val expectedCode: String) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<Array<String>> =
            ManifestFixtures.expectedCodes.map { (file, code) -> arrayOf(file, code) }
    }

    @Test
    fun `reports exactly its expected error`() {
        val errors = when (
            val result = ManifestParser().parse(
                ManifestFixtures.read("invalid/$file")
            )
        ) {
            is ManifestParseResult.Valid -> emptyList()
            is ManifestParseResult.Invalid -> result.errors
        }
        assertEquals(errors.toString(), listOf(expectedCode), errors.map { it.code })
    }
}
