package io.github.pranavm716.vgmixr.ui.player

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.components.ArtTile
import io.github.pranavm716.vgmixr.ui.components.GameChips

/** Stands in for the now-playing screen (step 9, part 2). Swipe down or go back to close it. */
@Composable
fun NowPlayingPlaceholder(onClose: () -> Unit, viewModel: MiniPlayerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val closeThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .draggable(
                state = rememberDraggableState { pull += it },
                orientation = Orientation.Vertical,
                onDragStarted = { pull = 0f },
                onDragStopped = { velocity ->
                    if (pull > closeThresholdPx || velocity > 1500f) onClose()
                    pull = 0f
                }
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.now_playing), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(32.dp))
        ArtTile(240.dp)
        (state as? MiniPlayerUiState.Visible)?.let {
            Spacer(Modifier.height(24.dp))
            Text(
                it.title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            GameChips(it.chips)
        }
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.now_playing_coming_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
