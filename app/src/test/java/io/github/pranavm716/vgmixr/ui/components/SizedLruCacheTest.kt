package io.github.pranavm716.vgmixr.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SizedLruCacheTest {
    private val cache = SizedLruCache<String, String>(maxSize = 10) { it.length }

    @Test
    fun `returns what was put`() {
        cache.put("a", "aaa")
        assertEquals("aaa", cache["a"])
        assertNull(cache["b"])
    }

    @Test
    fun `over the limit it drops the least recently used first`() {
        cache.put("a", "aaaa")
        cache.put("b", "bbbb")
        cache["a"]
        cache.put("c", "cccc")
        assertNull(cache["b"])
        assertEquals("aaaa", cache["a"])
        assertEquals("cccc", cache["c"])
    }

    @Test
    fun `an entry bigger than the whole cache is not kept`() {
        cache.put("a", "aaaa")
        cache.put("big", "x".repeat(11))
        assertNull(cache["big"])
        assertEquals("aaaa", cache["a"])
    }

    @Test
    fun `replacing an entry frees its old size`() {
        cache.put("a", "aaaaaa")
        cache.put("a", "aa")
        cache.put("b", "bbbbbbbb")
        assertEquals("aa", cache["a"])
        assertEquals("bbbbbbbb", cache["b"])
    }
}
