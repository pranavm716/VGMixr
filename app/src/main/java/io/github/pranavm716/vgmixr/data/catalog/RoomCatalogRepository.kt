package io.github.pranavm716.vgmixr.data.catalog

import androidx.room.withTransaction
import io.github.pranavm716.vgmixr.data.db.CatalogDao
import io.github.pranavm716.vgmixr.data.db.GameEntity
import io.github.pranavm716.vgmixr.data.db.SourceEntity
import io.github.pranavm716.vgmixr.data.db.VGMixrDatabase
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.model.Game
import io.github.pranavm716.vgmixr.domain.model.Loop
import io.github.pranavm716.vgmixr.domain.model.Source
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Reads the whole catalog from Room and re-reads it whenever a catalog table changes. */
class RoomCatalogRepository @Inject constructor(
    private val db: VGMixrDatabase,
    private val dao: CatalogDao
) : CatalogRepository {
    // One trigger over all catalog tables, rather than combining per-table Flows, so an import
    // (one transaction) yields one consistent snapshot. User-data tables aren't watched.
    override val catalog: Flow<Catalog> =
        db.invalidationTracker
            .createFlow("games", "tracks", "track_games", "sources")
            .map { db.withTransaction { load() } }

    private suspend fun load(): Catalog {
        val games = dao.activeGames().map { it.toDomain() }
        // Retired games are left out of name and label, like they are from chips.
        val families = games.groupBy { it.familyId }.map { (id, members) ->
            Family(
                id = id,
                name = members.joinToString(" / ") { it.name },
                label = members.joinToString("") { it.shortName }
            )
        }
        val subsets = dao.allTrackGames().groupBy({ it.trackId }, { it.gameId })
        val sources = dao.allSources().groupBy { it.trackId }
        val tracks = dao.activeTracks().map { track ->
            val subset = subsets[track.id]
            CatalogTrack(
                id = track.id,
                familyId = track.familyId,
                games = games.filter { game ->
                    if (subset == null) game.familyId == track.familyId else game.id in subset
                },
                title = track.title,
                order = track.order,
                sources = sources[track.id].orEmpty().map { it.toDomain() }
            )
        }
        return Catalog(games, families, tracks)
    }
}

private fun GameEntity.toDomain() = Game(
    id = id,
    name = name,
    shortName = shortName,
    color = color,
    generation = generation,
    platform = platform,
    familyId = familyId
)

private fun SourceEntity.toDomain(): Source = when (type) {
    SourceEntity.TYPE_LOCAL -> Source.Local(
        path = checkNotNull(path) { "local source without path: $trackId[$position]" },
        loop = loopSampleRate?.let { sampleRate ->
            Loop(sampleRate, checkNotNull(loopIntroEnd), checkNotNull(loopEnd))
        }
    )

    SourceEntity.TYPE_STREAM -> Source.Stream(
        videoId = checkNotNull(videoId) { "stream source without videoId: $trackId[$position]" },
        startMs = checkNotNull(startMs),
        endMs = endMs
    )

    else -> error("unknown source type $type: $trackId[$position]")
}
