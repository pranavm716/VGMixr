package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * One line of text that scrolls when it is too wide, as Compose's `basicMarquee` does (a pause, then
 * a loop at a steady speed with a gap of a third of the width), with fading edges: the start edge
 * fades while the text is scrolled away from its start, and with [fadeEnd] the end edge fades
 * while text runs past it. Text that fits sits still, without fades.
 */
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fadeEnd: Boolean = true
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val textWidth = remember(text, style, measurer) {
            measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        }
        val available = constraints.maxWidth
        if (textWidth <= available) {
            Text(text, style = style, maxLines = 1, softWrap = false)
            return@BoxWithConstraints
        }
        val density = LocalDensity.current
        val distance = textWidth + available * GAP_FRACTION
        val durationMs = (distance / with(density) { SPEED_PER_SECOND.toPx() } * 1000).roundToInt()
        val offset = remember(text) { Animatable(0f) }
        var moving by remember(text) { mutableStateOf(false) }
        LaunchedEffect(text, distance) {
            offset.snapTo(0f)
            while (true) {
                delay(PAUSE_MS)
                moving = true
                offset.animateTo(distance, tween(durationMs, easing = LinearEasing))
                // The next copy now sits where the text started.
                moving = false
                offset.snapTo(0f)
            }
        }
        val startFade by animateFloatAsState(if (moving) 1f else 0f, tween(EDGE_FADE_MS))
        val fadePx = with(density) { FADE_WIDTH.toPx() }
        Text(
            text,
            style = style,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .clipToBounds()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    translate(left = -offset.value) { this@drawWithContent.drawContent() }
                    translate(left = distance - offset.value) {
                        this@drawWithContent.drawContent()
                    }
                    val edge = (fadePx / size.width).coerceAtMost(0.5f)
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 1f - startFade),
                            edge to Color.Black,
                            1f - edge to Color.Black,
                            1f to if (fadeEnd) Color.Transparent else Color.Black
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
                // Laid out at its full width but taking only the room there is; the drawing above
                // shows the part in view.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(maxWidth = Constraints.Infinity)
                    )
                    layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
                }
        )
    }
}

private const val PAUSE_MS = 1_200L
private const val GAP_FRACTION = 1f / 3f
private val SPEED_PER_SECOND = 30.dp
private val FADE_WIDTH = 24.dp
private const val EDGE_FADE_MS = 200
