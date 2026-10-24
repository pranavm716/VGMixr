package io.github.pranavm716.vgmixr.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.theme.ArtPlaceholder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/**
 * A user track's video thumbnail, [height] tall and 16:9: the image file at [path], decoded off the
 * main thread at about the size shown. Cropped to 16:9, which cuts the black bars off YouTube's
 * smaller (4:3) thumbnail sizes. The blank tile shows until it is decoded, or if it can't be.
 */
@Composable
fun ThumbnailTile(path: String, height: Dp, modifier: Modifier = Modifier) {
    val width = height * ART_ASPECT
    ThumbnailArt(path, modifier.size(width = width, height = height), width)
}

/**
 * A video thumbnail at whatever 16:9 size [modifier] gives it, such as now playing's full-width
 * one, decoded at about [width] (the full width of a phone by default).
 */
@Composable
fun ThumbnailArt(path: String, modifier: Modifier, width: Dp = 360.dp) {
    val widthPx = with(LocalDensity.current) { width.roundToPx() }
    val image by produceState<ImageBitmap?>(null, path, widthPx) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(path, widthPx) }
    }
    val decoded = image
    if (decoded == null) {
        ArtTile(modifier)
    } else {
        Image(decoded, null, modifier.clip(ArtShape), contentScale = ContentScale.Crop)
    }
}

/** The image at [path], scaled down by halves while it stays at least [widthPx] wide. */
private fun decodeThumbnail(path: String, widthPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= widthPx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
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
