package io.github.pranavm716.vgmixr.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The queue screen: the current track, "Next in queue" (what the user added) and "Next from"
 * [nextFromName]. Played tracks aren't listed.
 */
data class QueueUiState(
    val nowPlaying: QueueRowUi?,
    val upNext: List<QueueRowUi>,
    val nextFromName: String?,
    val nextFrom: List<QueueRowUi>
) {
    companion object {
        val EMPTY = QueueUiState(null, emptyList(), null, emptyList())
    }
}

/**
 * A track in the queue; [index] is its place in the player's queue, for jumping and removing.
 * [unplayable] greys a track whose sources are all broken.
 */
data class QueueRowUi(
    val index: Int,
    val trackId: String,
    val title: String,
    val chips: List<ChipUi>,
    val unplayable: Boolean = false
)

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val playback: PlaybackController,
    private val messages: UserMessages
) : ViewModel() {
    val uiState: StateFlow<QueueUiState> =
        combine(repository.catalog, playback.state) { catalog, state -> catalog.queue(state) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueueUiState.EMPTY)

    /** Plays entry [index]; a track whose sources are all broken says so instead. */
    fun onJump(index: Int) {
        viewModelScope.launch {
            val trackId = playback.state.value.queue.entries.getOrNull(index)?.trackId
            val track = repository.catalog.first().tracks.firstOrNull { it.id == trackId }
            val unplayable = track?.unplayable
            if (unplayable != null) {
                messages.post(UserMessage.TrackUnplayable(track.id, track.title, unplayable))
            } else {
                playback.jumpTo(index)
            }
        }
    }

    fun onRemove(index: Int) = playback.removeFromQueue(index)

    /** Adds the track at [index] (from "Next from") to the end of "Next in queue". */
    fun onQueue(index: Int) {
        val trackId = playback.state.value.queue.entries.getOrNull(index)?.trackId ?: return
        playback.addToQueue(listOf(trackId))
        messages.post(UserMessage.AddedToQueue(1))
    }

    fun onClear() = playback.clearQueue()
}

private fun Catalog.queue(state: PlaybackState): QueueUiState {
    val queue = state.queue
    if (queue.current !in queue.entries.indices) return QueueUiState.EMPTY
    val tracksById = tracks.associateBy { it.id }
    fun row(index: Int): QueueRowUi? {
        val track = tracksById[queue.entries[index].trackId] ?: return null
        return QueueRowUi(
            index = index,
            trackId = track.id,
            title = track.title,
            chips = track.games.map { ChipUi(it.shortName, it.color) },
            unplayable = track.unplayable != null
        )
    }
    val queueStart = queue.current + 1
    val queueEnd = queueStart + queue.upNext.size
    val nextFromContext = queue.nextFromContext.firstOrNull()?.context as? QueueContext.Family
    return QueueUiState(
        nowPlaying = row(queue.current),
        upNext = (queueStart until queueEnd).mapNotNull(::row),
        nextFromName = nextFromContext?.let { context ->
            families.firstOrNull { it.id == context.familyId }?.name
        },
        nextFrom = (queueEnd until queue.entries.size).mapNotNull(::row)
    )
}
