package io.github.pranavm716.vgmixr.ui.navigation

import androidx.annotation.DrawableRes
import io.github.pranavm716.vgmixr.R
import kotlinx.serialization.Serializable

@Serializable data object GamesRoute

@Serializable data object Tab2Route

@Serializable data object Tab3Route

@Serializable data object Tab4Route

/**
 * The navbar's tabs, in bar order. The first entry is the start tab, so moving a tab is moving
 * its line. Tabs 2-4 are placeholders until their content is decided.
 */
enum class TopLevelDestination(
    val label: String,
    @param:DrawableRes val icon: Int,
    val route: Any
) {
    GAMES("Games", R.drawable.ic_games, GamesRoute),
    TAB_2("Tab 2", R.drawable.ic_tab_circle, Tab2Route),
    TAB_3("Tab 3", R.drawable.ic_tab_triangle, Tab3Route),
    TAB_4("Tab 4", R.drawable.ic_tab_square, Tab4Route);

    companion object {
        val start: TopLevelDestination get() = entries.first()
    }
}

/** Test tags for a tab's navbar item and its screen. */
val TopLevelDestination.navItemTag: String get() = "nav-$name"
val TopLevelDestination.screenTag: String get() = "screen-$name"
