package io.github.pranavm716.vgmixr.data.stream

import io.github.pranavm716.vgmixr.domain.playback.StreamResolution
import io.github.pranavm716.vgmixr.domain.playback.StreamResolver
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps resolved URLs for an hour. The player reopens a stream on every seek and retry, and a
 * YouTube URL stays valid for about six hours, so asking YouTube each time would only add delay.
 */
class CachingStreamResolver(private val upstream: StreamResolver, private val clock: Clock) :
    StreamResolver {
    private class Entry(val resolution: StreamResolution.Resolved, val at: Instant)

    private val entries = ConcurrentHashMap<String, Entry>()

    override suspend fun resolve(videoId: String): StreamResolution {
        val now = clock.instant()
        entries[videoId]?.takeIf {
            Duration.between(it.at, now) < TTL
        }?.let { return it.resolution }
        val resolution = upstream.resolve(videoId)
        if (resolution is StreamResolution.Resolved) entries[videoId] = Entry(resolution, now)
        return resolution
    }

    /** Drops a URL the server refused, so the next try resolves a fresh one. */
    fun forget(videoId: String) {
        entries.remove(videoId)
    }

    private companion object {
        val TTL: Duration = Duration.ofHours(1)
    }
}
