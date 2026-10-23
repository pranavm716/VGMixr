package io.github.pranavm716.vgmixr.domain.search

import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.history.PlayHistory
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Gathers what search ranking knows about the user's listening (see [frecencyWeight]). */
class SearchRanking @Inject constructor(
    playHistory: PlayHistory,
    playlists: PlaylistRepository,
    playback: PlaybackController,
    catalog: CatalogRepository,
    browsed: BrowsedFamily
) {
    // The current track's family, playing or paused; a track the catalog lacks lifts none.
    private val playingFamily: Flow<String?> = combine(
        playback.state.map { it.trackId }.distinctUntilChanged(),
        catalog.catalog
    ) { trackId, catalog ->
        trackId?.let { id -> catalog.tracks.firstOrNull { it.id == id }?.familyId }
    }

    val signals: Flow<RankingSignals> = combine(
        playHistory.plays,
        playlists.tracksInPlaylists,
        playingFamily,
        browsed.familyId
    ) { plays, inPlaylists, playing, browsedFamily ->
        RankingSignals(plays, inPlaylists, setOfNotNull(playing, browsedFamily))
    }
}
