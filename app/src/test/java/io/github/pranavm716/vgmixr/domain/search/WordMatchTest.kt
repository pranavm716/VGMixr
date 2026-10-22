package io.github.pranavm716.vgmixr.domain.search

import io.github.pranavm716.vgmixr.domain.search.WordMatch.EXACT
import io.github.pranavm716.vgmixr.domain.search.WordMatch.PREFIX
import io.github.pranavm716.vgmixr.domain.search.WordMatch.TYPO_1
import io.github.pranavm716.vgmixr.domain.search.WordMatch.TYPO_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordMatchTest {
    @Test
    fun `the same word is exact`() {
        assertEquals(EXACT, matchWord("route", "route"))
    }

    @Test
    fun `the start of a word is a prefix`() {
        assertEquals(PREFIX, matchWord("litt", "littleroot"))
        assertEquals(PREFIX, matchWord("l", "littleroot"))
    }

    @Test
    fun `a swap of two neighbouring letters is one typo`() {
        assertEquals(TYPO_1, matchWord("eliet", "elite"))
        assertEquals(TYPO_1, matchWord("centre", "center"))
    }

    @Test
    fun `a wrong, missing or extra letter is one typo`() {
        assertEquals(TYPO_1, matchWord("pokrmon", "pokemon"))
        assertEquals(TYPO_1, matchWord("pokmon", "pokemon"))
        assertEquals(TYPO_1, matchWord("routes", "route"))
    }

    @Test
    fun `a typo in a word still being typed matches the start of the word`() {
        assertEquals(TYPO_1, matchWord("pokrm", "pokemon"))
    }

    @Test
    fun `short words allow no typo`() {
        assertNull(matchWord("gyn", "gym"))
    }

    @Test
    fun `words of four to seven letters allow one typo`() {
        assertNull(matchWord("pakemun", "pokemon"))
    }

    @Test
    fun `words of eight letters or more allow two typos`() {
        assertEquals(TYPO_2, matchWord("littelrot", "littleroot"))
        assertNull(matchWord("lottelrat", "littleroot"))
    }

    @Test
    fun `words with a digit never match by typo`() {
        assertNull(matchWord("110", "101"))
        assertNull(matchWord("e5", "e4"))
        assertNull(matchWord("route1o1", "route101"))
    }

    @Test
    fun `words with a digit still match as a prefix`() {
        assertEquals(PREFIX, matchWord("10", "101"))
    }

    @Test
    fun `an unrelated word doesn't match`() {
        assertNull(matchWord("surf", "bicycle"))
    }
}
