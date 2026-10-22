package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.testing.FakeBrokenSources
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BrokenSourceCheckTest {
    private val resolved = StreamResolution.Resolved("https://g/a", "audio/webm", 160)
    private val results = mutableMapOf<String, StreamResolution>()
    private val asked = mutableListOf<String>()
    private val resolver = object : StreamResolver {
        override suspend fun resolve(videoId: String): StreamResolution {
            asked += videoId
            return results.getValue(videoId)
        }
    }

    private fun stream(videoId: String) = Source.Stream(videoId, 0, null)

    private val track = CatalogTrack(
        "oras-a",
        "oras",
        emptyList(),
        "Battle! (Elite Four)",
        1,
        listOf(stream("aaaaaaaaaaa"), stream("bbbbbbbbbbb")),
        BrokenReason.NO_AUDIO
    )

    private fun mark(videoId: String) =
        BrokenMark(videoId, "oras-a", BrokenReason.NO_AUDIO, null, Instant.EPOCH, null)

    private val broken = FakeBrokenSources(
        mapOf("aaaaaaaaaaa" to mark("aaaaaaaaaaa"), "bbbbbbbbbbb" to mark("bbbbbbbbbbb"))
    )
    private val check = BrokenSourceCheck(
        FakeCatalogRepository(Catalog(emptyList(), emptyList(), listOf(track))),
        broken,
        resolver
    )

    @Test
    fun `a video that resolves again loses its mark, so the track can play`() = runTest {
        results["aaaaaaaaaaa"] = StreamResolution.NoAudio
        results["bbbbbbbbbbb"] = resolved

        assertEquals(TryAgainResult.Back, check.tryAgain("oras-a"))
        assertEquals(setOf("aaaaaaaaaaa"), broken.marks.first().keys)
    }

    @Test
    fun `a video still dead keeps its mark, and the fresh reason is told`() = runTest {
        results["aaaaaaaaaaa"] = StreamResolution.Unavailable("This video is private")
        results["bbbbbbbbbbb"] = StreamResolution.NoAudio

        assertEquals(
            TryAgainResult.StillBroken(BrokenReason.UNAVAILABLE),
            check.tryAgain("oras-a")
        )
        assertEquals(setOf("aaaaaaaaaaa", "bbbbbbbbbbb"), broken.marks.first().keys)
    }

    @Test
    fun `without a connection nothing is checked or cleared`() = runTest {
        results["aaaaaaaaaaa"] = StreamResolution.Failed(ResolveFailure.NETWORK)
        results["bbbbbbbbbbb"] = resolved

        assertEquals(TryAgainResult.Unchecked, check.tryAgain("oras-a"))
        assertEquals(listOf("aaaaaaaaaaa"), asked)
        assertEquals(setOf("aaaaaaaaaaa", "bbbbbbbbbbb"), broken.marks.first().keys)
    }

    @Test
    fun `only the track's marked videos are asked about`() = runTest {
        broken.marks.value = mapOf("bbbbbbbbbbb" to mark("bbbbbbbbbbb"))
        results["bbbbbbbbbbb"] = resolved

        assertEquals(TryAgainResult.Back, check.tryAgain("oras-a"))
        assertEquals(listOf("bbbbbbbbbbb"), asked)
    }
}
