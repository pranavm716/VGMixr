package io.github.pranavm716.vgmixr.ui.remixes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.di.DefaultDispatcher
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.dimmed
import io.github.pranavm716.vgmixr.domain.offline.OfflineProgress
import io.github.pranavm716.vgmixr.domain.offline.OfflineStore
import io.github.pranavm716.vgmixr.domain.offline.offlineProgress
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.settings.SortSettings
import io.github.pranavm716.vgmixr.domain.sort.SortKey
import io.github.pranavm716.vgmixr.domain.sort.SortKeys
import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import io.github.pranavm716.vgmixr.domain.sort.playableUserTrackIds
import io.github.pranavm716.vgmixr.domain.sort.sortedUserTracks
import io.github.pranavm716.vgmixr.domain.usertrack.RestoreThumbnails
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrackRepository
import io.github.pranavm716.vgmixr.domain.usertrack.creditChoices
import io.github.pranavm716.vgmixr.domain.usertrack.creditFor
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.SortUi
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

/**
 * What a tap on a Remixes row does: play it, open its Link page, open its credit dialog, or (its
 * trash can) delete it.
 */
enum class RemixesMode { NORMAL, LINK, CREDIT, DELETE }

sealed interface RemixesUiState {
    data object Loading : RemixesUiState

    /**
     * The user tracks in [sort]; none shows the empty state. In a [mode] other than normal, each
     * row ends in that mode's button: a chevron to link it, a pencil to credit it, a trash can to
     * delete it.
     */
    data class Loaded(
        val rows: List<RemixRowUi>,
        val mode: RemixesMode = RemixesMode.NORMAL,
        val sort: SortUi = SortUi(SortKeys.remixesDefault, SortKeys.remixes)
    ) : RemixesUiState {
        val linking: Boolean get() = mode == RemixesMode.LINK
        val deleting: Boolean get() = mode == RemixesMode.DELETE

        /** The playing track's row, if it is one of these. */
        val playingIndex: Int? get() = rows.indexOfFirst { it.playing }.takeIf { it >= 0 }
    }
}

/**
 * A user track's row: its title over its original's game chips (none if it remixes nothing) and
 * its [channel] (its credit, when it has one). [thumbnail] is its thumbnail file's path, null until it is downloaded.
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

/**
 * The credit dialog for user track [trackId], shown under its row's [title]: its own [channel]
 * first, then the other names Remixes shows ([choices]). [selected] is its credit, null while its
 * channel shows.
 */
data class CreditDialogUi(
    val trackId: String,
    val title: String,
    val channel: String,
    val choices: List<String>,
    val selected: String?
)

@HiltViewModel
class RemixesViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val playback: PlaybackController,
    private val messages: UserMessages,
    private val userTracks: UserTrackRepository,
    private val sortSettings: SortSettings,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
    offline: OfflineStore = OfflineStore.NONE,
    restoreThumbnails: RestoreThumbnails? = null
) : ViewModel() {
    private val mode = MutableStateFlow(RemixesMode.NORMAL)
    private val creditTrackId = MutableStateFlow<String?>(null)

    init {
        // While Remixes is open: missing thumbnails come back whenever the phone is online.
        restoreThumbnails?.let { viewModelScope.launch { it() } }
    }

    val uiState: StateFlow<RemixesUiState> =
        combine(
            catalog.catalog,
            playback.state.map { it.trackId }.distinctUntilChanged(),
            mode,
            sortSettings.remixes
        ) { catalog, playingId, mode, sort ->
            RemixesUiState.Loaded(
                catalog.sortedUserTracks(sort).map { it.toRow(playingId) },
                mode,
                SortUi(sort, SortKeys.remixes)
            )
        }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemixesUiState.Loading)

    /** The open credit dialog, if any (credit mode's row tap opens it). */
    val creditDialog: StateFlow<CreditDialogUi?> =
        combine(creditTrackId, userTracks.userTracks, catalog.catalog) { id, tracks, catalog ->
            val track = tracks.firstOrNull { it.id == id } ?: return@combine null
            CreditDialogUi(
                trackId = track.id,
                // The title as its row shows it: the original's, while linked.
                title = catalog.track(track.id)?.title ?: track.title,
                channel = track.channel,
                choices = creditChoices(tracks, track),
                selected = track.credit
            )
        }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** How much of Remixes is downloaded, for the sort row's icon. */
    val download: StateFlow<OfflineProgress?> =
        combine(catalog.catalog, offline.playableOffline) { catalog, onPhone ->
            offlineProgress(catalog.userTracks, onPhone)
        }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Plays Remixes from the tapped track, in their sort, leaving out tracks that can't play. A track
     * whose video is broken says so instead, on every tap.
     */
    fun onTrackClick(trackId: String) {
        viewModelScope.launch {
            val catalog = catalog.catalog.first()
            val track = catalog.track(trackId) ?: return@launch
            if (track.offline == true) {
                messages.post(UserMessage.TrackOffline(track.title))
                return@launch
            }
            val unplayable = track.unplayable
            if (unplayable != null) {
                messages.post(UserMessage.TrackUnplayable(track.id, track.title, unplayable))
                return@launch
            }
            val ids = catalog.playableUserTrackIds(sortSettings.remixes.value)
            val index = ids.indexOf(trackId)
            if (index >= 0) playback.playFrom(ids, index, QueueContext.Remixes)
        }
    }

    /**
     * Plays every remix (a long press on the Remixes tab), leaving out those whose video is broken:
     * in order, or shuffled from a random one with shuffle on, as a held family or playlist plays.
     */
    fun onPlayAll() {
        viewModelScope.launch {
            val ids = catalog.catalog.first().playableUserTrackIds(sortSettings.remixes.value)
            if (ids.isNotEmpty()) playback.playAll(ids, QueueContext.Remixes)
        }
    }

    /** The sort menu: picking another key sorts by it, ascending. */
    fun onSortKey(key: SortKey) {
        val sort = sortSettings.remixes.value
        if (key != sort.key) changeSort(sort.withKey(key))
    }

    /** The sort arrow: the same key the other way. */
    fun onReverseSort() = changeSort(sortSettings.remixes.value.reversed())

    /** Saves the new sort; if Remixes is playing, its "Next from" takes the new order. */
    private fun changeSort(sort: SortSetting) {
        sortSettings.setRemixes(sort)
        viewModelScope.launch {
            val ids = catalog.catalog.first().playableUserTrackIds(sort)
            playback.resort(ids, QueueContext.Remixes)
        }
    }

    /** Adds one track to "Next in queue"; a greyed track can't be queued. */
    fun onQueueTrack(trackId: String) {
        viewModelScope.launch {
            val track = catalog.catalog.first().track(trackId)
            if (track == null || track.dimmed) return@launch
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

    /** The menu's Credit: every row gets a pencil, and a tap opens its credit dialog. */
    fun onStartCrediting() {
        mode.value = RemixesMode.CREDIT
    }

    /** Opens user track [trackId]'s credit dialog. */
    fun onOpenCredit(trackId: String) {
        creditTrackId.value = trackId
    }

    /** A choice in the dialog: a name, or null for the track's own channel. */
    fun onPickCredit(name: String?) = saveCredit(name.orEmpty())

    /** The dialog's New name: a blank one saves nothing. */
    fun onAddCredit(typed: String) {
        if (typed.isNotBlank()) saveCredit(typed)
    }

    fun onDismissCredit() {
        creditTrackId.value = null
    }

    // Closes the dialog and stays in credit mode, so the next row can be credited.
    private fun saveCredit(name: String) {
        val dialog = creditDialog.value ?: return
        creditTrackId.value = null
        viewModelScope.launch {
            val own = userTracks.userTracks.first().firstOrNull { it.id == dialog.trackId }
                ?: return@launch
            userTracks.setCredit(own.id, creditFor(name, own, dialog.choices))
        }
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
            playback.removeTrack(trackId)
            messages.post(UserMessage.RemixDeleted(track, shown))
        }
    }

    private fun CatalogTrack.toRow(playingId: String?) = RemixRowUi(
        id = id,
        title = title,
        chips = games.map { ChipUi(it.shortName, it.color) },
        channel = artist,
        thumbnail = thumbnail,
        unplayable = dimmed,
        playing = id == playingId
    )
}
