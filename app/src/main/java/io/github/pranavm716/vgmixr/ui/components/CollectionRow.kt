package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.pranavm716.vgmixr.R

/**
 * A collection's row, as Shuttle's album list: its [art] tile, its name over its track count.
 * Families in the Games tab and playlists in the Playlists tab. Not clickable without [onClick].
 */
@Composable
fun CollectionRow(
    name: String,
    trackCount: Int,
    art: @Composable () -> Unit,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TileRowHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        art()
        Spacer(Modifier.width(RowTileGap))
        Column {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
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

/** "No tracks", "1 track", "12 tracks". */
@Composable
fun trackCountText(count: Int): String = if (count == 0) {
    stringResource(R.string.track_count_none)
} else {
    pluralStringResource(R.plurals.track_count, count, count)
}
