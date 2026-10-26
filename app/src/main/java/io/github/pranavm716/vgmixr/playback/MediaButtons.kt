package io.github.pranavm716.vgmixr.playback

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.playback.QueueContext

const val TOGGLE_SHUFFLE = "io.github.pranavm716.vgmixr.TOGGLE_SHUFFLE"
const val TOGGLE_FAVORITE = "io.github.pranavm716.vgmixr.TOGGLE_FAVORITE"

val toggleShuffle = SessionCommand(TOGGLE_SHUFFLE, Bundle.EMPTY)
val toggleFavorite = SessionCommand(TOGGLE_FAVORITE, Bundle.EMPTY)

/** Not a button: the app tells the service a collection was sorted anew. */
const val RESORT = "io.github.pranavm716.vgmixr.RESORT"
val resortCommand = SessionCommand(RESORT, Bundle.EMPTY)
private const val RESORT_TRACK_IDS = "trackIds"
private const val RESORT_CONTEXT = "context"

fun resortArgs(trackIds: List<String>, context: QueueContext) = Bundle().apply {
    putStringArrayList(RESORT_TRACK_IDS, ArrayList(trackIds))
    putString(RESORT_CONTEXT, context.key)
}

/** The track ids and context [resortArgs] packed, or null if they are missing. */
fun resortOf(args: Bundle): Pair<List<String>, QueueContext>? {
    val trackIds = args.getStringArrayList(RESORT_TRACK_IDS) ?: return null
    val context = args.getString(RESORT_CONTEXT)?.let(QueueContext::fromKey) ?: return null
    return trackIds to context
}

/**
 * The media notification's extra buttons, each showing its state: shuffle at the far left and
 * favorite (the current track) at the far right, around previous, play/pause and next. Media3
 * passes only overflow buttons on to Android's own media session (the notification and lock
 * screen on Android 13+), so each falls back to it; there the system places them.
 */
@OptIn(UnstableApi::class)
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
