package io.github.pranavm716.vgmixr.playback

import android.content.Context
import androidx.media3.session.CommandButton
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaButtonsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `shuffle sits at the far left and favorite at the far right`() {
        val (shuffle, favorite) = mediaButtons(context, shuffle = false, favorite = false)

        assertEquals(CommandButton.SLOT_BACK_SECONDARY, shuffle.slots[0])
        assertEquals(TOGGLE_SHUFFLE, shuffle.sessionCommand?.customAction)
        assertEquals(CommandButton.SLOT_FORWARD_SECONDARY, favorite.slots[0])
        assertEquals(TOGGLE_FAVORITE, favorite.sessionCommand?.customAction)
    }

    @Test
    fun `both fall back to custom actions, which Android's own media controls show`() {
        // Media3 hands the platform session only buttons that can go in the overflow slot.
        mediaButtons(context, shuffle = false, favorite = false).forEach {
            assertEquals(true, it.slots.contains(CommandButton.SLOT_OVERFLOW))
        }
    }

    @Test
    fun `each button shows its state`() {
        val (shuffleOff, notFavorite) = mediaButtons(context, shuffle = false, favorite = false)
        val (shuffleOn, isFavorite) = mediaButtons(context, shuffle = true, favorite = true)

        assertEquals(CommandButton.ICON_SHUFFLE_OFF, shuffleOff.icon)
        assertEquals(CommandButton.ICON_SHUFFLE_ON, shuffleOn.icon)
        assertEquals(CommandButton.ICON_HEART_UNFILLED, notFavorite.icon)
        assertEquals(CommandButton.ICON_HEART_FILLED, isFavorite.icon)
        assertEquals("Shuffle on", shuffleOn.displayName)
        assertEquals("Remove from Favorites", isFavorite.displayName)
    }
}
