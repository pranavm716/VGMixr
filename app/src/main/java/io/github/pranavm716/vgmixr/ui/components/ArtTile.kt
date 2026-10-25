package io.github.pranavm716.vgmixr.ui.components

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.theme.ArtPlaceholder
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Art's shape: 16:9, YouTube's thumbnail shape, which user tracks show and family covers and
 * playlist art take too.
 */
const val ART_ASPECT = 16f / 9f

/**
 * A family's cover tile, [height] tall and 16:9 with rounded corners: [familyId]'s cover, or blank
 * without one.
 */
@Composable
fun ArtTile(height: Dp, modifier: Modifier = Modifier, familyId: String? = null) {
    val width = height * ART_ASPECT
    ArtTile(modifier.size(width = width, height = height), familyId, width)
}

/**
 * The tile at whatever size [modifier] gives it, such as now playing's full-width one, with the
 * cover decoded at about [width] (the full width of a phone by default). Blank while it decodes.
 */
@Composable
fun ArtTile(modifier: Modifier, familyId: String? = null, width: Dp = 360.dp) {
    val cover = rememberCover(familyId, width)
    if (cover is Cover.Loaded) {
        Image(cover.image, null, modifier.clip(ArtShape), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(ArtPlaceholder, ArtShape))
    }
}

/** A family's cover: still decoding, decoded, or [Missing] for a family without one. */
sealed interface Cover {
    data object Loading : Cover
    data object Missing : Cover
    data class Loaded(val image: ImageBitmap) : Cover
}

/** [familyId]'s cover, decoded off the main thread at about [width]; [Cover.Missing] for null. */
@Composable
fun rememberCover(familyId: String?, width: Dp): Cover {
    val assets = LocalContext.current.assets
    val widthPx = with(LocalDensity.current) { width.roundToPx() }
    // Keyed, so a tile that changes family (the mini player's, now playing's) drops the old cover.
    val cover = remember(familyId, widthPx) {
        mutableStateOf(
            when {
                familyId == null -> Cover.Missing
                else -> Covers.cached(familyId, widthPx)?.let(Cover::Loaded) ?: Cover.Loading
            }
        )
    }
    LaunchedEffect(cover, familyId) {
        if (cover.value == Cover.Loading && familyId != null) {
            val image = withContext(Dispatchers.IO) { Covers.load(assets, familyId, widthPx) }
            cover.value = image?.let(Cover::Loaded) ?: Cover.Missing
        }
    }
    return cover.value
}

/**
 * Decoded covers, kept so a list doesn't decode one family's cover again for every row. The covers
 * are bundled, so a family without one stays without one until the app updates.
 */
private object Covers {
    private val decoded = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun cached(familyId: String, widthPx: Int): ImageBitmap? = decoded.get(key(familyId, widthPx))

    fun load(assets: AssetManager, familyId: String, widthPx: Int): ImageBitmap? {
        if (familyId in missing) return null
        cached(familyId, widthPx)?.let { return it }
        val image = try {
            decodeCover(assets, coverAsset(familyId), widthPx)
        } catch (_: IOException) {
            null
        }
        if (image == null) missing += familyId else decoded.put(key(familyId, widthPx), image)
        return image
    }

    private fun key(familyId: String, widthPx: Int) = "$familyId@$widthPx"

    private const val CACHE_BYTES = 16 * 1024 * 1024
}

/** The cover at [path] in [assets], scaled down by halves while it stays at least [widthPx] wide. */
private fun decodeCover(assets: AssetManager, path: String, widthPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize =
            sampleSize(bounds.outWidth, widthPx)
    }
    return assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
}

private fun sampleSize(imageWidth: Int, widthPx: Int): Int {
    var sample = 1
    while (imageWidth / (sample * 2) >= widthPx) sample *= 2
    return sample
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
    val options = BitmapFactory.Options().apply {
        inSampleSize =
            sampleSize(bounds.outWidth, widthPx)
    }
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
