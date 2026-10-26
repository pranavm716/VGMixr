package io.github.pranavm716.vgmixr.domain.offline

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.playback.playableStreams
import kotlinx.coroutines.flow.StateFlow

/** Whether the phone can reach the internet now. */
interface Connectivity {
    val online: StateFlow<Boolean>
}

/**
 * This catalog as it plays with no connection. A track plays if the video playback would pick
 * (its first source that is on the phone or not in [broken], as the player chooses) is in
 * [onPhone], the finished videos; every other track is [CatalogTrack.offline]. So every slice of a
 * downloaded album plays, kept in a playlist or not. Broken tracks stay as they are.
 */
fun Catalog.offline(onPhone: Set<String>, broken: Set<String>): Catalog {
    fun mark(track: CatalogTrack): CatalogTrack {
        if (track.unplayable != null) return track
        val video = playableStreams(track.sources)
            .firstOrNull { it.videoId in onPhone || it.videoId !in broken }
            ?.videoId
        return if (video in onPhone) track else track.copy(offline = true)
    }
    return copy(tracks = tracks.map(::mark), userTracks = userTracks.map(::mark))
}
