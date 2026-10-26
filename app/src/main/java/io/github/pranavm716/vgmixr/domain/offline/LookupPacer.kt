package io.github.pranavm716.vgmixr.domain.offline

import java.time.Duration
import java.time.Instant

/**
 * Spaces the YouTube lookups downloads make, so a bulk fill doesn't trip the bot check: at least
 * [gap] apart, at most [hourlyCap] in any hour, and none for [botCheckPause] after a bot check.
 * Not thread-safe; one downloader uses it.
 */
class LookupPacer(
    private val gap: Duration = Duration.ofSeconds(6),
    private val hourlyCap: Int = 150,
    private val botCheckPause: Duration = Duration.ofMinutes(30)
) {
    private val lookups = ArrayDeque<Instant>()
    private var pausedUntil: Instant = Instant.MIN

    /** How long to wait at [now] before the next lookup. */
    fun waitBefore(now: Instant): Duration {
        while (lookups.isNotEmpty() && !lookups.first().plus(HOUR).isAfter(now)) {
            lookups.removeFirst()
        }
        val earliest = listOfNotNull(
            pausedUntil,
            lookups.lastOrNull()?.plus(gap),
            lookups.firstOrNull()?.plus(HOUR)?.takeIf { lookups.size >= hourlyCap }
        ).max()
        return if (earliest.isAfter(now)) Duration.between(now, earliest) else Duration.ZERO
    }

    fun onLookup(now: Instant) {
        lookups.addLast(now)
    }

    fun onBotCheck(now: Instant) {
        pausedUntil = now.plus(botCheckPause)
    }

    private companion object {
        val HOUR: Duration = Duration.ofHours(1)
    }
}
