package io.github.pranavm716.vgmixr.domain.search

/** How well a query word matches a word of a track, best first. */
enum class WordMatch(val tenths: Int) {
    EXACT(10),
    PREFIX(8),
    TYPO_1(6),
    TYPO_2(4)
}

/**
 * How [query] matches [target], or null. A typo is a wrong, missing or extra letter, or two
 * neighbouring letters swapped; it counts against the whole word or, for a word still being
 * typed, its start. Words up to 3 letters allow no typo, up to 7 one, longer ones two. A word
 * with a digit never matches by typo, so "101" doesn't find "110".
 */
fun matchWord(query: String, target: String): WordMatch? {
    if (query == target) return WordMatch.EXACT
    if (target.startsWith(query)) return WordMatch.PREFIX
    val allowed = allowedTypos(query)
    if (allowed == 0) return null
    val start = target.take(query.length)
    val typos = minOf(
        typoDistance(query, target, allowed),
        if (start.length < target.length) typoDistance(query, start, allowed) else Int.MAX_VALUE
    )
    return when {
        typos == 1 -> WordMatch.TYPO_1
        typos <= allowed -> WordMatch.TYPO_2
        else -> null
    }
}

private fun allowedTypos(query: String): Int = when {
    query.any(Char::isDigit) -> 0
    query.length <= 3 -> 0
    query.length <= 7 -> 1
    else -> 2
}

/**
 * Optimal string alignment distance between [a] and [b], or more than [max] once it is certain to
 * exceed it.
 */
private fun typoDistance(a: String, b: String, max: Int): Int {
    if (kotlin.math.abs(a.length - b.length) > max) return max + 1
    var twoBack = IntArray(b.length + 1)
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        var rowMin = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var d = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                d = minOf(d, twoBack[j - 2] + 1)
            }
            current[j] = d
            rowMin = minOf(rowMin, d)
        }
        if (rowMin > max) return max + 1
        val recycled = twoBack
        twoBack = previous
        previous = current
        current = recycled
    }
    return previous[b.length]
}
