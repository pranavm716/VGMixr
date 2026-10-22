package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Rows led by a family's cover tile share one size: families in the Games tab, tracks in the queue
 * and search, the mini player. A family's own track list has no tile and is shorter.
 */
val TileRowHeight = 64.dp

/** The cover tile in a [TileRowHeight] row. */
val RowTileSize = 44.dp

/** The gap between that tile and the row's text. */
val RowTileGap = 14.dp

/**
 * A track's title over its game chips, placed so the gap between the two splits a [RowTileSize]
 * tile beside them in half: the title's line is trimmed to its letters, and the block sits 1.5dp
 * up, as measured on the phone for the mini player. [marquee] scrolls a long title instead of
 * cutting it short, fading its start edge while it moves.
 */
@Composable
fun TrackTitleChips(
    title: String,
    chips: List<ChipUi>,
    modifier: Modifier = Modifier,
    marquee: Boolean = false
) {
    Column(modifier.offset(y = (-1.5).dp)) {
        val style = MaterialTheme.typography.titleMedium.trimmed()
        if (marquee) {
            // The mini player fades the end itself, before its button.
            MarqueeText(title, style, fadeEnd = false)
        } else {
            Text(title, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // The trim takes the room under the title's descenders too; give the chips some back.
        Spacer(Modifier.height(4.dp))
        GameChips(chips)
    }
}

/** The style with its line trimmed to the letters, so the text centers by its glyphs. */
private fun TextStyle.trimmed() = copy(
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

// The current track's tint: the accent over the background, faint as in Shuttle.
private const val CURRENT_TINT = 0.12f

/**
 * A track with its family's tile, the title over its game chips, as the queue and search list
 * them. [current] tints the playing track; [unplayable] greys a track whose sources are all broken.
 */
@Composable
fun TrackTileRow(
    title: String,
    chips: List<ChipUi>,
    unplayable: Boolean,
    current: Boolean,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .height(TileRowHeight)
            .then(if (current) Modifier.background(accent.copy(alpha = CURRENT_TINT)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val faint = Modifier.unplayable(unplayable)
        ArtTile(RowTileSize, faint)
        Spacer(Modifier.width(RowTileGap))
        TrackTitleChips(title, chips, Modifier.weight(1f).then(faint))
        if (unplayable) {
            Spacer(Modifier.width(12.dp))
            UnplayableIcon()
        }
    }
}
