package io.github.pranavm716.vgmixr.ui.remixes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The Link page's header: the remix it links, by its video's own title. */
data class LinkUiState(val videoTitle: String = "")

@HiltViewModel
class LinkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalog: CatalogRepository
) : ViewModel() {
    // The LinkRoute's argument, stored by type-safe navigation under its property name.
    private val trackId: String = checkNotNull(savedStateHandle["trackId"])

    val uiState: StateFlow<LinkUiState> = catalog.catalog
        .map { catalog ->
            val track = catalog.track(trackId)
            LinkUiState(track?.videoTitle ?: track?.title.orEmpty())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LinkUiState())
}

@Composable
fun LinkTab(onBack: () -> Unit, viewModel: LinkViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LinkScreen(state, onBack)
}

/**
 * Sets, changes or clears the catalog track a remix remixes. Its header, as Add to playlist's: the
 * page's name over the remix's video title. The rest comes later.
 */
@Composable
fun LinkScreen(state: LinkUiState, onBack: () -> Unit) {
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
    }
}
