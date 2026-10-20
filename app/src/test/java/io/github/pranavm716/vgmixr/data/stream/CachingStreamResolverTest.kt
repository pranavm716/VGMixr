package io.github.pranavm716.vgmixr.data.stream

import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.playback.StreamResolution
import io.github.pranavm716.vgmixr.domain.playback.StreamResolver
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CachingStreamResolverTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this
    }

    private class CountingResolver(var result: StreamResolution) : StreamResolver {
        val calls = mutableListOf<String>()

        override suspend fun resolve(videoId: String): StreamResolution {
            calls += videoId
            return result
        }
    }

    private val clock = MutableClock(Instant.parse("2026-10-20T12:00:00Z"))
    private val resolved = StreamResolution.Resolved("https://a", "audio/webm", 160)
    private val upstream = CountingResolver(resolved)
    private val resolver = CachingStreamResolver(upstream, clock)

    @Test
    fun `a resolved URL is reused, since seeking reopens the stream`() = runTest {
        assertEquals(resolved, resolver.resolve("aaa"))
        assertEquals(resolved, resolver.resolve("aaa"))

        assertEquals(listOf("aaa"), upstream.calls)
    }

    @Test
    fun `each video is resolved on its own`() = runTest {
        resolver.resolve("aaa")
        resolver.resolve("bbb")

        assertEquals(listOf("aaa", "bbb"), upstream.calls)
    }

    @Test
    fun `a URL is resolved again after an hour, well before YouTube's expire`() = runTest {
        resolver.resolve("aaa")
        clock.now += Duration.ofMinutes(59)
        resolver.resolve("aaa")
        clock.now += Duration.ofMinutes(2)
        resolver.resolve("aaa")

        assertEquals(listOf("aaa", "aaa"), upstream.calls)
    }

    @Test
    fun `failures are not cached, so the next try asks again`() = runTest {
        upstream.result = StreamResolution.Failed(ResolveFailure.NETWORK)
        resolver.resolve("aaa")
        upstream.result = resolved

        assertEquals(resolved, resolver.resolve("aaa"))
        assertEquals(listOf("aaa", "aaa"), upstream.calls)
    }

    @Test
    fun `a forgotten URL is resolved again`() = runTest {
        resolver.resolve("aaa")
        resolver.forget("aaa")
        resolver.resolve("aaa")

        assertEquals(listOf("aaa", "aaa"), upstream.calls)
    }
}
