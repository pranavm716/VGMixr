package io.github.pranavm716.vgmixr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import io.github.pranavm716.vgmixr.ui.family.FamilyTab
import io.github.pranavm716.vgmixr.ui.games.GamesTab
import io.github.pranavm716.vgmixr.ui.navigation.FamilyRoute
import io.github.pranavm716.vgmixr.ui.navigation.GamesGraph
import io.github.pranavm716.vgmixr.ui.navigation.GamesRoute
import io.github.pranavm716.vgmixr.ui.navigation.Tab2Route
import io.github.pranavm716.vgmixr.ui.navigation.Tab3Route
import io.github.pranavm716.vgmixr.ui.navigation.Tab4Route
import io.github.pranavm716.vgmixr.ui.navigation.TopLevelDestination
import io.github.pranavm716.vgmixr.ui.navigation.VGMixrNavBar
import io.github.pranavm716.vgmixr.ui.navigation.screenTag

/** Draws a tab's root screen; the controller lets it open screens inside its tab. */
typealias TabScreen = @Composable (TopLevelDestination, NavHostController) -> Unit

/**
 * The app shell: the tabs' screens above the navbar. [screen] draws a tab's root; it defaults to
 * placeholders so the shell runs without Hilt (tests), and MainActivity passes the real tabs.
 */
@Composable
fun VGMixrApp(
    navController: NavHostController = rememberNavController(),
    screen: TabScreen = { destination, _ -> PlaceholderScreen(destination.label) }
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selected = backStackEntry?.destination?.let { current ->
        TopLevelDestination.entries.firstOrNull { current.isIn(it) }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { VGMixrNavBar(selected = selected, onSelect = navController::navigateTo) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.start.route,
            modifier = Modifier.padding(padding)
        ) {
            // One entry per tab; the bar's order comes from TopLevelDestination, not from here.
            navigation<GamesGraph>(startDestination = GamesRoute) {
                composable<GamesRoute> { Tab(TopLevelDestination.GAMES, navController, screen) }
                composable<FamilyRoute> { FamilyTab() }
            }
            composable<Tab2Route> { Tab(TopLevelDestination.TAB_2, navController, screen) }
            composable<Tab3Route> { Tab(TopLevelDestination.TAB_3, navController, screen) }
            composable<Tab4Route> { Tab(TopLevelDestination.TAB_4, navController, screen) }
        }
    }
}

@Composable
private fun Tab(
    destination: TopLevelDestination,
    navController: NavHostController,
    screen: TabScreen
) {
    Box(Modifier.fillMaxSize().testTag(destination.screenTag)) {
        screen(destination, navController)
    }
}

/** The real root screen of each tab. */
@Composable
fun AppScreen(destination: TopLevelDestination, navController: NavHostController) {
    when (destination) {
        TopLevelDestination.GAMES ->
            GamesTab(onFamilyClick = { navController.navigate(FamilyRoute(it)) })

        else -> PlaceholderScreen(destination.label)
    }
}

private fun NavDestination.isIn(destination: TopLevelDestination): Boolean =
    hierarchy.any { it.hasRoute(destination.route::class) }

// Switching tabs keeps one back stack per tab: leaving saves it, coming back restores it.
private fun NavHostController.navigateTo(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
