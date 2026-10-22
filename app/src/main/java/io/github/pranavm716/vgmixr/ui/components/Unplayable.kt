package io.github.pranavm716.vgmixr.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.pranavm716.vgmixr.R

/** Greys a row's number, title and chips when its track's sources are all broken. */
fun Modifier.unplayable(unplayable: Boolean): Modifier = if (unplayable) alpha(0.38f) else this

/** The mark at the end of a greyed row. */
@Composable
fun UnplayableIcon() {
    Icon(
        painterResource(R.drawable.ic_music_off),
        contentDescription = stringResource(R.string.unplayable),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp)
    )
}
