package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import io.github.pranavm716.vgmixr.R

/**
 * A plus, or the accent check once [added]; a tap on either toggles. Ends the rows of "Edit
 * <playlist>" (a track in the playlist) and "Add to playlist" (the track in a playlist).
 */
@Composable
fun ToggleAddButton(added: Boolean, onClick: () -> Unit, addLabel: String, removeLabel: String) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(if (added) R.drawable.ic_check else R.drawable.ic_add),
            contentDescription = if (added) removeLabel else addLabel,
            tint = if (added) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
