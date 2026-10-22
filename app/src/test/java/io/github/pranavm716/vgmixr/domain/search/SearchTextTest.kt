package io.github.pranavm716.vgmixr.domain.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextTest {
    @Test
    fun `lower-cases and splits on spaces`() {
        assertEquals(listOf("littleroot", "town"), searchWords("Littleroot Town"))
    }

    @Test
    fun `strips accents`() {
        assertEquals(listOf("pokemon", "center"), searchWords("Pokémon Center"))
    }

    @Test
    fun `drops apostrophes inside words`() {
        assertEquals(
            listOf("trainers", "eyes", "meet", "lets", "go"),
            searchWords("Trainers' Eyes Meet — Let's Go")
        )
    }

    @Test
    fun `other punctuation separates words`() {
        assertEquals(
            listOf("battle", "elite", "four", "z", "a"),
            searchWords("Battle! (Elite Four) Z-A")
        )
    }

    @Test
    fun `keeps digits with their letters`() {
        assertEquals(listOf("rt", "101", "e4", "b2"), searchWords("rt 101 E4 B2"))
    }

    @Test
    fun `merges gen and a number into one word`() {
        assertEquals(listOf("gen4", "route"), searchWords("gen 4 route"))
        assertEquals(listOf("gen4", "route"), searchWords("Gen4 route"))
        assertEquals(listOf("gen10"), searchWords("generation 10"))
    }

    @Test
    fun `gen alone stays a word`() {
        assertEquals(listOf("gen"), searchWords("gen"))
        assertEquals(listOf("gen", "route"), searchWords("gen route"))
    }

    @Test
    fun `blank text has no words`() {
        assertEquals(emptyList<String>(), searchWords("  !? "))
    }
}
