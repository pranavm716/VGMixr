package io.github.pranavm716.vgmixr.domain.catalog

import io.github.pranavm716.vgmixr.domain.model.Game
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import java.time.Instant

/**
 * The browsable catalog: non-retired games and tracks only. [games] are in manifest order, and
 * [families] in the order of each family's first game. [userTracks] are the tracks added from
 * YouTube: [track] finds them, but family browsing reads only [tracks].
 */
data class Catalog(
    val games: List<Game>,
    val families: List<Family>,
    val tracks: List<CatalogTrack>,
    val userTracks: List<CatalogTrack> = emptyList()
) {
    private val byId: Map<String, CatalogTrack> by lazy {
        (tracks + userTracks).associateBy { it.id }
    }

    /** The catalog or user track with [id], or null if there is none. */
    fun track(id: String): CatalogTrack? = byId[id]
}

/** A derived family. [name] and [label] come from its non-retired games, in manifest order. */
data class Family(val id: String, val name: String, val label: String)

/**
 * A track as the UI shows it. [familyId] is null only for a user track that remixes no catalog
 * track (a remix has its original's family and games). [games] are its chip games, in manifest
 * order. [unplayable] is set once every one of its stream sources is marked broken: the reason its
 * first source was marked. Only user tracks have a [channel] (the video's), a [remixOf] (the
 * catalog track they remix), a [thumbnail] (its file's path, once downloaded) and a [videoTitle]
 * (the video's own title, which [title] replaces with the original's while linked) and an [addedAt]
 * (when its link was pasted).
 */
data class CatalogTrack(
    val id: String,
    val familyId: String?,
    val games: List<Game>,
    val title: String,
    val order: Int,
    val sources: List<Source>,
    val unplayable: BrokenReason? = null,
    val channel: String? = null,
    val remixOf: String? = null,
    val thumbnail: String? = null,
    val videoTitle: String? = null,
    val addedAt: Instant? = null,
    /** No connection, and the track isn't on the phone (roadmap step 13). */
    val offline: Boolean = false
)

/** Whether the track is greyed and skipped: broken, or offline and not on the phone. */
val CatalogTrack.dimmed: Boolean get() = unplayable != null || offline
