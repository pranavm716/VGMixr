package io.github.pranavm716.vgmixr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.pranavm716.vgmixr.ui.games.GamesTab
import io.github.pranavm716.vgmixr.ui.navigation.FamilyRoute
import io.github.pranavm716.vgmixr.ui.navigation.GamesGraph
import io.github.pranavm716.vgmixr.ui.navigation.GamesRoute
import io.github.pranavm716.vgmixr.ui.navigation.PlaylistsRoute
import io.github.pranavm716.vgmixr.ui.navigation.SearchRoute
import io.github.pranavm716.vgmixr.ui.navigation.Tab4Route
import io.github.pranavm716.vgmixr.ui.navigation.TopLevelDestination
import io.github.pranavm716.vgmixr.ui.navigation.VGMixrNavBar
import io.github.pranavm716.vgmixr.ui.navigation.screenTag
import io.github.pranavm716.vgmixr.ui.player.NowPlayingSheet
import io.github.pranavm716.vgmixr.ui.player.NowPlayingSheetState
import io.github.pranavm716.vgmixr.ui.playlists.PlaylistsTab
import io.github.pranavm716.vgmixr.ui.search.SearchTab

/**
 * Draws a tab's root screen; the controller lets it open screens inside its tab. The count goes
 * up each time the tab is tapped while its root screen shows (Games then scrolls to the top, Search
 * focuses its field).
 */
typealias TabScreen = @Composable (TopLevelDestination, NavHostController, reselects: Int) -> Unit

/** Test tag of the family track screen. */
const val FAMILY_SCREEN_TAG = "screen-family"

/**
 * The app shell: the tabs' screens above the navbar. [screen] draws a tab's root and
 * [familyScreen] a family's tracks; both default to placeholders so the shell runs without Hilt
 * (tests), and MainActivity passes the real screens.
 */
@Composable
fun VGMixrApp(
    navController: NavHostController = rememberNavController(),
    screen: TabScreen = { destination, _, _ -> PlaceholderScreen(destination.label) },
    familyScreen: @Composable () -> Unit = { PlaceholderScreen("Family") },
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
    miniPlayer: @Composable (
        NowPlayingSheetState,
        onOpenNowPlaying: () -> Unit
    ) -> Unit = { _, _ -> },
    miniPlayerInSheet: @Composable () -> Unit = {},
    nowPlayingHeader: @Composable (
        onClose: () -> Unit,
        onOpenFamily: (familyId: String) -> Unit
    ) -> Unit = { _, _ -> },
    nowPlaying: @Composable (
        onOpenQueue: () -> Unit
    ) -> Unit = { PlaceholderScreen("Now playing") },
    queue: @Composable (onClose: () -> Unit) -> Unit = { PlaceholderScreen("Queue") },
    nowPlayingOpen: MutableState<Boolean> = rememberSaveable { mutableStateOf(false) }
) {
    val openNowPlaying = { nowPlayingOpen.value = true }
    val scope = rememberCoroutineScope()
    // The sheet grows out of the mini player; a drag that settles updates the open flag, and a
    // change of the flag (a tap, a track, the notification, back) animates the sheet.
    val sheet = remember { NowPlayingSheetState(scope) { nowPlayingOpen.value = it } }
    LaunchedEffect(nowPlayingOpen.value) { sheet.animateTo(nowPlayingOpen.value) }
    // The queue opens over now playing and closes with it.
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(nowPlayingOpen.value) { if (!nowPlayingOpen.value) queueOpen = false }
    val reselects = remember { mutableStateMapOf<TopLevelDestination, Int>() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selected = backStackEntry?.destination?.let { current ->
        TopLevelDestination.entries.firstOrNull { current.isIn(it) }
    }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // Screens reach up behind the status bar and pad themselves, so a screen's header can
            // tint it.
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(
                WindowInsets.statusBars
            ),
            // The action (Try again) in the app's accent rather than Material's inverse primary.
            snackbarHost = {
                SnackbarHost(snackbars) {
                    Snackbar(it, actionColor = MaterialTheme.colorScheme.primary)
                }
            },
            bottomBar = {
                Column {
                    miniPlayer(sheet, openNowPlaying)
                    VGMixrNavBar(
                        // Slides down out of view as now playing opens.
                        modifier = Modifier
                            .onSizeChanged { sheet.navBarHeightPx = it.height.toFloat() }
                            .graphicsLayer {
                                translationY = sheet.navBarHeightPx * sheet.expansion
                            },
                        selected = selected,
                        onSelect = { tab ->
                            if (tab == selected) {
                                // Inside a tab, a tap goes back to its first screen; on that
                                // screen, the screen reacts (scrolls to the top, focuses search).
                                if (navController.isAtRoot(tab)) {
                                    reselects[tab] = (reselects[tab] ?: 0) + 1
                                } else {
                                    navController.popToRoot(tab)
                                }
                            } else {
                                navController.navigateTo(tab)
                            }
                        }
                    )
                }
            }
        ) { padding ->
            AppNavHost(
                navController,
                screen,
                familyScreen,
                { reselects[it] ?: 0 },
                Modifier.padding(padding)
            )
        }
        // Now playing grows out of the mini player over everything, the navbar included.
        NowPlayingSheet(
            sheet,
            collapsed = miniPlayerInSheet,
            header = {
                nowPlayingHeader(
                    { nowPlayingOpen.value = false },
                    { familyId ->
                        // Closes the sheet and opens the family in the Games tab, replacing any
                        // family already open there; it scrolls to the playing track on opening.
                        nowPlayingOpen.value = false
                        navController.navigate(FamilyRoute(familyId, fromNowPlaying = true)) {
                            popUpTo<GamesRoute>()
                        }
                    }
                )
            },
            content = { nowPlaying { queueOpen = true } }
        )
        AnimatedVisibility(
            visible = queueOpen,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it }
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                BackHandler { queueOpen = false }
                queue { queueOpen = false }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    screen: TabScreen,
    familyScreen: @Composable () -> Unit,
    reselects: (TopLevelDestination) -> Int,
    modifier: Modifier
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.start.route,
        modifier = modifier,
        enterTransition = {
            when {
                targetState.opensAtOnce() -> EnterTransition.None
                withinTab() -> slideIn(SlideDirection.Start)
                else -> tabFadeIn
            }
        },
        exitTransition = {
            when {
                targetState.opensAtOnce() -> ExitTransition.None
                withinTab() -> slideOut(SlideDirection.Start, parallax = true)
                else -> tabFadeOut
            }
        },
        popEnterTransition = {
            if (withinTab()) slideIn(SlideDirection.End, parallax = true) else tabFadeIn
        },
        popExitTransition = { if (withinTab()) slideOut(SlideDirection.End) else tabFadeOut },
        // The back gesture plays its own pair (by default the leaving screen shrinks and
        // fades); use the same slide, which then follows the finger.
        predictivePopEnterTransition = {
            if (withinTab()) slideIn(SlideDirection.End, parallax = true) else tabFadeIn
        },
        predictivePopExitTransition = {
            if (withinTab()) slideOut(SlideDirection.End) else tabFadeOut
        }
    ) {
        // One entry per tab; the bar's order comes from TopLevelDestination, not from here.
        navigation<GamesGraph>(startDestination = GamesRoute) {
            composable<GamesRoute> {
                Tab(TopLevelDestination.GAMES, navController, screen, reselects)
            }
            // The family screen pads below the status bar itself, under its tinted header.
            composable<FamilyRoute> {
                Box(Modifier.screen().testTag(FAMILY_SCREEN_TAG)) {
                    familyScreen()
                }
            }
        }
        composable<SearchRoute> {
            Tab(TopLevelDestination.SEARCH, navController, screen, reselects)
        }
        composable<PlaylistsRoute> {
            Tab(TopLevelDestination.PLAYLISTS, navController, screen, reselects)
        }
        composable<Tab4Route> { Tab(TopLevelDestination.TAB_4, navController, screen, reselects) }
    }
}

@Composable
private fun Tab(
    destination: TopLevelDestination,
    navController: NavHostController,
    screen: TabScreen,
    reselects: (TopLevelDestination) -> Int
) {
    Box(Modifier.screen().statusBarsPadding().testTag(destination.screenTag)) {
        screen(destination, navController, reselects(destination))
    }
}

/** The real root screen of each tab. */
@Composable
fun AppScreen(destination: TopLevelDestination, navController: NavHostController, reselects: Int) {
    when (destination) {
        TopLevelDestination.GAMES ->
            GamesTab(onFamilyClick = { navController.navigate(FamilyRoute(it)) }, reselects)

        TopLevelDestination.SEARCH -> SearchTab(reselects)

        TopLevelDestination.PLAYLISTS -> PlaylistsTab(reselects)

        else -> PlaceholderScreen(destination.label)
    }
}

// Screens are opaque, so one sliding over another hides it.
@Composable
private fun Modifier.screen() = fillMaxSize().background(MaterialTheme.colorScheme.background)

private const val SLIDE_MS = 400
private const val FADE_MS = 150

private val tabFadeIn = fadeIn(tween(FADE_MS))
private val tabFadeOut = fadeOut(tween(FADE_MS))

// A family opened from now playing shows at once under the collapsing sheet; leaving it slides as
// usual.
private fun NavBackStackEntry.opensAtOnce(): Boolean =
    destination.hasRoute<FamilyRoute>() && toRoute<FamilyRoute>().fromNowPlaying

private fun NavDestination.tab(): TopLevelDestination? =
    TopLevelDestination.entries.firstOrNull { isIn(it) }

// Screens opened inside a tab slide like pages (the back gesture drags the top one off to the
// right, revealing the one below); switching tabs only fades.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.withinTab(): Boolean =
    initialState.destination.tab() == targetState.destination.tab()

// The screen underneath moves a quarter of the way, so it reads as being behind the moving one.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideIn(
    direction: SlideDirection,
    parallax: Boolean = false
): EnterTransition = slideIntoContainer(direction, tween(SLIDE_MS)) { if (parallax) it / 4 else it }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideOut(
    direction: SlideDirection,
    parallax: Boolean = false
): ExitTransition = slideOutOfContainer(direction, tween(SLIDE_MS)) { if (parallax) it / 4 else it }

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

// Tapping the selected tab goes back to its first screen.
private fun NavHostController.popToRoot(destination: TopLevelDestination) {
    rootOf(destination)?.let { popBackStack(it.id, inclusive = false) }
}

private fun NavHostController.isAtRoot(destination: TopLevelDestination): Boolean =
    currentDestination?.id != null && currentDestination?.id == rootOf(destination)?.id

private fun NavHostController.rootOf(destination: TopLevelDestination): NavDestination? {
    val tab = graph.findNode(destination.route)
    return if (tab is NavGraph) tab.findStartDestination() else tab
}
