package io.github.pranavm716.vgmixr.domain.search

import java.text.Normalizer

/**
 * Lower-case, accent- and punctuation-free words of [text], as search compares them: "Pokémon"
 * is "pokemon", "Trainers'" is "trainers", "Z-A" is "z" and "a". "gen 4" and "generation 4"
 * become the single word "gen4", as a track's generation is indexed.
 */
fun searchWords(text: String): List<String> {
    val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace(APOSTROPHES, "")
        .replace(NON_WORD, " ")
    val words = plain.split(' ').filter { it.isNotEmpty() }
    return buildList {
        var i = 0
        while (i < words.size) {
            val word = words[i]
            val next = words.getOrNull(i + 1)
            if (word in GEN_WORDS && next != null && next.all(Char::isDigit)) {
                add("gen$next")
                i += 2
            } else {
                add(word)
                i++
            }
        }
    }
}

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val APOSTROPHES = Regex("['’]")
private val NON_WORD = Regex("[^a-z0-9]+")
private val GEN_WORDS = setOf("gen", "generation")
