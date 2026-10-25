package io.github.pranavm716.vgmixr.ui.remixes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.di.DefaultDispatcher
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.playableUserTrackIds
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrackRepository
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a tap on a Remixes row does: play it, open its Link page, or (its trash can) delete it. */
enum class RemixesMode { NORMAL, LINK, DELETE }

sealed interface RemixesUiState {
    data object Loading : RemixesUiState

    /**
     * The user tracks, newest first; none shows the empty state. In a [mode] other than normal, each
     * row ends in that mode's button: a chevron to link it, a trash can to delete it.
     */
    data class Loaded(val rows: List<RemixRowUi>, val mode: RemixesMode = RemixesMode.NORMAL) :
        RemixesUiState {
        val linking: Boolean get() = mode == RemixesMode.LINK
        val deleting: Boolean get() = mode == RemixesMode.DELETE

        /** The playing track's row, if it is one of these. */
        val playingIndex: Int? get() = rows.indexOfFirst { it.playing }.takeIf { it >= 0 }
    }
}

/**
 * A user track's row: its title over its original's game chips (none if it remixes nothing) and
 * its [channel]. [thumbnail] is its thumbnail file's path, null until it is downloaded.
 */
data class RemixRowUi(
    val id: String,
    val title: String,
    val chips: List<ChipUi>,
    val channel: String?,
    val thumbnail: String?,
    val unplayable: Boolean = false,
    val playing: Boolean = false
)

@HiltViewModel
class RemixesViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val playback: PlaybackController,
    private val messages: UserMessages,
    private val userTracks: UserTrackRepository,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : ViewModel() {
    private val mode = MutableStateFlow(RemixesMode.NORMAL)

    val uiState: StateFlow<RemixesUiState> =
        combine(
            catalog.catalog,
            playback.state.map { it.trackId }.distinctUntilChanged(),
            mode
        ) { catalog, playingId, mode ->
            RemixesUiState.Loaded(catalog.userTracks.map { it.toRow(playingId) }, mode)
        }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemixesUiState.Loading)

    /**
     * Plays Remixes from the tapped track, as listed, leaving out tracks that can't play. A track
     * whose video is broken says so instead, on every tap.
     */
    fun onTrackClick(trackId: String) {
        viewModelScope.launch {
            val catalog = catalog.catalog.first()
            val track = catalog.track(trackId) ?: return@launch
            val unplayable = track.unplayable
            if (unplayable != null) {
                messages.post(UserMessage.TrackUnplayable(track.id, track.title, unplayable))
                return@launch
            }
            val ids = catalog.playableUserTrackIds()
            val index = ids.indexOf(trackId)
            if (index >= 0) playback.playFrom(ids, index, QueueContext.Remixes)
        }
    }

    /** Adds one track to "Next in queue"; a greyed track can't be queued. */
    fun onQueueTrack(trackId: String) {
        viewModelScope.launch {
            val track = catalog.catalog.first().track(trackId)
            if (track == null || track.unplayable != null) return@launch
            playback.addToQueue(listOf(trackId))
            messages.post(UserMessage.AddedToQueue(1))
        }
    }

    /** The menu's Delete: every row gets a delete button. */
    fun onStartDeleting() {
        mode.value = RemixesMode.DELETE
    }

    /** The menu's Link: every row gets a chevron, and a tap opens its Link page. */
    fun onStartLinking() {
        mode.value = RemixesMode.LINK
    }

    /** Done, or back: the rows go back to normal. */
    fun onDone() {
        mode.value = RemixesMode.NORMAL
    }

    /**
     * Deletes user track [trackId] at once, offering to undo it. Its playlist entries, history and
     * thumbnail stay, so undoing (or sharing the video again) brings it all back.
     */
    fun onDelete(trackId: String) {
        viewModelScope.launch {
            val track = userTracks.userTracks.first().firstOrNull { it.id == trackId }
                ?: return@launch
            // The title as the row showed it: the original's, while linked.
            val shown = catalog.catalog.first().track(trackId)?.title ?: track.title
            userTracks.delete(trackId)
            messages.post(UserMessage.RemixDeleted(track, shown))
        }
    }

    private fun CatalogTrack.toRow(playingId: String?) = RemixRowUi(
        id = id,
        title = title,
        chips = games.map { ChipUi(it.shortName, it.color) },
        channel = channel,
        thumbnail = thumbnail,
        unplayable = unplayable != null,
        playing = id == playingId
    )
}
