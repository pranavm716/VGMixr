package io.github.pranavm716.vgmixr.domain.playback

import kotlin.random.Random

/**
 * At most this share of a shuffled list is held back at its end as recently heard. One day of
 * listening covered up to ~60% of a playlist in the play log, so two thirds keeps a day's tracks
 * out of the next day's shuffle while the front stays a real shuffle.
 */
const val HELD_FRACTION = 2.0 / 3

/**
 * Shuffles [items] so the tracks ([trackOf]) heard most recently come last: of the tracks in
 * [lastPlayed] (track id -> rank, 0 = heard last), the newest, filling up to [HELD_FRACTION] of
 * [items], go to the end, oldest first, so the one heard last plays last. Repeats of a track stay
 * together. The rest, older plays included, shuffle in front. With nothing heard, a plain shuffle.
 */
fun <T> smartShuffled(
    items: List<T>,
    trackOf: (T) -> String,
    lastPlayed: Map<String, Int>,
    random: Random
): List<T> {
    val cap = (items.size * HELD_FRACTION).toInt()
    val byTrack = items.groupBy(trackOf)
    val held = mutableListOf<String>()
    var size = 0
    for (track in byTrack.keys.filter { it in lastPlayed }.sortedBy { lastPlayed.getValue(it) }) {
        val count = byTrack.getValue(track).size
        if (size + count > cap) break
        held += track
        size += count
    }
    val heldSet = held.toSet()
    val front = items.filter { trackOf(it) !in heldSet }.shuffled(random)
    return front + held.asReversed().flatMap { byTrack.getValue(it) }
}
