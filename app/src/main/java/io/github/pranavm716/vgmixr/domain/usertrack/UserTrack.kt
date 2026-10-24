package io.github.pranavm716.vgmixr.domain.usertrack

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * A track added from YouTube, mainly a fan remix: one video, or the part of it from [startMs] to
 * [endMs] (null: to its end). [title] and [channel] are the video's when it was added, and
 * [remixOf] the catalog track it remixes, if any.
 */
data class UserTrack(
    val videoId: String,
    val startMs: Long = 0,
    val endMs: Long? = null,
    val title: String,
    val channel: String,
    val durationMs: Long?,
    val addedAt: Instant,
    val remixOf: String? = null
) {
    val id: String get() = UserTrackId.of(videoId)
}

interface UserTrackRepository {
    /** Every user track, newest first. */
    val userTracks: Flow<List<UserTrack>>

    /**
     * Adds [track] as given, or returns false and changes nothing if its video is already added.
     * Adding a deleted track back as it was puts it back in its place.
     */
    suspend fun add(track: UserTrack): Boolean

    /** Sets the catalog track that user track [id] remixes, or clears it with null. */
    suspend fun setRemixOf(id: String, remixOf: String?)

    /** Deletes user track [id]. Its playlist entries and history stay, for if it's added again. */
    suspend fun delete(id: String)
}

/**
 * The track as the UI shows it and the player plays it: its video, under the title, family and
 * games of the [catalog] track it remixes. One that remixes nothing, or a track no longer in
 * [catalog], keeps its video's title and has no family or games.
 * [unplayable] is why its video is marked broken, if it is; [thumbnail] its thumbnail file's path.
 */
fun UserTrack.toCatalogTrack(
    catalog: Catalog,
    unplayable: BrokenReason?,
    thumbnail: String? = null
): CatalogTrack {
    val original = remixOf?.let(catalog::track)
    return CatalogTrack(
        id = id,
        familyId = original?.familyId,
        games = original?.games.orEmpty(),
        title = original?.title ?: title,
        order = 0,
        sources = listOf(Source.Stream(videoId, startMs, endMs)),
        unplayable = unplayable,
        channel = channel,
        remixOf = remixOf,
        thumbnail = thumbnail
    )
}
