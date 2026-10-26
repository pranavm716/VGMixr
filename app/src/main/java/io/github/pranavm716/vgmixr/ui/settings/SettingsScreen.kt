package io.github.pranavm716.vgmixr.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.offline.OfflineUsage
import io.github.pranavm716.vgmixr.domain.settings.Accent
import io.github.pranavm716.vgmixr.domain.settings.Background
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import io.github.pranavm716.vgmixr.ui.theme.color
import io.github.pranavm716.vgmixr.ui.theme.label

// Smaller than Shuttle's 32 dp, so all eight fit in one row across a 360 dp phone.
private val CircleSize = 28.dp
private val CheckSize = 18.dp

// Each circle's cell reaches past it, so the row starts this far before the title's edge (24 dp)
// and the first circle lines up with it.
private val RowPadding = 18.dp

@Composable
fun SettingsTab(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    SettingsScreen(
        state,
        onAccentClick = viewModel::onAccentClick,
        onBack = onBack,
        onBackgroundClick = viewModel::onBackgroundClick,
        storage = storage,
        onClearRecent = viewModel::onClearRecent
    )
}

/**
 * Settings under a back arrow, as Edit playlist's header. Laid out as Shuttle's Appearance page:
 * a section title, then its options. Accent and Background are a solid circle per color, the
 * current one with a white check; a tap applies either at once.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAccentClick: (Accent) -> Unit,
    onBack: () -> Unit,
    onBackgroundClick: (Background) -> Unit = {},
    storage: OfflineUsage? = null,
    onClearRecent: () -> Unit = {}
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
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
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(R.string.accent))
            // One row, spread evenly: every circle gets an equal share of the width.
            Row(Modifier.fillMaxWidth().padding(horizontal = RowPadding).selectableGroup()) {
                state.accents.forEach { accent ->
                    Swatch(
                        accent.color,
                        stringResource(accent.label),
                        accent == state.accent,
                        onClick = { onAccentClick(accent) }
                    )
                }
            }
            SectionTitle(stringResource(R.string.background))
            // The accent row's grid, so the circles line up under the accents.
            Row(Modifier.fillMaxWidth().padding(horizontal = RowPadding).selectableGroup()) {
                state.backgrounds.forEach { background ->
                    Swatch(
                        background.color,
                        stringResource(background.label),
                        background == state.background,
                        onClick = { onBackgroundClick(background) },
                        outlined = true
                    )
                }
                repeat(state.accents.size - state.backgrounds.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            if (storage != null) Storage(storage, onClearRecent)
        }
    }
}

/**
 * Space taken on the phone: downloaded tracks (playlists and Remixes, kept) and recent plays
 * (cached), with a button that deletes the recent ones.
 */
@Composable
private fun Storage(usage: OfflineUsage, onClearRecent: () -> Unit) {
    val context = LocalContext.current
    SectionTitle(stringResource(R.string.storage))
    Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        Text(
            stringResource(
                R.string.storage_downloaded,
                Formatter.formatShortFileSize(context, usage.downloadedBytes)
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            stringResource(
                R.string.storage_cached,
                Formatter.formatShortFileSize(context, usage.cachedBytes)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    TextButton(onClick = onClearRecent, modifier = Modifier.padding(horizontal = 12.dp)) {
        Text(stringResource(R.string.clear_recent))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 4.dp)
    )
}

/**
 * One color to pick: a solid circle, with a white check inside when it is the current one. A
 * background's circle is [outlined] in grey, since black on the black page would vanish.
 */
@Composable
private fun RowScope.Swatch(
    color: Color,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    outlined: Boolean = false
) {
    Box(
        Modifier
            .weight(1f)
            .height(48.dp)
            .semantics { contentDescription = name }
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
                interactionSource = null,
                indication = ripple(bounded = false, radius = 20.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(CircleSize)
                .background(color, CircleShape)
                .then(
                    if (outlined) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(CheckSize)
                )
            }
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    VGMixrTheme {
        SettingsScreen(SettingsUiState(accent = Accent.TEAL), onAccentClick = {}, onBack = {})
    }
}
