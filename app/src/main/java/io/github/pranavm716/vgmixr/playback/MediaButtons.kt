package io.github.pranavm716.vgmixr.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import io.github.pranavm716.vgmixr.R

const val TOGGLE_SHUFFLE = "io.github.pranavm716.vgmixr.TOGGLE_SHUFFLE"
const val TOGGLE_FAVORITE = "io.github.pranavm716.vgmixr.TOGGLE_FAVORITE"

val toggleShuffle = SessionCommand(TOGGLE_SHUFFLE, Bundle.EMPTY)
val toggleFavorite = SessionCommand(TOGGLE_FAVORITE, Bundle.EMPTY)

/**
 * The media notification's extra buttons, each showing its state: shuffle at the far left and
 * favorite (the current track) at the far right, around previous, play/pause and next. Media3
 * passes only overflow buttons on to Android's own media session (the notification and lock
 * screen on Android 13+), so each falls back to it; there the system places them.
 */
fun mediaButtons(context: Context, shuffle: Boolean, favorite: Boolean): List<CommandButton> =
    listOf(
        CommandButton.Builder(
            if (shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        )
            .setDisplayName(
                context.getString(if (shuffle) R.string.shuffle_on else R.string.shuffle_off)
            )
            .setSessionCommand(toggleShuffle)
            .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build(),
        CommandButton.Builder(
            if (favorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName(
                context.getString(
                    if (favorite) R.string.remove_from_favorites else R.string.add_to_favorites
                )
            )
            .setSessionCommand(toggleFavorite)
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
    )
