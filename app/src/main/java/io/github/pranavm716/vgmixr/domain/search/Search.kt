package io.github.pranavm716.vgmixr.domain.search

import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.history.Play
import java.time.Instant

/**
 * The tracks matching [query], best first. Every query word must match a word of the track's
 * title, family, games, generation or region (see [matchWord] and [SEARCH_ALIASES]); a title word
 * counts double. Tracks with the same text score are ordered by [weight] (history), then by how
 * much of the title matched, then in Games tab order.
 */
fun search(
    query: String,
    index: SearchIndex,
    history: List<Play>,
    now: Instant,
    weight: HistoryWeight = NoHistoryWeight
): List<CatalogTrack> {
    val words = searchWords(query)
    if (words.isEmpty()) return emptyList()
    return index.entries
        .mapNotNull { entry ->
            score(words, entry) { weight.weight(entry.track.id, history, now) }
        }
        .sortedWith(
            compareByDescending<Scored> { it.points }
                .thenByDescending { it.history }
                .thenByDescending { it.coverage }
                .thenBy { it.entry.familyPosition }
                .thenBy { it.entry.track.order }
                .thenBy { it.entry.track.id }
        )
        .map { it.entry.track }
}

/**
 * How much a track's play [history] lifts it at [now]. It only orders tracks whose text matches
 * score the same, so it never lifts a weaker match over a stronger one. Step 12b fills it in.
 */
fun interface HistoryWeight {
    fun weight(trackId: String, history: List<Play>, now: Instant): Double
}

/** No history effect yet. */
val NoHistoryWeight = HistoryWeight { _, _, _ -> 0.0 }

private class Scored(
    val entry: IndexedTrack,
    val points: Int,
    val history: Double,
    val coverage: Double
)

/** A query word's best match: its points and the title words it used. */
private class WordScore(val points: Int, val titleWords: Set<Int>)

/** [entry]'s score, or null unless every word matches; [history] is asked only for a match. */
private fun score(words: List<String>, entry: IndexedTrack, history: () -> Double): Scored? {
    var points = 0
    val matchedTitleWords = mutableSetOf<Int>()
    for (word in words) {
        val best = listOfNotNull(
            literalScore(word, entry),
            SEARCH_ALIASES[word]?.let { aliasScore(it, entry) }
        ).maxByOrNull { it.points } ?: return null
        points += best.points
        matchedTitleWords += best.titleWords
    }
    val coverage = if (entry.title.isEmpty()) {
        0.0
    } else {
        matchedTitleWords.size.toDouble() / entry.title.size
    }
    return Scored(entry, points, history(), coverage)
}

/** A title word scores twice a family or game word of the same kind of match. */
private fun literalScore(word: String, entry: IndexedTrack): WordScore? {
    var titlePoints = 0
    var titleIndex = -1
    entry.title.forEachIndexed { i, target ->
        val points = (matchWord(word, target)?.tenths ?: 0) * TITLE_WEIGHT
        if (points > titlePoints) {
            titlePoints = points
            titleIndex = i
        }
    }
    val metaPoints = entry.meta.maxOfOrNull { matchWord(word, it)?.tenths ?: 0 } ?: 0
    return when {
        titlePoints == 0 && metaPoints == 0 -> null
        titlePoints >= metaPoints -> WordScore(titlePoints, setOf(titleIndex))
        else -> WordScore(metaPoints, emptySet())
    }
}

/**
 * An alias matches when every word of its [expansion] is a word of the track, and scores as one
 * exact word: in the title if all of them are there.
 */
private fun aliasScore(expansion: List<String>, entry: IndexedTrack): WordScore? {
    val titleIndexes = expansion.map { entry.title.indexOf(it) }
    if (titleIndexes.none { it < 0 }) {
        return WordScore(WordMatch.EXACT.tenths * TITLE_WEIGHT, titleIndexes.toSet())
    }
    val everywhere = entry.title + entry.meta
    return if (expansion.all { it in everywhere }) {
        WordScore(WordMatch.EXACT.tenths, titleIndexes.filter { it >= 0 }.toSet())
    } else {
        null
    }
}

private const val TITLE_WEIGHT = 2
