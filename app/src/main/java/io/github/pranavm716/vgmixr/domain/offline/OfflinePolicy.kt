package io.github.pranavm716.vgmixr.domain.offline

import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.playback.playableStreams

/** How long a video stays on the phone. */
enum class OfflineKind {
    /** Kept while a playlist (or Remixes) holds one of its tracks; never evicted. */
    DOWNLOADED,

    /** One of the last [RECENT_LIMIT] tracks played, or the next in the queue; evicted. */
    CACHED
}

/**
 * A video that should be on the phone, for [trackIds]. Stored per video: a download is always the
 * whole video's audio (an album video's slices all play from it, clipped).
 */
data class OfflineWant(val videoId: String, val kind: OfflineKind, val trackIds: List<String>)

/** A video already on the phone, or on its way ([complete] false). */
data class OfflineEntry(
    val videoId: String,
    val kind: OfflineKind,
    val complete: Boolean,
    val trackIds: Set<String>
)

/**
 * What decides the downloads. [downloadOrder] is every track to keep, in fill order (Favorites,
 * Remixes, then the other playlists), repeats allowed; [recentlyPlayed] is play history, newest
 * first. [broken] holds the videos marked broken, and [hasLocal] tells a track with a playable
 * local file, which needs no download.
 */
data class OfflineInputs(
    val tracks: Map<String, CatalogTrack>,
    val downloadOrder: List<String>,
    val recentlyPlayed: List<String>,
    val next: String?,
    val broken: Set<String>,
    val hasLocal: (CatalogTrack) -> Boolean = { false }
)

/** What to change: entries to add or update (in fill order), and videos to delete. */
data class OfflinePlan(val upsert: List<OfflineWant>, val remove: List<String>)

const val RECENT_LIMIT = 200

/**
 * The videos that should be on the phone, in fill order: the next track (cached), playlist tracks
 * (downloaded; album videos after the rest, since each is a few hundred MB), then recent plays
 * (cached). A video wanted both ways is downloaded, at its first place.
 */
fun wantedOffline(inputs: OfflineInputs, existing: Map<String, OfflineEntry>): List<OfflineWant> {
    val albums = albumVideos(inputs.tracks.values)
    val recent = inputs.recentlyPlayed.distinct().take(RECENT_LIMIT)
    val kept = inputs.downloadOrder.distinct()
    val keptVideos = kept.mapNotNull { id -> videoFor(id, inputs, existing) }.toSet()

    val byVideo = LinkedHashMap<String, Pair<OfflineKind, MutableList<String>>>()
    fun add(trackId: String, kind: OfflineKind) {
        val videoId = videoFor(trackId, inputs, existing) ?: return
        val effective = if (videoId in keptVideos) OfflineKind.DOWNLOADED else kind
        val ids = byVideo.getOrPut(videoId) { effective to mutableListOf() }.second
        if (trackId !in ids) ids += trackId
    }
    inputs.next?.let { add(it, OfflineKind.CACHED) }
    kept.filter { videoFor(it, inputs, existing) !in albums }
        .forEach { add(it, OfflineKind.DOWNLOADED) }
    kept.filter { videoFor(it, inputs, existing) in albums }
        .forEach { add(it, OfflineKind.DOWNLOADED) }
    recent.forEach { add(it, OfflineKind.CACHED) }

    return byVideo.map { (videoId, value) -> OfflineWant(videoId, value.first, value.second) }
}

/**
 * The changes that bring [existing] to [wanted]. A video no longer wanted stays while one of its
 * tracks is still waiting for its new video, so it plays until the replacement is in.
 */
fun planOffline(wanted: List<OfflineWant>, existing: Map<String, OfflineEntry>): OfflinePlan {
    val upsert = wanted.filter { want ->
        val entry = existing[want.videoId]
        entry == null || !entry.complete || entry.kind != want.kind ||
            entry.trackIds != want.trackIds.toSet()
    }
    val waiting = wanted.filter { existing[it.videoId]?.complete != true }
        .flatMap { it.trackIds }
        .toSet()
    val wantedVideos = wanted.map { it.videoId }.toSet()
    val remove = existing.values
        .filter { it.videoId !in wantedVideos && it.trackIds.none { id -> id in waiting } }
        .map { it.videoId }
    return OfflinePlan(upsert, remove)
}

/** Videos that more than one track plays a slice of: full-album uploads. */
fun albumVideos(tracks: Collection<CatalogTrack>): Set<String> = tracks
    .flatMap { track -> playableStreams(track.sources).map { it.videoId }.distinct() }
    .groupingBy { it }
    .eachCount()
    .filterValues { it > 1 }
    .keys

/**
 * The video a track plays offline from: its first stream source that is already on the phone or
 * not marked broken (a download keeps playing after its video goes). Null for an unknown track, a
 * track with a playable local file, or one with every source broken.
 */
private fun videoFor(
    trackId: String,
    inputs: OfflineInputs,
    existing: Map<String, OfflineEntry>
): String? {
    val track = inputs.tracks[trackId] ?: return null
    if (inputs.hasLocal(track)) return null
    return playableStreams(track.sources)
        .firstOrNull { it.videoId in existing || it.videoId !in inputs.broken }
        ?.videoId
}
