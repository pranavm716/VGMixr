package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.theme.ArtPlaceholder

/** A family's cover tile: square with rounded corners. A blank tile until families get covers. */
@Composable
fun ArtTile(size: Dp, modifier: Modifier = Modifier) {
    ArtTile(modifier.size(size))
}

/** The tile at whatever size [modifier] gives it, such as now playing's large square. */
@Composable
fun ArtTile(modifier: Modifier) {
    Box(modifier.background(ArtPlaceholder, ArtShape))
}

/** The Favorites playlist's tile: the accent with a white heart, half the tile's size. */
@Composable
fun FavoritesArt(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(MaterialTheme.colorScheme.primary, ArtShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_favorite),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size / 2)
        )
    }
}

private val ArtShape = RoundedCornerShape(6.dp)
