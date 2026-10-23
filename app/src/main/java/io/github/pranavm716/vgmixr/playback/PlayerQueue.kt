package io.github.pranavm716.vgmixr.playback

import androidx.media3.common.Player
import io.github.pranavm716.vgmixr.domain.playback.PlayQueue
import io.github.pranavm716.vgmixr.domain.playback.QueueEdit

/** The player's items as the domain's queue. */
fun Player.queue(): PlayQueue = PlayQueue(
    entries = (0 until mediaItemCount).map { getMediaItemAt(it).queueEntry() },
    current = if (mediaItemCount == 0) -1 else currentMediaItemIndex
)

/** Applies [edit] to the player, as [PlayQueue.applying] does to the queue. */
fun Player.apply(edit: QueueEdit) {
    when (edit) {
        is QueueEdit.Replace -> {
            setMediaItems(edit.entries.map(::queueRequest), edit.startIndex, 0)
            prepare()
            play()
        }

        is QueueEdit.Insert -> addMediaItems(edit.index, edit.entries.map(::queueRequest))

        is QueueEdit.Move -> moveMediaItems(edit.from, edit.to, edit.newIndex)

        is QueueEdit.Remove -> removeMediaItems(edit.from, edit.to)

        is QueueEdit.Rearrange ->
            replaceMediaItems(edit.from, edit.to, edit.sourceIndices.map(::getMediaItemAt))

        is QueueEdit.Seek -> {
            seekTo(edit.index, 0)
            playFromHere()
        }
    }
}

fun Player.apply(edits: List<QueueEdit>) = edits.forEach { apply(it) }

/** Skipping always plays the new track, also from pause or after playback stopped. */
fun Player.playFromHere() {
    if (playbackState == Player.STATE_IDLE) prepare()
    play()
}
