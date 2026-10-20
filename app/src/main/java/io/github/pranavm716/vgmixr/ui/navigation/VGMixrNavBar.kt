package io.github.pranavm716.vgmixr.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource

/** Shuttle's navbar: gray icons, the selected tab in the accent with its label, no pill. */
@Composable
fun VGMixrNavBar(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = Color.Transparent,
        unselectedIconColor = MaterialTheme.colorScheme.outline,
        unselectedTextColor = MaterialTheme.colorScheme.outline
    )
    NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(painterResource(destination.icon), contentDescription = destination.label)
                },
                label = { Text(destination.label, style = MaterialTheme.typography.labelLarge) },
                alwaysShowLabel = false,
                colors = colors,
                modifier = Modifier.testTag(destination.navItemTag)
            )
        }
    }
}
