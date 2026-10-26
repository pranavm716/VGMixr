package io.github.pranavm716.vgmixr.domain.offline

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.history.Play
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How many of a collection's [total] tracks are on the phone. */
data class OfflineProgress(val done: Int, val total: Int) {
    val complete: Boolean get() = done >= total
}

/**
 * [tracks]' progress against [onPhone], the tracks that play offline. A broken track counts only
 * if it is on the phone, since it would never download otherwise.
 */
fun offlineProgress(tracks: List<CatalogTrack>, onPhone: Set<String>): OfflineProgress {
    val counted = tracks.filter { it.id in onPhone || it.unplayable == null }
    return OfflineProgress(counted.count { it.id in onPhone }, counted.size)
}

/** The tracks in [plays] (oldest first) played after [since]: newest first, each once. */
fun recentTrackIds(plays: List<Play>, since: Instant): List<String> = plays
    .asReversed()
    .filter { it.playedAt.isAfter(since) }
    .map { it.trackId }
    .distinct()

/** Where recent plays start counting: Settings' Clear recent moves it to now. */
interface RecentCutoff {
    val since: StateFlow<Instant>

    fun clearNow()

    companion object {
        val NONE: RecentCutoff = object : RecentCutoff {
            override val since: StateFlow<Instant> = MutableStateFlow(Instant.EPOCH)

            override fun clearNow() = Unit
        }
    }
}

/**
 * What the download notification calls a video: its track's title, or for a video several tracks
 * play (an album), its family's name and "album".
 */
fun downloadLabel(trackIds: Set<String>, catalog: Catalog): String {
    val tracks = trackIds.mapNotNull(catalog::track)
    return when {
        tracks.size == 1 -> tracks.single().title

        tracks.isEmpty() -> "a track"

        else -> {
            val family = catalog.families.firstOrNull { it.id == tracks.first().familyId }
            "${family?.name ?: tracks.first().title} album"
        }
    }
}
