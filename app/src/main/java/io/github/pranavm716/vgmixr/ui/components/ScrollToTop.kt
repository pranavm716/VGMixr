package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Scrolls [state] to the top each time [reselects] goes up (the tab tapped again on this screen),
 * but not when the screen first shows or comes back.
 */
@Composable
fun ScrollToTopOnReselect(reselects: Int, state: LazyListState) {
    var handled by rememberSaveable { mutableIntStateOf(reselects) }
    LaunchedEffect(reselects) {
        if (reselects != handled) {
            handled = reselects
            state.animateScrollToItem(0)
        }
    }
}
