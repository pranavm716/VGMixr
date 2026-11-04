package io.github.pranavm716.vgmixr.ui.components

import kotlin.math.abs

/** How far a row moves toward one side. */
enum class SwipeLimit {
    /** No action on that side: the row doesn't move. */
    NONE,

    /** The row stops at the threshold (queue). */
    STOPS,

    /** The row follows the finger to its full width (remove, which slides off). */
    EXITS
}

/** The limits of a row's two sides: [start] for a swipe right, [end] for left. */
data class SwipeLimits(val start: SwipeLimit, val end: SwipeLimit)

/**
 * Follows one swipe on a row: where it is drawn ([offset]) and whether letting go acts. It arms at
 * [threshold] and disarms only once the row is [margin] back under it, so a finger rolling back on
 * lift, or wiggling at the threshold, neither drops the action nor ticks again. The row follows
 * the finger back at once, even after the finger went past a row's stop.
 */
class SwipeTracker(private val threshold: Float, private val margin: Float) {
    /** Whether letting go now runs the action of the side [offset] points to. */
    var armed = false
        private set

    /** Where the row is drawn, in px from its rest place (positive: moved right). */
    var offset = 0f
        private set

    /** Moves the finger by [delta] px on a row [width] px wide. True when this arms (tick). */
    fun drag(delta: Float, limits: SwipeLimits, width: Float): Boolean {
        // 0f - x, not -x: a side with no action gives 0, not -0.
        offset =
            (offset + delta).coerceIn(0f - reach(limits.end, width), reach(limits.start, width))
        val distance = abs(offset)
        val wasArmed = armed
        armed = if (armed) distance >= threshold - margin else distance >= threshold
        return armed && !wasArmed
    }

    fun reset() {
        offset = 0f
        armed = false
    }

    /** How far the row moves toward a side. */
    private fun reach(limit: SwipeLimit, width: Float) = when (limit) {
        SwipeLimit.NONE -> 0f
        SwipeLimit.STOPS -> threshold
        SwipeLimit.EXITS -> width
    }
}
