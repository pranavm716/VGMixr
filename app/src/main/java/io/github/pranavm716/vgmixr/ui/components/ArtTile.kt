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

/**
 * Art's shape: 16:9, YouTube's thumbnail shape, which user tracks show and family covers and
 * playlist art take too.
 */
const val ART_ASPECT = 16f / 9f

/** A family's cover tile, [height] tall and 16:9 with rounded corners. Blank until covers come. */
@Composable
fun ArtTile(height: Dp, modifier: Modifier = Modifier) {
    ArtTile(modifier.size(width = height * ART_ASPECT, height = height))
}

/** The tile at whatever size [modifier] gives it, such as now playing's full-width one. */
@Composable
fun ArtTile(modifier: Modifier) {
    Box(modifier.background(ArtPlaceholder, ArtShape))
}

/** The Favorites playlist's tile, [height] tall and 16:9: the accent with a white heart. */
@Composable
fun FavoritesArt(height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = height * ART_ASPECT, height = height)
            .background(MaterialTheme.colorScheme.primary, ArtShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_favorite),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(height / 2)
        )
    }
}

private val ArtShape = RoundedCornerShape(6.dp)
