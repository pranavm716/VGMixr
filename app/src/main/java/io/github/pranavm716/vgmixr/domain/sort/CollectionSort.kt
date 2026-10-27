package io.github.pranavm716.vgmixr.domain.sort

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.TrackFilter
import io.github.pranavm716.vgmixr.domain.catalog.dimmed
import io.github.pranavm716.vgmixr.domain.catalog.filter
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
import java.time.Instant

// What each collection lists and plays, in its saved sort. A tap and a long-press play read these
// too, so playing follows the order on screen.

/** Family [familyId]'s tracks in [sort]. */
fun Catalog.familyTracks(familyId: String, sort: SortSetting): List<CatalogTrack> =
    sorted(filter(TrackFilter(family = familyId)), sort) { null }

/** The user's tracks (Remixes) in [sort], by when each link was pasted. */
fun Catalog.sortedUserTracks(sort: SortSetting): List<CatalogTrack> =
    sorted(userTracks, sort) { it.addedAt }

/**
 * [playlist]'s tracks found in the catalog, retired ones included, in its own sort, by when each
 * went into it. Deleted remixes are left out.
 */
fun Catalog.playlistTracks(playlist: Playlist): List<CatalogTrack> =
    sorted(playlist.trackIds.mapNotNull(::trackOrRetired), playlist.sort) {
        playlist.addedAt[it.id]
    }

/** What a tap in family [familyId] plays: its list in [sort], without greyed tracks. */
fun Catalog.playableFamilyTrackIds(familyId: String, sort: SortSetting): List<String> =
    familyTracks(familyId, sort).playableIds()

/** What a tap on a user track plays: Remixes in [sort], without greyed tracks. */
fun Catalog.playableUserTrackIds(sort: SortSetting): List<String> =
    sortedUserTracks(sort).playableIds()

/** What a tap in [playlist] plays and a swipe queues: its list, without greyed tracks. */
fun Catalog.playablePlaylistTrackIds(playlist: Playlist): List<String> =
    playlistTracks(playlist).playableIds()

private fun List<CatalogTrack>.playableIds() = filterNot { it.dimmed }.map { it.id }

/**
 * [tracks] in [sort]. [tracks] come newest first, so where a time is missing (a family's tracks,
 * data from before times were kept) their places stand in for when each was added.
 */
private fun Catalog.sorted(
    tracks: List<CatalogTrack>,
    sort: SortSetting,
    addedAt: (CatalogTrack) -> Instant?
): List<CatalogTrack> {
    val byId = tracks.associateBy { it.id }
    val times = tracks.map(addedAt).takeIf { times -> times.all { it != null } }
        ?: tracks.indices.map { Instant.ofEpochSecond((tracks.size - it).toLong()) }
    return tracks.mapIndexed { i, track -> sortRow(track, times[i]) }
        .sortedBy(sort)
        .mapNotNull { byId[it.id] }
}
