package io.github.pranavm716.vgmixr.ui.settings

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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.pranavm716.vgmixr.domain.settings.Accent
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme
import io.github.pranavm716.vgmixr.ui.theme.color
import io.github.pranavm716.vgmixr.ui.theme.label

// Smaller than Shuttle's 32 dp, so all eight fit in one row across a 360 dp phone.
private val CircleSize = 28.dp
private val RingWidth = 2.5.dp
private val DotSize = 19.dp

// Each circle's cell reaches past it, so the row starts this far before the title's edge (24 dp)
// and the first circle lines up with it.
private val RowPadding = 18.dp

@Composable
fun SettingsTab(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state, onAccentClick = viewModel::onAccentClick, onBack = onBack)
}

/**
 * Settings under a back arrow, as Edit playlist's header. Laid out as Shuttle's Appearance page:
 * a section title, then its options. Accent is a ring per color, the current one filled in;
 * a tap applies it at once.
 */
@Composable
fun SettingsScreen(state: SettingsUiState, onAccentClick: (Accent) -> Unit, onBack: () -> Unit) {
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
                    AccentCircle(accent, accent == state.accent) { onAccentClick(accent) }
                }
            }
        }
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

@Composable
private fun RowScope.AccentCircle(accent: Accent, selected: Boolean, onClick: () -> Unit) {
    val color = accent.color
    val name = stringResource(accent.label)
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
            Modifier.size(CircleSize).border(RingWidth, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(DotSize).background(color, CircleShape))
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
