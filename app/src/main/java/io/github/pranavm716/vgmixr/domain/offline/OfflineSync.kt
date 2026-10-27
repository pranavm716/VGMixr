package io.github.pranavm716.vgmixr.domain.offline

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import io.github.pranavm716.vgmixr.domain.sort.playlistTracks
import io.github.pranavm716.vgmixr.domain.sort.sortedUserTracks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/** Fetches and deletes the audio of videos; the work runs elsewhere, one video at a time. */
interface OfflineDownloader {
    /** Downloads [videoIds] in this order (one already done or under way is left as it is). */
    fun download(videoIds: List<String>)

    /** Deletes [videoIds]' audio, also what playback stored of them, and stops their downloads. */
    fun remove(videoIds: List<String>)
}

/**
 * Every track to keep downloaded, in fill order: Favorites, Remixes (in [remixSort]), then the
 * other [playlists] in their order on the Playlists tab; each list in its own sort.
 */
fun downloadOrder(
    catalog: Catalog,
    playlists: List<Playlist>,
    remixSort: SortSetting
): List<String> {
    val (favorites, others) = playlists.partition { it.isFavorites }
    return (
        favorites.flatMap { catalog.playlistTracks(it) } +
            catalog.sortedUserTracks(remixSort) +
            others.flatMap { catalog.playlistTracks(it) }
        ).filterNot { it.retired }.map { it.id }
}

/**
 * Keeps the phone's videos in line with the playlists, Remixes, recent plays, the next track and
 * broken marks: whenever one of
 * them (or a finished download) changes, it works out the plan, records it in [store] and hands the
 * downloads and deletions to [downloader].
 */
class OfflineSync(
    private val catalog: Flow<Catalog>,
    private val playlists: Flow<List<Playlist>>,
    private val remixSort: Flow<SortSetting>,
    private val brokenVideos: Flow<Set<String>>,
    private val store: OfflineStore,
    private val downloader: OfflineDownloader,
    /** Play history, newest first, repeats allowed. */
    private val recentlyPlayed: Flow<List<String>> = flowOf(emptyList()),
    /** The track after the playing one in the queue, prefetched whole. */
    private val next: Flow<String?> = flowOf(null)
) {
    suspend fun run() {
        val kept =
            combine(catalog, playlists, remixSort, brokenVideos, store.onPhone) { c, p, s, b, _ ->
                OfflineInputs(
                    tracks = (c.tracks + c.userTracks).associateBy { it.id },
                    downloadOrder = downloadOrder(c, p, s),
                    recentlyPlayed = emptyList(),
                    next = null,
                    broken = b
                )
            }
        combine(kept, recentlyPlayed, next) { inputs, recent, nextId ->
            inputs.copy(recentlyPlayed = recent, next = nextId)
        }.collectLatest { inputs ->
            val existing = store.entries.first()
            val wanted = wantedOffline(inputs, existing)
            val plan = planOffline(wanted, existing)
            store.apply(plan)
            if (plan.remove.isNotEmpty()) downloader.remove(plan.remove)
            val pending = store.entries.first().values.filter {
                !it.complete
            }.map { it.videoId }.toSet()
            val order = wanted.map { it.videoId }.filter { it in pending }
            if (order.isNotEmpty()) downloader.download(order)
        }
    }
}
