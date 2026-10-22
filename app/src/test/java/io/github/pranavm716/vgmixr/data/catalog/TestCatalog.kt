package io.github.pranavm716.vgmixr.data.catalog

import io.github.pranavm716.vgmixr.data.db.UserDataDao
import io.github.pranavm716.vgmixr.data.db.VGMixrDatabase
import io.github.pranavm716.vgmixr.domain.manifest.ManifestParseResult
import io.github.pranavm716.vgmixr.domain.model.Game
import io.github.pranavm716.vgmixr.domain.model.Manifest
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.model.Track
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Small builders for valid manifests, so each test states only what it's about. */
object TestCatalog {
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)

    fun game(
        id: String,
        familyId: String = "rse",
        name: String = id,
        shortName: String = id.take(1).uppercase(),
        generation: Int? = 3
    ) = Game(
        id = id,
        name = name,
        shortName = shortName,
        color = "#336699",
        generation = generation,
        platform = "GBA",
        familyId = familyId
    )

    fun track(
        id: String,
        familyId: String = "rse",
        games: List<String>? = null,
        title: String = id,
        order: Int = 0,
        sources: List<Source> = listOf(Source.Stream("AAAAAAAAAAA", 0, null)),
        replaces: List<String> = emptyList()
    ) = Track(
        id = id,
        familyId = familyId,
        games = games,
        title = title,
        order = order,
        sources = sources,
        replaces = replaces
    )

    val rse = listOf(game("ruby"), game("sapphire"), game("emerald"))

    fun manifest(
        generatedAt: String = "2026-10-13T08:00:00Z",
        games: List<Game> = rse,
        tracks: List<Track> = listOf(track("rse-route-101"))
    ) = ManifestParseResult.Valid(Manifest(Instant.parse(generatedAt), games, tracks))

    fun importer(db: VGMixrDatabase, userDataDao: UserDataDao = db.userDataDao()) =
        CatalogImporter(db, db.catalogDao(), userDataDao, db.importStateDao(), clock)
}
