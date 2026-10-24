package io.github.pranavm716.vgmixr.domain.catalog

import io.github.pranavm716.vgmixr.domain.model.Game
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason

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
 * A track as the UI shows it. [games] are its chip games, in manifest order. [unplayable] is set
 * once every one of its stream sources is marked broken: the reason its first source was marked.
 */
data class CatalogTrack(
    val id: String,
    val familyId: String,
    val games: List<Game>,
    val title: String,
    val order: Int,
    val sources: List<Source>,
    val unplayable: BrokenReason? = null
)
