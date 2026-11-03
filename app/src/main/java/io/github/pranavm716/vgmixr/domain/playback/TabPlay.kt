package io.github.pranavm716.vgmixr.domain.playback

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.settings.HoldSettings
import io.github.pranavm716.vgmixr.domain.settings.SortSettings
import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import io.github.pranavm716.vgmixr.domain.sort.playableFamilyTrackIds
import io.github.pranavm716.vgmixr.domain.sort.playablePlaylistTrackIds
import io.github.pranavm716.vgmixr.domain.sort.playableUserTrackIds
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

// What a long press on the Games or Playlists tab plays, from the choices in Settings.

/** Tracks a tab's long press plays, and the context they play from. */
data class TabMix(val trackIds: List<String>, val context: QueueContext)

/**
 * Every family but [excluded], in Games-tab order, each in [sort] without greyed tracks. One
 * family left plays as that family. Null when there is nothing to play.
 */
fun Catalog.gamesMix(excluded: Set<String>, sort: SortSetting): TabMix? {
    val chosen = families.filterNot { it.id in excluded }
    val ids = chosen.flatMap { playableFamilyTrackIds(it.id, sort) }
    if (ids.isEmpty()) return null
    val context = chosen.singleOrNull()?.let { QueueContext.Family(it.id) } ?: QueueContext.Games
    return TabMix(ids, context)
}

/**
 * The [selected] ones of [playlists] (in tab order), each in its own sort without greyed tracks,
 * a track in several playing once at its first place. Favorites when none of [selected] exists.
 * One playlist plays as that playlist. Null when there is nothing to play.
 */
fun Catalog.playlistsMix(selected: Set<Long>, playlists: List<Playlist>): TabMix? {
    val chosen = playlists.filter { it.id in selected }
        .ifEmpty { playlists.filter { it.isFavorites } }
    val ids = chosen.flatMap { playablePlaylistTrackIds(it) }.distinct()
    if (ids.isEmpty()) return null
    val context =
        chosen.singleOrNull()?.let { QueueContext.Playlist(it.id) } ?: QueueContext.Playlists
    return TabMix(ids, context)
}

/** The tabs whose long press plays. */
enum class PlayableTab { GAMES, PLAYLISTS, REMIXES }

/** Plays what a long press on a [PlayableTab] plays, shuffled when shuffle is on. */
@Singleton
class TabPlayer @Inject constructor(
    private val catalog: CatalogRepository,
    private val playlists: PlaylistRepository,
    private val sorts: SortSettings,
    private val hold: HoldSettings,
    private val playback: PlaybackController
) {
    suspend fun play(tab: PlayableTab) {
        val catalog = catalog.catalog.first()
        val mix = when (tab) {
            PlayableTab.GAMES -> catalog.gamesMix(hold.excludedFamilies.value, sorts.games.value)

            PlayableTab.PLAYLISTS -> {
                val all = playlists.playlists.first().mapNotNull {
                    playlists.playlist(it.id).first()
                }
                catalog.playlistsMix(hold.playlists.value, all)
            }

            PlayableTab.REMIXES -> catalog.playableUserTrackIds(sorts.remixes.value)
                .takeIf { it.isNotEmpty() }
                ?.let { TabMix(it, QueueContext.Remixes) }
        }
        mix?.let { playback.playAll(it.trackIds, it.context) }
    }
}
