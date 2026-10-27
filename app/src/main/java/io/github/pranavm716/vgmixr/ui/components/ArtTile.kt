package io.github.pranavm716.vgmixr.ui.components

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.ui.theme.VGMixrColors
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
fun ArtTile(modifier: Modifier, familyId: String? = null, width: Dp = FullWidth) {
    AssetTile(modifier, familyId?.let(::coverAsset), width)
}

/** A playlist's tile, [height] tall: Favorites' heart, or the playlist's bundled art, or blank. */
@Composable
fun PlaylistArt(name: String, isFavorites: Boolean, height: Dp) {
    if (isFavorites) {
        FavoritesArt(height)
    } else {
        val width = height * ART_ASPECT
        AssetTile(Modifier.size(width = width, height = height), playlistArtAsset(name), width)
    }
}

/** The bundled image at [path] as a rounded tile, blank while it decodes or without one. */
@Composable
private fun AssetTile(modifier: Modifier, path: String?, width: Dp) {
    val art = rememberArt(path, width)
    if (art is Art.Loaded) {
        Image(art.image, null, modifier.clip(ArtShape), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(VGMixrColors.artPlaceholder, ArtShape))
    }
}

/** The full width of a phone, at which full-width art is decoded. */
val FullWidth = 360.dp

/**
 * A bundled image (a family's cover, a playlist's art): still decoding, decoded with its
 * [Loaded.average] color, or [Missing] when there's no such file.
 */
sealed interface Art {
    data object Loading : Art
    data object Missing : Art
    data class Loaded(val image: ImageBitmap, val average: Color) : Art
}

/** The bundled image at [path], decoded off the main thread at about [width]; missing for null. */
@Composable
fun rememberArt(path: String?, width: Dp): Art {
    val assets = LocalContext.current.assets
    val widthPx = with(LocalDensity.current) { width.roundToPx() }
    // Keyed, so a tile that changes family (the mini player's, now playing's) drops the old cover.
    val art = remember(path, widthPx) {
        mutableStateOf(
            when (path) {
                null -> Art.Missing
                else -> Images.cached(path, widthPx) ?: Art.Loading
            }
        )
    }
    LaunchedEffect(art, path) {
        if (art.value == Art.Loading && path != null) {
            art.value = withContext(Dispatchers.IO) { Images.load(assets, path, widthPx) }
        }
    }
    return art.value
}

/**
 * Decoded images, kept so a list doesn't decode one family's cover again for every row. They are
 * bundled, so a missing one stays missing until the app updates.
 */
private object Images {
    private val decoded = object : LruCache<String, Art.Loaded>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Art.Loaded) =
            value.image.width * value.image.height * 4
    }
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun cached(path: String, widthPx: Int): Art.Loaded? = decoded.get(key(path, widthPx))

    fun load(assets: AssetManager, path: String, widthPx: Int): Art {
        if (path in missing) return Art.Missing
        cached(path, widthPx)?.let { return it }
        val bitmap = try {
            decodeAsset(assets, path, widthPx)
        } catch (_: IOException) {
            null
        }
        if (bitmap == null) {
            missing += path
            return Art.Missing
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val art = Art.Loaded(bitmap.asImageBitmap(), Color(averageColor(pixels)))
        decoded.put(key(path, widthPx), art)
        return art
    }

    private fun key(path: String, widthPx: Int) = "$path@$widthPx"

    private const val CACHE_BYTES = 16 * 1024 * 1024
}

/** The pixels' (ARGB) average color, opaque: each channel's mean, ignoring alpha. */
internal fun averageColor(pixels: IntArray): Int {
    var red = 0L
    var green = 0L
    var blue = 0L
    for (pixel in pixels) {
        red += (pixel shr 16) and 0xFF
        green += (pixel shr 8) and 0xFF
        blue += pixel and 0xFF
    }
    val n = pixels.size.coerceAtLeast(1)
    return (0xFF shl 24) or ((red / n).toInt() shl 16) or ((green / n).toInt() shl 8) or
        (blue / n).toInt()
}

/** The image at [path] in [assets], scaled down by halves while it stays at least [widthPx] wide. */
private fun decodeAsset(assets: AssetManager, path: String, widthPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize =
            sampleSize(bounds.outWidth, widthPx)
    }
    return assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
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
    // Starts from the cache, so art moving to another spot (a swiped-in track becoming the
    // current one) shows at once instead of blanking while it decodes again. Keyed by the path,
    // so a new path never shows the old image.
    val key = path to widthPx
    var image by remember(key) { mutableStateOf(thumbnails[key]) }
    LaunchedEffect(key) {
        if (image == null) {
            image = withContext(Dispatchers.IO) { decodeThumbnail(path, widthPx) }
                ?.also { thumbnails.put(key, it) }
        }
    }
    val decoded = image
    if (decoded == null) {
        ArtTile(modifier)
    } else {
        Image(decoded, null, modifier.clip(ArtShape), contentScale = ContentScale.Crop)
    }
}

// Decoded thumbnails by path and width, up to an eighth of the app's memory.
private val thumbnails = SizedLruCache<Pair<String, Int>, ImageBitmap>(
    Runtime.getRuntime().maxMemory() / 8
) { it.asAndroidBitmap().allocationByteCount }

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

/**
 * Favorites' tile at a page header's full width: the accent with the white heart, sitting a little
 * high so it clears the name below.
 */
@Composable
fun FavoritesHeaderArt() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_favorite),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.offset(y = (-12).dp).size(80.dp)
        )
    }
}

private val ArtShape = RoundedCornerShape(6.dp)
