package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.sort.SortDirection
import io.github.pranavm716.vgmixr.domain.sort.SortKey
import io.github.pranavm716.vgmixr.domain.sort.SortKeys
import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import io.github.pranavm716.vgmixr.ui.theme.DialogBackground
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

/** A collection's sort row: the [selected] sort and the [keys] its menu offers. */
data class SortUi(val selected: SortSetting, val keys: List<SortKey>)

/**
 * The sort row above a collection's first track, left-aligned as One UI's My Files has it: the
 * sort icon and the key's name, which opens a menu of keys anchored to it (the selected one in the
 * accent with a check), a thin divider, then an arrow, up for ascending, that reverses the
 * direction in one tap.
 */
@Composable
fun SortRow(
    sort: SortUi,
    onKey: (SortKey) -> Unit,
    onReverse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth().height(SortRowHeight).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var expanded by remember { mutableStateOf(false) }
        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.ic_sort),
                    contentDescription = null,
                    tint = muted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(sort.selected.key.label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted
                )
            }
            SortMenu(
                expanded,
                sort,
                onKey = {
                    expanded = false
                    onKey(it)
                },
                onDismiss = { expanded = false }
            )
        }
        // The arrow's 36 dp button pads its glyph by 8 dp and the key's text has 8 dp after it, so
        // the divider sits as far from the arrow as from the text (measured on the phone).
        VerticalDivider(Modifier.height(16.dp).padding(start = 4.dp), color = muted)
        val ascending = sort.selected.direction == SortDirection.Ascending
        // One up arrow that turns over, so reversing the direction animates.
        val turn by animateFloatAsState(
            if (ascending) 0f else HALF_TURN,
            tween(ARROW_TURN_MS),
            label = "sort arrow"
        )
        IconButton(onClick = onReverse, modifier = Modifier.size(36.dp)) {
            Icon(
                painterResource(R.drawable.ic_sort_up),
                contentDescription = stringResource(
                    if (ascending) R.string.sort_ascending else R.string.sort_descending
                ),
                tint = muted,
                modifier = Modifier.size(20.dp).rotate(turn)
            )
        }
    }
}

/** The keys, one item each; the selected one in the accent with a check at its end. */
@Composable
private fun SortMenu(
    expanded: Boolean,
    sort: SortUi,
    onKey: (SortKey) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = DialogBackground
    ) {
        sort.keys.forEach { key ->
            val selected = key == sort.selected.key
            val color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(key.label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = color
                    )
                },
                trailingIcon = {
                    // Every item keeps the check's room, so the menu is as wide whatever is picked.
                    Box(Modifier.size(24.dp)) {
                        if (selected) {
                            Icon(painterResource(R.drawable.ic_check), null, tint = color)
                        }
                    }
                },
                onClick = { onKey(key) },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

private val SortKey.label: Int
    get() = when (this) {
        SortKey.Order -> R.string.sort_order
        SortKey.Title -> R.string.sort_title
        SortKey.Game -> R.string.sort_game
        SortKey.RecentlyAdded -> R.string.sort_recently_added
        SortKey.Channel -> R.string.sort_channel
    }

/**
 * A new sort shows the list from its top. The sort the page opens with doesn't scroll, so coming
 * back to a page keeps where the user was.
 */
@Composable
fun ScrollToTopOnSortChange(sort: SortUi?, listState: LazyListState) {
    val selected = sort?.selected
    var shown by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected == shown) return@LaunchedEffect
        // Opening the page: its sort arrives with its first state.
        if (shown != null) listState.scrollToItem(0)
        shown = selected
    }
}

private const val HALF_TURN = 180f
private const val ARROW_TURN_MS = 250

/** The sort row's height, for scrolling past it. */
val SortRowHeight = 48.dp

/** The sort row's key in a lazy list; no track id looks like it. */
const val SORT_ROW_KEY = "sort"

@Preview
@Composable
private fun SortRowPreview() {
    VGMixrTheme {
        SortRow(SortUi(SortKeys.playlistDefault, SortKeys.playlist), onKey = {}, onReverse = {})
    }
}
