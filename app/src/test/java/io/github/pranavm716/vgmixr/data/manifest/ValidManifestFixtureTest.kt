package io.github.pranavm716.vgmixr.data.manifest

import io.github.pranavm716.vgmixr.domain.manifest.ManifestParseResult
import io.github.pranavm716.vgmixr.domain.model.Loop
import io.github.pranavm716.vgmixr.domain.model.Manifest
import io.github.pranavm716.vgmixr.domain.model.Source
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidManifestFixtureTest {
    private val manifest: Manifest by lazy {
        val result = ManifestParser().parse(ManifestFixtures.read("valid.json"))
        check(result is ManifestParseResult.Valid) {
            "Expected valid.json to be valid, got $result"
        }
        result.manifest
    }

    private fun game(id: String) = manifest.games.single { it.id == id }

    private fun track(id: String) = manifest.tracks.single { it.id == id }

    @Test
    fun `parses every game and track`() {
        assertEquals(7, manifest.games.size)
        assertEquals(6, manifest.tracks.size)
        assertEquals(Instant.parse("2026-10-14T08:00:00Z"), manifest.generatedAt)
    }

    @Test
    fun `resolves family ids`() {
        assertEquals("rse", game("ruby").familyId)
        assertEquals("botw", game("botw").familyId)
        assertNull(game("botw").generation)
    }

    @Test
    fun `parses loop points on local sources`() {
        assertEquals(
            Source.Local(
                "rse/012.flac",
                Loop(sampleRate = 32000, introEnd = 96000, loopEnd = 1824000)
            ),
            track("rse-route-101").sources.first()
        )
        assertEquals(
            listOf(
                Source.Local("botw/field/hyrule-field.ogg", loop = null),
                Source.Local(
                    "botw/field/hyrule-field-loop.flac",
                    Loop(48000, introEnd = 0, loopEnd = 1)
                )
            ),
            track("botw-hyrule-field").sources
        )
    }

    @Test
    fun `defaults omitted stream startMs and endMs`() {
        assertEquals(
            listOf(Source.Stream("WWWWWWWWWWW", startMs = 0, endMs = null)),
            track("pla-example-theme").sources
        )
    }

    @Test
    fun `parses stream slices`() {
        assertEquals(
            listOf(
                Source.Stream("Ab-cd_EF012", startMs = 120000, endMs = 245500),
                Source.Stream("Ab-cd_EF012", startMs = 245500, endMs = null)
            ),
            track("rse-weather-institute").sources
        )
    }

    @Test
    fun `parses track games and replaces`() {
        assertNull(track("rse-route-101").games)
        assertEquals(listOf("ruby", "emerald"), track("rse-weather-institute").games)
        assertEquals(listOf("rse-weather-lab"), track("rse-weather-institute").replaces)
        assertEquals(emptyList<String>(), track("rse-route-101").replaces)
    }
}
