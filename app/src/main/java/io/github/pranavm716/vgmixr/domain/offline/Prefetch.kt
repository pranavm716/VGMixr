package io.github.pranavm716.vgmixr.domain.offline

import io.github.pranavm716.vgmixr.domain.playback.PlayQueue

/**
 * The track to prefetch: the one after the current track, once the current one has counted as a
 * play ([played], 10 s in). Skipping through a list then fetches nothing, which would spend
 * YouTube lookups and hold back playlist downloads on tracks never heard.
 */
fun prefetchTarget(queue: PlayQueue, played: String?): String? {
    val current = queue.entries.getOrNull(queue.current)?.trackId ?: return null
    if (current != played) return null
    return queue.entries.getOrNull(queue.current + 1)?.trackId
}
