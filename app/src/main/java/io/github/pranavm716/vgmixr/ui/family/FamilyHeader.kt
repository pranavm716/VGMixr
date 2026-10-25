package io.github.pranavm716.vgmixr.ui.family

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import io.github.pranavm716.vgmixr.ui.components.ART_ASPECT
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.Cover
import io.github.pranavm716.vgmixr.ui.components.rememberCover
import io.github.pranavm716.vgmixr.ui.components.trackCountText
import io.github.pranavm716.vgmixr.ui.player.gameTint
import kotlin.math.roundToInt

/**
 * A family page's header: the family's whole 16:9 cover across the top of the screen, behind the
 * status bar, fading to the page's black behind the name and its track count. It shrinks as the
 * list scrolls (the [progress] lambda, 0 open to 1 shut, read only while drawing): from
 * [expandedHeight] to [collapsedHeight], a bar with the name, while the cover slides up at half
 * speed and fades out and the family's games' colors blend in. A family without a cover shows its
 * games' colors instead. Touches pass through to the list underneath.
 */
@Composable
fun FamilyHeader(
    name: String,
    trackCount: Int,
    familyId: String?,
    games: List<ChipUi>,
    expandedHeight: Dp,
    collapsedHeight: Dp,
    progress: () -> Float,
    modifier: Modifier = Modifier
) {
    val background = MaterialTheme.colorScheme.background
    val tint = remember(games) { gameTint(games) }
    val noCover = remember(games) { gameTint(games, NO_COVER_TINT) }
    val cover = rememberCover(familyId, expandedHeight * ART_ASPECT)
    Box(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val height = lerp(expandedHeight, collapsedHeight, progress()).roundToPx()
                val placeable = measurable.measure(
                    constraints.copy(minHeight = height, maxHeight = height)
                )
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            .clipToBounds()
            .drawBehind {
                drawRect(background)
                drawRect(tint, alpha = progress())
            }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(expandedHeight)
                .graphicsLayer {
                    val p = progress()
                    translationY = -(expandedHeight - collapsedHeight).toPx() * p / 2
                    alpha = 1 - p
                }
        ) {
            when (cover) {
                is Cover.Loaded ->
                    Image(
                        cover.image,
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                Cover.Missing -> Box(Modifier.fillMaxSize().background(noCover))

                Cover.Loading -> Unit
            }
        }
        Scrims(tint, progress, Modifier.matchParentSize())
        Label(name, trackCount, collapsedHeight, progress, Modifier.align(Alignment.BottomStart))
    }
}

/**
 * A shade at the top for the status bar's icons, and the fade at the bottom behind the name: to
 * the page's black while the header is open, blending into the games' [tint] as it shuts.
 */
@Composable
private fun Scrims(tint: Brush, progress: () -> Float, modifier: Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier.drawBehind {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = TOP_SHADE), Color.Transparent),
                    endY = TopShadeHeight.toPx()
                )
            )
            drawRect(Brush.verticalGradient(*fadeStops(background)))
        }
    )
    Box(
        modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawRect(tint, alpha = progress())
                drawRect(
                    Brush.verticalGradient(*fadeStops(Color.Black)),
                    blendMode = BlendMode.DstIn
                )
            }
    )
}

/** The bottom fade's stops: clear over the top third, then into [color] behind the name. */
private fun fadeStops(color: Color) = arrayOf(
    FADE_START to Color.Transparent,
    FADE_MIDDLE to color.copy(alpha = FADE_MIDDLE_ALPHA),
    1f to color
)

/**
 * The name over its track count. The name takes the largest size from 22 sp down to 18 sp that
 * fits it on one line (wrapping only if even 18 sp is too wide); as the header shuts it shrinks to
 * 18 sp and slides from the left to the middle of the bar while the count fades out.
 */
@Composable
private fun Label(
    name: String,
    trackCount: Int,
    collapsedHeight: Dp,
    progress: () -> Float,
    modifier: Modifier
) {
    val style = MaterialTheme.typography.titleLarge.copy(shadow = TextShadow)
    val measurer = rememberTextMeasurer()
    var nameWidth by remember { mutableIntStateOf(0) }
    var nameHeight by remember { mutableIntStateOf(0) }
    var countHeight by remember { mutableIntStateOf(0) }
    BoxWithConstraints(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    ) {
        val maxWidth = constraints.maxWidth.toFloat()
        val size = remember(name, style, maxWidth) {
            fitFontSize(
                widthAt = { sp ->
                    measurer.measure(
                        name,
                        style.copy(fontSize = sp.sp),
                        softWrap = false,
                        maxLines = 1
                    )
                        .size.width.toFloat()
                },
                maxWidth = maxWidth,
                largest = style.fontSize.value,
                smallest = BAR_TITLE_SIZE
            )
        }
        Column {
            Text(
                name,
                style = style.copy(fontSize = size.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .onSizeChanged {
                        nameWidth = it.width
                        nameHeight = it.height
                    }
                    .graphicsLayer {
                        val p = progress()
                        val scale = 1 + (BAR_TITLE_SIZE / size - 1) * p
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 1f)
                        // The name moves from the left, above the count, to the middle of the bar
                        // below the status bar.
                        translationX = p * (maxWidth - nameWidth * scale) / 2
                        val barBottom = (BarHeight.toPx() - nameHeight * scale) / 2
                        translationY = p * (countHeight + 12.dp.toPx() - barBottom)
                    }
            )
            Text(
                trackCountText(trackCount),
                style = MaterialTheme.typography.bodyMedium.copy(shadow = TextShadow),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .onSizeChanged { countHeight = it.height }
                    .graphicsLayer { alpha = 1 - (progress() * COUNT_FADE).coerceAtMost(1f) }
            )
        }
    }
}

/**
 * How far the header has shut, in px from 0 to [rangePx]: as far as the list's first row, the
 * spacer under the header, has scrolled, and all the way once that row is gone.
 */
internal fun headerScroll(firstIndex: Int, firstOffset: Int, rangePx: Int): Int =
    if (firstIndex > 0) rangePx else firstOffset.coerceAtMost(rangePx)

/**
 * The largest font size from [largest] down to [smallest], in half steps, at which a line is at
 * most [maxWidth] wide ([widthAt] measures it at a size); [smallest] if none fits.
 */
internal fun fitFontSize(
    widthAt: (Float) -> Float,
    maxWidth: Float,
    largest: Float,
    smallest: Float
): Float {
    var size = largest
    while (size > smallest && widthAt(size) > maxWidth) size -= FIT_STEP
    return size.coerceAtLeast(smallest)
}

/** The shut header's bar under the status bar. */
val BarHeight = 56.dp

private const val BAR_TITLE_SIZE = 18f
private const val FIT_STEP = 0.5f
private const val NO_COVER_TINT = 0.55f
private const val TOP_SHADE = 0.45f
private val TopShadeHeight = 56.dp
private const val FADE_START = 0.35f
private const val FADE_MIDDLE = 0.72f
private const val FADE_MIDDLE_ALPHA = 0.8f

// The count is gone by the time the header is about 60% shut.
private const val COUNT_FADE = 1.6f
private val TextShadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), blurRadius = 16f)
