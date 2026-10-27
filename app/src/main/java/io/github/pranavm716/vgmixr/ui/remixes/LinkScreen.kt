package io.github.pranavm716.vgmixr.ui.remixes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.di.DefaultDispatcher
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.search.SearchIndex
import io.github.pranavm716.vgmixr.domain.search.search
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrackRepository
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.components.NoSearchResults
import io.github.pranavm716.vgmixr.ui.components.SearchField
import io.github.pranavm716.vgmixr.ui.components.TrackTileRow
import io.github.pranavm716.vgmixr.ui.search.SEARCH_DELAY_MS
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A catalog track on the Link page: [linked] marks the one the remix remixes. */
data class LinkRowUi(
    val id: String,
    val title: String,
    val chips: List<ChipUi>,
    val familyId: String?,
    val linked: Boolean = false
)

/**
 * The Link page: the remix by its video's title, the track it is [linked] to (none if it isn't),
 * and the catalog tracks matching a typed query ([results], [noResults] when none).
 * [resultsQuery] is the query the results answer.
 */
data class LinkUiState(
    val videoTitle: String = "",
    val linked: LinkRowUi? = null,
    val results: List<LinkRowUi> = emptyList(),
    val noResults: Boolean = false,
    val resultsQuery: String = ""
)

/** One-off actions for the page. */
sealed interface LinkEvent {
    /** A track was picked: leave the page. */
    data object Close : LinkEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class LinkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalog: CatalogRepository,
    private val userTracks: UserTrackRepository,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : ViewModel() {
    // The LinkRoute's argument, stored by type-safe navigation under its property name.
    private val trackId: String = checkNotNull(savedStateHandle["trackId"])

    private val query = MutableStateFlow("")

    // As in the Search tab: searched once typing pauses, an emptied field clears at once. Only
    // the catalog's tracks: a remix remixes a catalog track, never another remix.
    private val matches = combine(
        query.debounce { if (it.isBlank()) 0L else SEARCH_DELAY_MS },
        catalog.catalog.map { SearchIndex(it.copy(userTracks = emptyList())) }
    ) { query, index -> query to if (query.isBlank()) emptyList() else search(query, index) }
        .flowOn(dispatcher)

    val uiState: StateFlow<LinkUiState> =
        combine(catalog.catalog, matches) { catalog, (query, tracks) ->
            val remix = catalog.track(trackId)
            val linkedId = remix?.remixOf
            LinkUiState(
                videoTitle = remix?.videoTitle ?: remix?.title.orEmpty(),
                linked = linkedId?.let { id ->
                    catalog.tracks.firstOrNull { it.id == id }?.toRow(linked = true)
                },
                results = tracks.map { it.toRow(linked = it.id == linkedId) },
                noResults = query.isNotBlank() && tracks.isEmpty(),
                resultsQuery = query
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LinkUiState())

    private val eventChannel = Channel<LinkEvent>(Channel.BUFFERED)
    val events: Flow<LinkEvent> = eventChannel.receiveAsFlow()

    fun onQueryChange(query: String) {
        this.query.value = query
    }

    /** Links the remix to catalog track [originalId], then closes the page. */
    fun onPick(originalId: String) {
        viewModelScope.launch {
            userTracks.setRemixOf(trackId, originalId)
            eventChannel.send(LinkEvent.Close)
        }
    }

    /** Clears the link: the remix shows its video's title again and remixes nothing. */
    fun onClear() {
        viewModelScope.launch { userTracks.setRemixOf(trackId, null) }
    }
}

private fun CatalogTrack.toRow(linked: Boolean) = LinkRowUi(
    id = id,
    title = title,
    chips = games.map { ChipUi(it.shortName, it.color) },
    familyId = familyId,
    linked = linked
)

@Composable
fun LinkTab(onBack: () -> Unit, viewModel: LinkViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The field's text is kept here, so typing never waits on the ViewModel.
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LinkEvent.Close -> onBack()
            }
        }
    }
    LinkScreen(
        state,
        query,
        onQueryChange = {
            query = it
            viewModel.onQueryChange(it)
        },
        onPick = viewModel::onPick,
        onClear = viewModel::onClear,
        onBack = onBack
    )
}

/**
 * Sets, changes or clears the catalog track a remix remixes. Its header, as Add to playlist's: the
 * page's name over the remix's video title. Under it the track it is linked to, whose cross clears
 * the link, then the Search tab's field, focused on opening, over the catalog tracks it finds (the
 * linked one checked). Tapping one links the remix to it and closes the page.
 */
@Composable
fun LinkScreen(
    state: LinkUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.back)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    stringResource(R.string.link_to_original),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1
                )
                Text(
                    state.videoTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        val linked = state.linked
        if (linked != null) {
            Text(
                stringResource(R.string.linked_to),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
            TrackTileRow(
                linked.title,
                linked.chips,
                unplayable = false,
                current = false,
                onClick = null,
                familyId = linked.familyId,
                trailing = {
                    IconButton(onClick = onClear) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.clear_link)
                        )
                    }
                }
            )
        }
        SearchField(
            query,
            onQueryChange,
            focusRequester,
            onClear = {
                onQueryChange("")
                focusRequester.requestFocus()
                keyboard?.show()
            },
            onSearch = {
                keyboard?.hide()
                focusManager.clearFocus()
            }
        )
        if (state.noResults) {
            NoSearchResults(query.trim())
            return@Column
        }
        val listState = rememberLazyListState()
        // Results for a new query start at the top, as the Search tab's do.
        val shown = remember { mutableStateOf("") }
        SideEffect {
            if (shown.value != state.resultsQuery) {
                shown.value = state.resultsQuery
                listState.requestScrollToItem(0)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(state.results, key = { it.id }) { row ->
                TrackTileRow(
                    row.title,
                    row.chips,
                    unplayable = false,
                    current = false,
                    onClick = {
                        keyboard?.hide()
                        onPick(row.id)
                    },
                    familyId = row.familyId,
                    trailing = if (row.linked) {
                        {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    painterResource(R.drawable.ic_check),
                                    contentDescription = stringResource(R.string.linked_to),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }
}
