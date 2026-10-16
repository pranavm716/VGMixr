package io.github.pranavm716.vgmixr.domain.catalog

import io.github.pranavm716.vgmixr.domain.model.Category
import io.github.pranavm716.vgmixr.domain.model.Game
import io.github.pranavm716.vgmixr.domain.model.Source

/**
 * The browsable catalog: non-retired games and tracks only. [games] are in manifest order, and
 * [families] in the order of each family's first game.
 */
data class Catalog(
    val games: List<Game>,
    val families: List<Family>,
    val tracks: List<CatalogTrack>
)

/** A derived family. [name] and [label] come from its non-retired games, in manifest order. */
data class Family(val id: String, val name: String, val label: String)

/** A track as the UI shows it. [games] are its chip games, in manifest order. */
data class CatalogTrack(
    val id: String,
    val familyId: String,
    val games: List<Game>,
    val title: String,
    val category: Category,
    val order: Int,
    val sources: List<Source>
)
