package io.github.pranavm716.vgmixr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A tab whose content isn't built yet: its header and "Coming soon". */
@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(title)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Coming soon",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A tab's title, large and left-aligned as in Shuttle. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
    )
}
