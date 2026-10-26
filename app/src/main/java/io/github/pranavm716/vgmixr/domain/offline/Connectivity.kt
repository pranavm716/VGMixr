package io.github.pranavm716.vgmixr.domain.offline

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import kotlinx.coroutines.flow.StateFlow

/** Whether the phone can reach the internet now. */
interface Connectivity {
    val online: StateFlow<Boolean>
}

/**
 * This catalog as it plays with no connection: tracks outside [playable] (the ones on the phone)
 * are [CatalogTrack.offline]; broken ones stay as they are.
 */
fun Catalog.offline(playable: Set<String>): Catalog {
    fun mark(track: CatalogTrack) = if (track.unplayable == null && track.id !in playable) {
        track.copy(offline = true)
    } else {
        track
    }
    return copy(tracks = tracks.map(::mark), userTracks = userTracks.map(::mark))
}
