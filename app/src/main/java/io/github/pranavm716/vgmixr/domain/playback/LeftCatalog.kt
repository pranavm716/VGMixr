package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.catalog.Catalog

/**
 * The upcoming entries of [queue] whose track left the catalog since it was queued: retired by a
 * new manifest, or a deleted remix. The player skips them; the playing one plays to its end.
 */
fun Catalog.leftCatalog(queue: PlayQueue): List<Int> =
    (queue.current + 1 until queue.entries.size).filter { track(queue.entries[it].trackId) == null }
