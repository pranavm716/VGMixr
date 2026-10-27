package io.github.pranavm716.vgmixr.ui.components

/**
 * A least-recently-used cache holding at most [maxSize], each value counting [sizeOf] toward it.
 * Thread-safe.
 */
class SizedLruCache<K, V : Any>(private val maxSize: Long, private val sizeOf: (V) -> Int) {
    private val entries = LinkedHashMap<K, V>(16, 0.75f, true)
    private var size = 0L

    @Synchronized
    operator fun get(key: K): V? = entries[key]

    @Synchronized
    fun put(key: K, value: V) {
        entries.remove(key)?.let { size -= sizeOf(it) }
        val added = sizeOf(value)
        if (added > maxSize) return
        entries[key] = value
        size += added
        val oldest = entries.iterator()
        while (size > maxSize && oldest.hasNext()) {
            size -= sizeOf(oldest.next().value)
            oldest.remove()
        }
    }
}
