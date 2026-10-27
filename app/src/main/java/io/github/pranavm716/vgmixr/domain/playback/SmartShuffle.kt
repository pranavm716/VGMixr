package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.history.Play
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

/** How many slots after the tapped track hold only tracks not heard recently. */
const val FRESH_SLOTS = 10

/** How long a played track counts as recent for shuffle. */
val RECENT_WINDOW: Duration = 1.hours

/**
 * Shuffles [items] so the first [FRESH_SLOTS] are not recent: fresh ones in random order fill
 * them, and the recent ones shuffle in among the rest after them. With too few fresh ones, they
 * come first and the recent ones follow, each group shuffled. With none recent, a plain shuffle.
 */
fun <T> smartShuffled(items: List<T>, isRecent: (T) -> Boolean, random: Random): List<T> {
    val (recent, fresh) = items.partition(isRecent)
    if (recent.isEmpty()) return items.shuffled(random)
    val shuffledFresh = fresh.shuffled(random)
    return shuffledFresh.take(FRESH_SLOTS) +
        (shuffledFresh.drop(FRESH_SLOTS) + recent).shuffled(random)
}

/** The tracks played within [window] before [now]. */
fun recentTrackIds(plays: List<Play>, now: Instant, window: Duration = RECENT_WINDOW): Set<String> {
    val since = now.minus(window.toJavaDuration())
    return plays.filter { it.playedAt.isAfter(since) }.mapTo(mutableSetOf()) { it.trackId }
}
