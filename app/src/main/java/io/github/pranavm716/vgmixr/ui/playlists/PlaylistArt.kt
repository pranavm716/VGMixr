package io.github.pranavm716.vgmixr.ui.playlists

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import io.github.pranavm716.vgmixr.ui.components.ArtTile
import io.github.pranavm716.vgmixr.ui.components.FavoritesArt

/** A playlist's tile: Favorites' heart, or the blank tile until playlists get art. */
@Composable
fun PlaylistArt(isFavorites: Boolean, size: Dp) {
    if (isFavorites) FavoritesArt(size) else ArtTile(size)
}
