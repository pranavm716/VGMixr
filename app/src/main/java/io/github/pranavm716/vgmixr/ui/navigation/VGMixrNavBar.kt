package io.github.pranavm716.vgmixr.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

// Long enough to be seen at a 0.5x animator scale, short enough not to feel slow at 1x.
private const val ANIMATION_MS = 300

/**
 * Shuttle's navbar: gray icons, the selected tab in the accent with its label, no pill. Built by
 * hand instead of M3's NavigationBar so the ripple covers the icon and label and the timing is ours.
 */
@Composable
fun VGMixrNavBar(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .height(68.dp)
                .selectableGroup()
        ) {
            TopLevelDestination.entries.forEach { destination ->
                NavItem(destination, destination == selected, onClick = { onSelect(destination) })
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(ANIMATION_MS),
        label = "navItemColor"
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .testTag(destination.navItemTag)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = null,
                // Unbounded, centered on the item, wide enough to cover the icon and label.
                indication = ripple(bounded = false, radius = 36.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painterResource(destination.icon),
            contentDescription = destination.label,
            tint = color
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(ANIMATION_MS)) + expandVertically(tween(ANIMATION_MS)),
            exit = fadeOut(tween(ANIMATION_MS)) + shrinkVertically(tween(ANIMATION_MS))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(4.dp))
                Text(destination.label, style = MaterialTheme.typography.labelLarge, color = color)
            }
        }
    }
}
