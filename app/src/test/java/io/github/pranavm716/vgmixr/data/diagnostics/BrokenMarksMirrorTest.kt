package io.github.pranavm716.vgmixr.data.diagnostics

import io.github.pranavm716.vgmixr.data.manifest.ManifestFixtures
import io.github.pranavm716.vgmixr.domain.playback.BrokenMark
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.testing.FakeBrokenSources
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BrokenMarksMirrorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val eliteFour = BrokenMark(
        videoId = "7DbGoUJkNfU",
        trackId = "oras-battle-elite-four",
        reason = BrokenReason.NO_AUDIO,
        detail = "no playable audio stream",
        markedAt = Instant.parse("2026-10-21T07:23:44.638Z"),
        catalogGeneratedAt = "2026-10-21T03:12:40Z"
    )
    private val route101 = BrokenMark(
        videoId = "WMj9b4vYTXU",
        trackId = "rse-route-101",
        reason = BrokenReason.UNAVAILABLE,
        detail = "This video is private",
        markedAt = Instant.parse("2026-10-22T18:05:00Z"),
        catalogGeneratedAt = null
    )

    @Test
    fun `the file is the shared fixture's format, marks oldest first`() {
        assertEquals(
            ManifestFixtures.read("broken.json").replace("\r\n", "\n"),
            brokenJson(listOf(route101, eliteFour))
        )
    }

    @Test
    fun `the file follows the marks, also when they are all cleared`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sources = FakeBrokenSources()
        val file = File(folder.root, "broken.json")
        BrokenMarksMirror(file, sources, backgroundScope, dispatcher).start()

        sources.marks.value = mapOf(eliteFour.videoId to eliteFour)
        runCurrent()
        assertEquals(brokenJson(listOf(eliteFour)), file.readText())

        sources.marks.value = emptyMap()
        runCurrent()
        assertEquals(brokenJson(emptyList()), file.readText())
    }
}
