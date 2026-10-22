package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

/**
 * A family's or playlist's page header: its [art], then its name over its track count, [gap]
 * apart so the name lines up with the rows below. A [background] (a family's game colors) runs up
 * behind the status bar; without one the page's own shows.
 */
@Composable
fun CollectionHeader(
    name: String,
    trackCount: Int,
    art: @Composable () -> Unit,
    gap: Dp,
    background: Brush? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (background != null) Modifier.background(background) else Modifier)
            .statusBarsPadding()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        art()
        Spacer(Modifier.width(gap))
        Column {
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                trackCountText(trackCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Opening a page whose track is playing shows that track mid-screen, once: coming back to the
 * screen keeps where the user scrolled. [playingIndex] is the track's row, [rowHeight] the rows'.
 */
@Composable
fun ScrollToPlayingOnce(listState: LazyListState, playingIndex: Int?, rowHeight: Dp) {
    var done by rememberSaveable { mutableStateOf(false) }
    val rowPx = with(LocalDensity.current) { rowHeight.roundToPx() }
    LaunchedEffect(Unit) {
        if (done) return@LaunchedEffect
        done = true
        val index = playingIndex ?: return@LaunchedEffect
        val viewport = snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
        listState.scrollToItem(index, scrollOffset = -(viewport - rowPx) / 2)
    }
}
