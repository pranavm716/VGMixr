package io.github.pranavm716.vgmixr.data.catalog

import androidx.room.withTransaction
import io.github.pranavm716.vgmixr.data.db.CatalogDao
import io.github.pranavm716.vgmixr.data.db.GameEntity
import io.github.pranavm716.vgmixr.data.db.ImportStateDao
import io.github.pranavm716.vgmixr.data.db.SourceEntity
import io.github.pranavm716.vgmixr.data.db.TrackEntity
import io.github.pranavm716.vgmixr.data.db.TrackGameEntity
import io.github.pranavm716.vgmixr.data.db.UserDataDao
import io.github.pranavm716.vgmixr.data.db.VGMixrDatabase
import io.github.pranavm716.vgmixr.domain.catalog.CatalogImportResult
import io.github.pranavm716.vgmixr.domain.manifest.ManifestParseResult
import io.github.pranavm716.vgmixr.domain.model.Game
import io.github.pranavm716.vgmixr.domain.model.Manifest
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.model.Track
import java.time.Clock
import javax.inject.Inject

/** Applies a parsed manifest to Room following the import rules in docs/manifest.md. */
class CatalogImporter @Inject constructor(
    private val db: VGMixrDatabase,
    private val catalogDao: CatalogDao,
    private val userDataDao: UserDataDao,
    private val importStateDao: ImportStateDao,
    private val clock: Clock
) {
    suspend fun import(result: ManifestParseResult): CatalogImportResult {
        val manifest = (result as ManifestParseResult.Valid).manifest
        db.withTransaction { write(manifest) }
        return CatalogImportResult.Imported(manifest.generatedAt)
    }

    private suspend fun write(manifest: Manifest) {
        catalogDao.upsertGames(manifest.games.mapIndexed { index, game -> game.toEntity(index) })
        catalogDao.upsertTracks(manifest.tracks.map { it.toEntity() })
        for (track in manifest.tracks) {
            catalogDao.deleteTrackGames(track.id)
            catalogDao.insertTrackGames(track.games.orEmpty().map { TrackGameEntity(track.id, it) })
            catalogDao.deleteSources(track.id)
            catalogDao.insertSources(
                track.sources.mapIndexed { index, source -> source.toEntity(track.id, index) }
            )
        }
    }
}

private fun Game.toEntity(position: Int) = GameEntity(
    id = id,
    name = name,
    shortName = shortName,
    color = color,
    generation = generation,
    platform = platform,
    familyId = familyId,
    position = position,
    retired = false
)

private fun Track.toEntity() = TrackEntity(
    id = id,
    familyId = familyId,
    title = title,
    category = category.name,
    order = order,
    retired = false
)

private fun Source.toEntity(trackId: String, position: Int) = when (this) {
    is Source.Local -> SourceEntity(
        trackId = trackId,
        position = position,
        type = SourceEntity.TYPE_LOCAL,
        path = path,
        loopSampleRate = loop?.sampleRate,
        loopIntroEnd = loop?.introEnd,
        loopEnd = loop?.loopEnd
    )

    is Source.Stream -> SourceEntity(
        trackId = trackId,
        position = position,
        type = SourceEntity.TYPE_STREAM,
        videoId = videoId,
        startMs = startMs,
        endMs = endMs
    )
}
