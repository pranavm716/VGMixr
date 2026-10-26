package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first

/**
 * A family's or playlist's page: its rows ([content], [rowHeight] tall) under its header. The
 * header lies over the list, which starts with a spacer as tall as the open header, so the header
 * shuts as far as that spacer has scrolled. Opening the page shows the playing row
 * ([playingIndex] in [content]) mid-screen, once.
 */
@Composable
fun CollectionPage(
    name: String,
    trackCount: Int,
    barTint: Brush,
    playingIndex: Int?,
    rowHeight: Dp,
    art: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val expanded = maxWidth / ART_ASPECT
        val collapsed =
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + BarHeight
        val rangePx = with(LocalDensity.current) {
            (expanded - collapsed).roundToPx().coerceAtLeast(1)
        }
        val listState = rememberLazyListState()
        // The spacer is the list's first row, so the page's rows start at index 1.
        ScrollToPlayingOnce(listState, playingIndex?.plus(1), rowHeight)
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            item(key = HEADER_SPACER_KEY) { Spacer(Modifier.height(expanded)) }
            content()
        }
        CollectionHeader(
            name,
            trackCount,
            art,
            barTint,
            expandedHeight = expanded,
            collapsedHeight = collapsed,
            progress = {
                val scrolled = headerScroll(
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset,
                    rangePx
                )
                scrolled / rangePx.toFloat()
            },
            action = action
        )
    }
}

/**
 * The page's header: its 16:9 [art] across the top of the screen, behind the status bar, fading
 * to the page's black behind the name and its track count. It shrinks as the list scrolls (the
 * [progress] lambda, 0 open to 1 shut, read only while drawing): from [expandedHeight] to
 * [collapsedHeight], a bar with the name, while the art slides up at half speed and fades out and
 * [barTint] blends in. The [action] (a button) stays at the top right. Other touches pass through
 * to the list underneath.
 */
@Composable
private fun CollectionHeader(
    name: String,
    trackCount: Int,
    art: @Composable () -> Unit,
    barTint: Brush,
    expandedHeight: Dp,
    collapsedHeight: Dp,
    progress: () -> Float,
    action: (@Composable () -> Unit)?
) {
    val background = MaterialTheme.colorScheme.background
    Box(
        Modifier
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
                drawRect(barTint, alpha = progress())
            }
    ) {
        // The art keeps its open height while the header shrinks around it (the header clips it),
        // so it slides up without leaving a gap below it.
        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .height(expandedHeight)
                .graphicsLayer {
                    val p = progress()
                    translationY = -(expandedHeight - collapsedHeight).toPx() * p / 2
                    alpha = 1 - p
                }
        ) {
            art()
        }
        Scrims(barTint, progress, Modifier.matchParentSize())
        Label(name, trackCount, progress, Modifier.align(Alignment.BottomStart))
        if (action != null) {
            // Centered in the bar under the status bar, open or shut.
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 4.dp, end = 4.dp)
            ) {
                action()
            }
        }
    }
}

/**
 * Header art from a bundled image: the image itself, filling the header, or [missing] (a brush)
 * when there's no such image. Nothing while it decodes.
 */
@Composable
fun HeaderImage(art: Art, missing: Brush) {
    when (art) {
        is Art.Loaded ->
            Image(art.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

        Art.Missing -> Box(Modifier.fillMaxSize().background(missing))

        Art.Loading -> Unit
    }
}

/** The shut bar's tint for a page with nothing to take colors from: a faint lift off the black. */
val PlainBarTint: Brush = SolidColor(Color.White.copy(alpha = 0.04f))

/**
 * A shade at the top for the status bar's icons and the action, and the fade at the bottom behind
 * the name: to the page's black while the header is open, blending into [tint] as it shuts.
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
private fun Label(name: String, trackCount: Int, progress: () -> Float, modifier: Modifier) {
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

/**
 * Opening a page whose track is playing shows that track mid-screen, once: coming back to the
 * screen keeps where the user scrolled. [playingIndex] is the track's row, [rowHeight] the rows'.
 */
@Composable
fun ScrollToPlayingOnce(listState: LazyListState, playingIndex: Int?, rowHeight: Dp) {
    var done by rememberSaveable { mutableStateOf(false) }
    val rowPx = with(LocalDensity.current) { rowHeight.roundToPx() }
    LaunchedEffect(Unit) {
        if (done) return@LaunchedEffect
        done = true
        val index = playingIndex ?: return@LaunchedEffect
        listState.centerOn(index, rowPx)
    }
}

/** Scrolls so row [index], [rowPx] tall, sits mid-screen. */
suspend fun LazyListState.centerOn(index: Int, rowPx: Int) {
    val viewport = snapshotFlow { layoutInfo.viewportSize.height }.first { it > 0 }
    scrollToItem(index, scrollOffset = -(viewport - rowPx) / 2)
}

/** The shut header's bar under the status bar. */
private val BarHeight = 56.dp

// Rows are keyed by track id ("<family>-<slug>" or "yt:<videoId>"), so no row has this key.
private const val HEADER_SPACER_KEY = "header"

private const val BAR_TITLE_SIZE = 18f
private const val FIT_STEP = 0.5f
private const val TOP_SHADE = 0.45f
private val TopShadeHeight = 56.dp
private const val FADE_START = 0.35f
private const val FADE_MIDDLE = 0.72f
private const val FADE_MIDDLE_ALPHA = 0.8f

// The count is gone by the time the header is about 60% shut.
private const val COUNT_FADE = 1.6f
private val TextShadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), blurRadius = 16f)
