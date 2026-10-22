package io.github.pranavm716.vgmixr.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.components.ArtTile
import io.github.pranavm716.vgmixr.ui.components.GameChips
import io.github.pranavm716.vgmixr.ui.components.SwipeToQueueOrRemove
import io.github.pranavm716.vgmixr.ui.components.SwipeToRemove
import io.github.pranavm716.vgmixr.ui.components.UnplayableIcon
import io.github.pranavm716.vgmixr.ui.components.unplayable
import kotlinx.coroutines.launch

/**
 * The queue, Spotify's two tiers: now playing, "Next in queue" (swipe left to remove a track, or
 * clear them all) and "Next from" the family (swipe right to queue a track, left to remove
 * it). Tapping a track plays it.
 */
@Composable
fun QueueScreen(onClose: () -> Unit, viewModel: QueueViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Playing a track from the list brings the list back to the top, where it now plays.
    val jump: (Int) -> Unit = { index ->
        viewModel.onJump(index)
        scope.launch { listState.animateScrollToItem(0) }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.back)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.queue), style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            state.nowPlaying?.let { row ->
                section(R.string.now_playing)
                item(key = "now-${row.index}") {
                    QueueRow(row, current = true, onClick = {})
                }
            }
            if (state.upNext.isNotEmpty()) {
                item(key = "upnext-header") {
                    SectionHeader(stringResource(R.string.next_in_queue)) {
                        TextButton(onClick = viewModel::onClear) {
                            Text(stringResource(R.string.clear_queue))
                        }
                    }
                }
                items(state.upNext, key = { "upnext-${it.index}-${it.trackId}" }) { row ->
                    SwipeToRemove(onRemove = { viewModel.onRemove(row.index) }) {
                        QueueRow(row, current = false, onClick = { jump(row.index) })
                    }
                }
            }
            if (state.nextFrom.isNotEmpty()) {
                item(key = "nextfrom-header") {
                    SectionHeader(
                        state.nextFromName?.let { stringResource(R.string.next_from, it) }
                            ?: stringResource(R.string.next_in_queue)
                    )
                }
                items(state.nextFrom, key = { "nextfrom-${it.index}-${it.trackId}" }) { row ->
                    SwipeToQueueOrRemove(
                        onQueue = { viewModel.onQueue(row.index) },
                        onRemove = { viewModel.onRemove(row.index) }
                    ) {
                        QueueRow(row, current = false, onClick = { jump(row.index) })
                    }
                }
            }
        }
    }
}

private fun LazyListScope.section(title: Int) {
    item(key = "header-$title") { SectionHeader(stringResource(title)) }
}

@Composable
private fun SectionHeader(title: String, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        action()
    }
}

/**
 * A queued track: its tile, title and game chips; the current one is tinted, and one whose sources
 * are all broken is greyed.
 */
@Composable
private fun QueueRow(row: QueueRowUi, current: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .then(if (current) Modifier.background(accent.copy(alpha = 0.12f)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val faint = Modifier.unplayable(row.unplayable)
        ArtTile(44.dp, faint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).then(faint)) {
            Text(
                row.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            GameChips(row.chips)
        }
        if (row.unplayable) {
            Spacer(Modifier.width(12.dp))
            UnplayableIcon()
        }
    }
}
