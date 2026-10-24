package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayPauseShapeTest {
    // The app's Material Symbols pause and play_arrow, in their 24-unit box.
    private val pause = listOf(
        listOf(Offset(6.5f, 5f), Offset(9.5f, 5f), Offset(9.5f, 19f), Offset(6.5f, 19f)),
        listOf(Offset(14.5f, 5f), Offset(17.5f, 5f), Offset(17.5f, 19f), Offset(14.5f, 19f))
    )
    private val playCorners = listOf(Offset(8f, 5f), Offset(19f, 12f), Offset(8f, 19f))

    // A quarter turn clockwise about the box's center, as the icon turns while it morphs.
    private fun Offset.turned() = Offset(12f - (y - 12f), 12f + (x - 12f))

    private fun Offset.near(other: Offset) = abs(x - other.x) < 1e-3f && abs(y - other.y) < 1e-3f

    // Inside play's triangle: right of its back, under its top edge, over its bottom edge.
    private fun Offset.insidePlay(): Boolean {
        val slope = 7f / 11f
        return x >= 8f - 1e-3f && y >= 5f + slope * (x - 8f) - 1e-3f &&
            y <= 19f - slope * (x - 8f) + 1e-3f
    }

    @Test
    fun `at the start it is the pause icon`() {
        assertEquals(pause, PlayPauseShape.halves(0f))
    }

    @Test
    fun `at the end, turned a quarter, it is the play icon`() {
        val turned = PlayPauseShape.halves(1f).flatten().map { it.turned() }

        assertTrue(turned.all { it.insidePlay() })
        playCorners.forEach { corner -> assertTrue("$corner", turned.any { it.near(corner) }) }
    }

    // The halves reach past the center, so the gap between them closes before the slow end of
    // the animation and no seam shows across the middle.
    @Test
    fun `the two halves meet before the morph ends`() {
        val (left, right) = PlayPauseShape.halves(0.85f)

        assertTrue(left[1].x >= right[0].x)
        assertTrue(left[2].x >= right[3].x)
    }

    @Test
    fun `pause to play turns a quarter, and play to pause keeps turning the same way`() {
        assertEquals(90f, PlayPauseShape.turnTarget(turn = 0f, toPlay = true))
        assertEquals(180f, PlayPauseShape.turnTarget(turn = 90f, toPlay = false))
    }

    @Test
    fun `a tap mid-way turns back the way it came`() {
        assertEquals(0f, PlayPauseShape.turnTarget(turn = 40f, toPlay = false))
        assertEquals(90f, PlayPauseShape.turnTarget(turn = 130f, toPlay = true))
    }
}
