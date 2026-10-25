package io.github.pranavm716.vgmixr.ui.navigation

import androidx.annotation.DrawableRes
import io.github.pranavm716.vgmixr.R
import kotlinx.serialization.Serializable

/** The Games tab: the family list, and a family's tracks opened from it. */
@Serializable data object GamesGraph

@Serializable data object GamesRoute

/**
 * A family's tracks. [fromNowPlaying] opens it from now playing's "Playing from": the page shows at
 * once under the collapsing sheet, without sliding in.
 */
@Serializable data class FamilyRoute(val familyId: String, val fromNowPlaying: Boolean = false)

/** The Search tab. */
@Serializable data object SearchRoute

/** The Playlists tab: the playlist list, and a playlist's tracks opened from it. */
@Serializable data object PlaylistsGraph

@Serializable data object PlaylistsRoute

/** A playlist's tracks. [fromNowPlaying] opens it at once, as [FamilyRoute]'s does. */
@Serializable data class PlaylistRoute(val playlistId: Long, val fromNowPlaying: Boolean = false)

/**
 * "Edit <playlist>": playlist [playlistId]'s tracks, and search to add more. [playlistName] is
 * the name the playlist page showed, so the title is whole before the playlist loads.
 */
@Serializable data class EditPlaylistRoute(val playlistId: Long, val playlistName: String = "")

/** The Remixes tab: tracks added from YouTube (a placeholder until step 13b), and Settings. */
@Serializable data object RemixesGraph

@Serializable data object RemixesRoute

/** Settings, opened by a long press on the Games tab. */
@Serializable data object SettingsRoute

/**
 * The navbar's tabs, in bar order. The first entry is the start tab, so moving a tab is moving
 * its line. A long press on a tab with a [longPressRoute] opens that page inside the tab.
 */
enum class TopLevelDestination(
    val label: String,
    @param:DrawableRes val icon: Int,
    val route: Any,
    val longPressRoute: Any? = null
) {
    GAMES("Games", R.drawable.ic_games, GamesGraph, longPressRoute = SettingsRoute),
    SEARCH("Search", R.drawable.ic_search, SearchRoute),
    PLAYLISTS("Playlists", R.drawable.ic_library_music, PlaylistsGraph),
    REMIXES("Remixes", R.drawable.ic_equalizer, RemixesGraph);

    companion object {
        val start: TopLevelDestination get() = entries.first()
    }
}

/** Test tags for a tab's navbar item and its screen. */
val TopLevelDestination.navItemTag: String get() = "nav-$name"
val TopLevelDestination.screenTag: String get() = "screen-$name"
