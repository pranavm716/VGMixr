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

/** The Search tab: search, and Settings. */
@Serializable data object SearchGraph

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

/** Remix [trackId]'s Link page: sets, changes or clears the catalog track it remixes. */
@Serializable data class LinkRoute(val trackId: String)

/** Settings, opened by a long press on the Search tab. */
@Serializable data object SettingsRoute

/** What a long press on a navbar tab does. */
sealed interface LongPress {
    /** Opens [route] inside the tab, over whatever it shows. */
    data class Open(val route: Any) : LongPress

    /** Plays the tab's tracks, as a held family or playlist does (chosen in Settings). */
    data object Play : LongPress
}

/**
 * The navbar's tabs, in bar order. The first entry is the start tab, so moving a tab is moving
 * its line. Each has a [longPress].
 */
enum class TopLevelDestination(
    val label: String,
    @param:DrawableRes val icon: Int,
    val route: Any,
    val longPress: LongPress
) {
    GAMES("Games", R.drawable.ic_games, GamesGraph, LongPress.Play),
    SEARCH("Search", R.drawable.ic_search, SearchGraph, LongPress.Open(SettingsRoute)),
    PLAYLISTS("Playlists", R.drawable.ic_library_music, PlaylistsGraph, LongPress.Play),
    REMIXES("Remixes", R.drawable.ic_equalizer, RemixesGraph, LongPress.Play);

    companion object {
        val start: TopLevelDestination get() = entries.first()
    }
}

/** Test tags for a tab's navbar item and its screen. */
val TopLevelDestination.navItemTag: String get() = "nav-$name"
val TopLevelDestination.screenTag: String get() = "screen-$name"
