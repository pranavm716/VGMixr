package io.github.pranavm716.vgmixr.data.manifest

import io.github.pranavm716.vgmixr.domain.manifest.ManifestError
import java.time.Instant

/**
 * Checks a decoded manifest against the rules in docs/manifest.md and returns every error found.
 * Each rule reports once per offending object, so one defect never yields two errors.
 */
internal object ManifestValidator {
    private val slug = Regex("[a-z0-9-]+")
    private val color = Regex("#[0-9A-Fa-f]{6}")
    private val videoId = Regex("[A-Za-z0-9_-]{11}")
    private const val SHORT_NAME_MAX = 12

    fun validate(manifest: ManifestDto): List<ManifestError> = buildList {
        if (!isUtcInstant(manifest.generatedAt)) {
            add(ManifestError.GeneratedAtInvalid(manifest.generatedAt))
        }
        addAll(validateGames(manifest.games))
        addAll(validateFamilies(manifest.games))
        addAll(validateTracks(manifest.tracks, manifest.games))
    }

    // Instant.parse accepts offsets like +02:00 on newer JDKs, so require the Z explicitly.
    private fun isUtcInstant(value: String): Boolean =
        value.endsWith("Z") && runCatching { Instant.parse(value) }.isSuccess

    private fun validateGames(games: List<GameDto>): List<ManifestError> = buildList {
        duplicates(games.map { it.id }).forEach { add(ManifestError.GameIdDuplicate(it)) }
        for (game in games) {
            if (!slug.matches(game.id)) add(ManifestError.GameSlugInvalid(game.id, game.id))
            game.family?.let {
                if (!slug.matches(it)) add(ManifestError.GameSlugInvalid(game.id, it))
            }
            if (game.name.isEmpty()) add(ManifestError.GameFieldEmpty(game.id, "name"))
            if (game.platform.isEmpty()) add(ManifestError.GameFieldEmpty(game.id, "platform"))
            game.generation?.let {
                if (it <
                    1
                ) {
                    add(ManifestError.GameGenerationInvalid(game.id, it))
                }
            }
            // Code points, not UTF-16 units, to match Python's len().
            val shortNameLength = game.shortName.codePointCount(0, game.shortName.length)
            if (shortNameLength !in 1..SHORT_NAME_MAX) {
                add(ManifestError.GameShortNameInvalid(game.id, game.shortName))
            }
            if (!color.matches(game.color)) add(ManifestError.GameColorInvalid(game.id, game.color))
        }
    }

    private fun validateFamilies(games: List<GameDto>): List<ManifestError> = buildList {
        games.groupBy { it.familyId }.forEach { (familyId, members) ->
            // Null counts as a value: some games with a generation and some without is a mismatch.
            if (members.map { it.generation }.distinct().size > 1) {
                add(ManifestError.FamilyGenerationMismatch(familyId))
            }
        }
        games.mapNotNull { it.family }.distinct().forEach { family ->
            if (games.any { it.id == family && it.familyId != family }) {
                add(ManifestError.FamilyIdCollision(family))
            }
        }
    }

    private fun validateTracks(tracks: List<TrackDto>, games: List<GameDto>): List<ManifestError> =
        buildList {
            val gameIdsByFamily = games.groupBy({ it.familyId }, { it.id })
            val trackIds = tracks.map { it.id }.toSet()
            duplicates(tracks.map { it.id }).forEach { add(ManifestError.TrackIdDuplicate(it)) }
            for (track in tracks) {
                if (!slug.matches(track.id)) add(ManifestError.TrackIdInvalid(track.id))
                if (track.title.isEmpty()) add(ManifestError.TrackTitleEmpty(track.id))
                if (track.order < 0) add(ManifestError.TrackOrderInvalid(track.id, track.order))
                val familyGameIds = gameIdsByFamily[track.family]
                if (familyGameIds == null) {
                    add(ManifestError.TrackFamilyUnknown(track.id, track.family))
                }
                track.games?.let { trackGames ->
                    // Membership is only checked against a known family, so an unknown family
                    // isn't reported twice.
                    val outside = familyGameIds != null && trackGames.any { it !in familyGameIds }
                    if (trackGames.isEmpty() || trackGames.distinct().size != trackGames.size ||
                        outside
                    ) {
                        add(ManifestError.TrackGamesInvalid(track.id))
                    }
                }
                if (track.sources.isEmpty()) add(ManifestError.TrackSourcesEmpty(track.id))
                addAll(validateSources(track))
                track.replaces
                    .filter { !slug.matches(it) || it in trackIds }
                    .forEach { add(ManifestError.TrackReplacesInvalid(track.id, it)) }
            }
            addAll(validateReplacedOnce(tracks))
        }

    /** One error per id replaced by more than one track, reported on the second track to list it. */
    private fun validateReplacedOnce(tracks: List<TrackDto>): List<ManifestError> {
        val firstReplacer = mutableMapOf<String, String>()
        val reported = mutableSetOf<String>()
        return buildList {
            for (track in tracks) {
                for (replaced in track.replaces.distinct()) {
                    val first = firstReplacer.getOrPut(replaced) { track.id }
                    if (first != track.id && reported.add(replaced)) {
                        add(ManifestError.TrackReplacesInvalid(track.id, replaced))
                    }
                }
            }
        }
    }

    private fun validateSources(track: TrackDto): List<ManifestError> = buildList {
        val seen = mutableSetOf<Any>()
        track.sources.forEachIndexed { index, source ->
            source.identity()?.let {
                if (!seen.add(it)) add(ManifestError.SourceIdentityDuplicate(track.id, index))
            }
            when (source) {
                is LocalSourceDto -> {
                    if (!isValidPath(source.path)) {
                        add(ManifestError.LocalPathInvalid(track.id, index, source.path))
                    }
                    source.loop?.let {
                        if (!it.isValid()) add(ManifestError.LoopInvalid(track.id, index))
                    }
                }

                is StreamSourceDto -> {
                    val endBeforeStart = source.endMs != null && source.endMs <= source.startMs
                    if (!videoId.matches(source.videoId) || source.startMs < 0 || endBeforeStart) {
                        add(ManifestError.StreamInvalid(track.id, index))
                    }
                    source.confidence?.let {
                        if (it !in
                            0.0..1.0
                        ) {
                            add(ManifestError.StreamConfidenceInvalid(track.id, index, it))
                        }
                    }
                    if (source.loop != null) add(ManifestError.StreamLoopPresent(track.id, index))
                }

                is UnknownSourceDto -> add(
                    ManifestError.SourceTypeUnknown(track.id, index, source.type)
                )
            }
        }
    }

    private fun isValidPath(path: String): Boolean =
        path.isNotEmpty() && !path.startsWith("/") && '\\' !in path && ".." !in path.split('/')

    private fun LoopDto.isValid(): Boolean = sampleRate > 0 && introEnd >= 0 && loopEnd > introEnd

    /** Runtime source state is keyed by this, so two sources in a track must not share it. */
    private fun SourceDto.identity(): Any? = when (this) {
        is LocalSourceDto -> path
        is StreamSourceDto -> Triple(videoId, startMs, endMs)
        is UnknownSourceDto -> null
    }

    private fun duplicates(ids: List<String>): Set<String> =
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
}

/** A standalone game (no `family`) is its own family. */
internal val GameDto.familyId: String get() = family ?: id
