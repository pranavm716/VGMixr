package io.github.pranavm716.vgmixr.domain.playlist

import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.sort.playablePlaylistTrackIds
import kotlinx.coroutines.flow.first

/**
 * The playlists after Favorites on the app icon's press-and-hold menu, in order, found by name.
 * One UI shows 4 rows there.
 */
private val SHORTCUT_PLAYLISTS = listOf("Hype", "Atmosphere", "Chill")

/** A row on the app icon's press-and-hold menu, which plays [playlistId]. */
data class PlaylistShortcut(val playlistId: Long, val label: String, val isFavorites: Boolean)

/** Favorites, then [SHORTCUT_PLAYLISTS] that exist (a renamed one drops out). */
fun playlistShortcuts(playlists: List<PlaylistSummary>): List<PlaylistShortcut> {
    val favorites = playlists.filter { it.isFavorites }
    val named = SHORTCUT_PLAYLISTS.mapNotNull { name ->
        playlists.firstOrNull { !it.isFavorites && it.name.equals(name, ignoreCase = true) }
    }
    return (favorites + named).map { PlaylistShortcut(it.id, it.name, it.isFavorites) }
}

/**
 * Plays all of playlist [playlistId] in its own sort, leaving out tracks that can't play, shuffled
 * if shuffle is on: a long press in the Playlists tab, or its row on the app icon's menu.
 */
suspend fun playPlaylist(
    playlistId: Long,
    playlists: PlaylistRepository,
    catalog: CatalogRepository,
    playback: PlaybackController
) {
    val playlist = playlists.playlist(playlistId).first() ?: return
    val ids = catalog.catalog.first().playablePlaylistTrackIds(playlist)
    if (ids.isNotEmpty()) playback.playAll(ids, QueueContext.Playlist(playlistId))
}
