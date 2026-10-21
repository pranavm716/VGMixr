package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.pranavm716.vgmixr.ui.theme.ArtPlaceholder

/** A family's cover tile: square with rounded corners. A blank tile until families get covers. */
@Composable
fun ArtTile(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(ArtPlaceholder, RoundedCornerShape(6.dp)))
}
