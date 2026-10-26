package io.github.pranavm716.vgmixr.domain.offline

import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.playback.StreamResolution
import io.github.pranavm716.vgmixr.domain.playback.StreamResolver
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PacedStreamResolverTest {
    private class FakeResolver(var next: StreamResolution) : StreamResolver {
        override suspend fun resolve(videoId: String) = next
    }

    private fun TestScope.paced(upstream: StreamResolver) = PacedStreamResolver(
        upstream,
        LookupPacer(
            gap = Duration.ofSeconds(6),
            hourlyCap = 100,
            botCheckPause = Duration.ofMinutes(30)
        )
    ) { Instant.ofEpochMilli(currentTime) }

    @Test
    fun `lookups wait for the gap between them`() = runTest {
        val resolver =
            paced(FakeResolver(StreamResolution.Resolved("https://x", "audio/webm", 160)))

        resolver.resolve("a")
        resolver.resolve("b")

        assertEquals(6_000, currentTime)
    }

    @Test
    fun `a bot check holds the next lookup for the pause`() = runTest {
        val upstream = FakeResolver(StreamResolution.Failed(ResolveFailure.BOT_CHECK))
        val resolver = paced(upstream)

        resolver.resolve("a")
        upstream.next = StreamResolution.Resolved("https://x", "audio/webm", 160)
        resolver.resolve("b")

        assertEquals(Duration.ofMinutes(30).toMillis(), currentTime)
    }
}
