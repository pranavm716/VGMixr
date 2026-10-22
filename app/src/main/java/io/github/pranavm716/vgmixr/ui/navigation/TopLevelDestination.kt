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

@Serializable data object Tab3Route

@Serializable data object Tab4Route

/**
 * The navbar's tabs, in bar order. The first entry is the start tab, so moving a tab is moving
 * its line. Tabs 3 and 4 are placeholders until their content is built.
 */
enum class TopLevelDestination(
    val label: String,
    @param:DrawableRes val icon: Int,
    val route: Any
) {
    GAMES("Games", R.drawable.ic_games, GamesGraph),
    SEARCH("Search", R.drawable.ic_search, SearchRoute),
    TAB_3("Tab 3", R.drawable.ic_tab_triangle, Tab3Route),
    TAB_4("Tab 4", R.drawable.ic_tab_square, Tab4Route);

    companion object {
        val start: TopLevelDestination get() = entries.first()
    }
}

/** Test tags for a tab's navbar item and its screen. */
val TopLevelDestination.navItemTag: String get() = "nav-$name"
val TopLevelDestination.screenTag: String get() = "screen-$name"
