package io.github.pranavm716.vgmixr.data.catalog

import androidx.room.withTransaction
import io.github.pranavm716.vgmixr.data.db.CatalogDao
import io.github.pranavm716.vgmixr.data.db.GameEntity
import io.github.pranavm716.vgmixr.data.db.ImportStateDao
import io.github.pranavm716.vgmixr.data.db.ImportStateEntity
import io.github.pranavm716.vgmixr.data.db.SourceEntity
import io.github.pranavm716.vgmixr.data.db.TrackEntity
import io.github.pranavm716.vgmixr.data.db.TrackGameEntity
import io.github.pranavm716.vgmixr.data.db.UserDataDao
import io.github.pranavm716.vgmixr.data.db.VGMixrDatabase
import io.github.pranavm716.vgmixr.domain.catalog.CatalogImportResult
import io.github.pranavm716.vgmixr.domain.catalog.ImportError
import io.github.pranavm716.vgmixr.domain.manifest.ManifestParseResult
import io.github.pranavm716.vgmixr.domain.model.Game
import io.github.pranavm716.vgmixr.domain.model.Manifest
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.model.Track
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** Applies a parsed manifest to Room following the import rules in docs/manifest.md. */
class CatalogImporter @Inject constructor(
    private val db: VGMixrDatabase,
    private val catalogDao: CatalogDao,
    private val userDataDao: UserDataDao,
    private val importStateDao: ImportStateDao,
    private val clock: Clock
) {
    /**
     * Imports [result] unless its generatedAt was already imported. All writes happen in one
     * transaction: an invalid manifest writes nothing, and a failed write rolls back, keeping the
     * previous catalog.
     */
    suspend fun import(result: ManifestParseResult): CatalogImportResult {
        val manifest = when (result) {
            is ManifestParseResult.Invalid ->
                return CatalogImportResult.Failed(result.errors.map(ImportError::Invalid))

            is ManifestParseResult.Valid -> result.manifest
        }
        val generatedAt = manifest.generatedAt.toString()
        if (importStateDao.get()?.generatedAt == generatedAt) return CatalogImportResult.Unchanged
        return try {
            db.withTransaction {
                write(manifest)
                importStateDao.upsert(
                    ImportStateEntity(generatedAt = generatedAt, importedAt = clock.instant())
                )
            }
            CatalogImportResult.Imported(manifest.generatedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CatalogImportResult.Failed(
                listOf(ImportError.WriteFailed(e.message ?: e.javaClass.name))
            )
        }
    }

    private suspend fun write(manifest: Manifest) {
        // Retire everything, then upsert the manifest's games and tracks as not retired: anything
        // missing stays retired and anything reappearing is un-retired. This avoids
        // `NOT IN (:ids)`, which hits SQLite's 999-variable limit on API 26 for a large catalog.
        // Retired tracks keep their last games subset and sources.
        catalogDao.retireAllGames()
        catalogDao.retireAllTracks()
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
        for (track in manifest.tracks) {
            track.replaces.forEach { oldId -> migrateUserData(oldId, track.id) }
        }
        catalogDao.clearUnusedBrokenMarks()
    }

    /**
     * Re-points history and playlist entries (favorites included) from [oldId] to [newId]. If a
     * playlist holds both, they merge into one entry with the earlier addedAt. Done in Kotlin
     * rather than SQL because SQLite on API 26 (3.18) has no `ON CONFLICT DO UPDATE`.
     */
    private suspend fun migrateUserData(oldId: String, newId: String) {
        userDataDao.repointHistory(oldId, newId)
        for (old in userDataDao.playlistEntriesOf(oldId)) {
            val new = userDataDao.playlistEntry(old.playlistId, newId)
            userDataDao.deletePlaylistEntry(old)
            userDataDao.upsertPlaylistEntry(
                old.copy(trackId = newId, addedAt = minOf(old.addedAt, new?.addedAt ?: old.addedAt))
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
