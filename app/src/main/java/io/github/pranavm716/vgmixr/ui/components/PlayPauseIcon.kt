package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.pranavm716.vgmixr.R
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val MORPH_MS = 250

/**
 * Pause while [isPlaying], else play, morphing between them as Shuttle's does: the bars reshape
 * into a triangle while the icon turns a quarter clockwise, and keep turning the same way back.
 * A change mid-way runs back the way it came.
 */
@Composable
fun PlayPauseIcon(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    // 0 shows pause, 1 shows play; the turn is in degrees.
    val progress = remember { Animatable(if (isPlaying) 0f else 1f) }
    val turn = remember { Animatable(if (isPlaying) 0f else 90f) }
    LaunchedEffect(isPlaying) {
        val toPlay = !isPlaying
        val target = if (toPlay) 1f else 0f
        if (progress.value == target && progress.targetValue == target) return@LaunchedEffect
        val spec = tween<Float>(MORPH_MS, easing = FastOutSlowInEasing)
        coroutineScope {
            val turning =
                async { turn.animateTo(PlayPauseShape.turnTarget(turn.value, toPlay), spec) }
            progress.animateTo(target, spec)
            turning.await()
        }
        // Pause turned half way round looks the same; the next turn starts from 0.
        if (!toPlay) turn.snapTo(0f)
    }
    val description = stringResource(if (isPlaying) R.string.pause else R.string.play)
    Canvas(modifier.semantics { contentDescription = description }) {
        val box = size.minDimension / PlayPauseShape.BOX
        val path = Path()
        PlayPauseShape.halves(progress.value).forEach { corners ->
            path.moveTo(corners[0].x, corners[0].y)
            corners.drop(1).forEach { path.lineTo(it.x, it.y) }
            path.close()
        }
        scale(box, pivot = Offset.Zero) {
            rotate(turn.value, pivot = Offset(PlayPauseShape.BOX / 2, PlayPauseShape.BOX / 2)) {
                drawPath(path, tint)
            }
        }
    }
}

/**
 * The play/pause icon's geometry in Material's 24-unit box. Pause's two bars each reshape into
 * one half of play's triangle, drawn pointing up (play turned a quarter back), which the icon's
 * quarter turn brings to pointing right.
 */
internal object PlayPauseShape {
    const val BOX = 24f

    // Corners top-left, top-right, bottom-right, bottom-left, as pause's bars.
    private val pause = listOf(
        listOf(Offset(6.5f, 5f), Offset(9.5f, 5f), Offset(9.5f, 19f), Offset(6.5f, 19f)),
        listOf(Offset(14.5f, 5f), Offset(17.5f, 5f), Offset(17.5f, 19f), Offset(14.5f, 19f))
    )

    // Play pointing up: apex (12, 5), base (5, 16) to (19, 16), split down the middle. Each half
    // reaches half a unit past the middle, inside the triangle, so they overlap: the gap between
    // them closes partway through the morph, not at its slow end, and leaves no seam.
    private val playUp = listOf(
        listOf(Offset(12f, 5f), Offset(12.5f, 5.786f), Offset(12.5f, 16f), Offset(5f, 16f)),
        listOf(Offset(11.5f, 5.786f), Offset(12f, 5f), Offset(19f, 16f), Offset(11.5f, 16f))
    )

    /** The two halves' corners at [progress], 0 being pause and 1 play pointing up. */
    fun halves(progress: Float): List<List<Offset>> = pause.zip(playUp) { from, to ->
        from.zip(to) { a, b -> a + (b - a) * progress }
    }

    /**
     * Where the turn goes from [turn] degrees: pause to play turns 0 to 90, play to pause 90 to
     * 180 (the same way round). Mid-way, it goes back where it came from.
     */
    fun turnTarget(turn: Float, toPlay: Boolean): Float = when {
        toPlay -> 90f
        turn >= 90f -> 180f
        else -> 0f
    }
}
