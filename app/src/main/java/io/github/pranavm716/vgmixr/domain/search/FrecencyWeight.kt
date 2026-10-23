package io.github.pranavm716.vgmixr.domain.search

import io.github.pranavm716.vgmixr.domain.history.Play
import java.time.Duration
import java.time.Instant
import kotlin.math.pow

/**
 * What search ranking knows about the user's listening: every [plays], the tracks [inPlaylists]
 * (Favorites included) and the [liftedFamilies] (the one playing and the one last browsed).
 */
data class RankingSignals(
    val plays: List<Play> = emptyList(),
    val inPlaylists: Set<String> = emptySet(),
    val liftedFamilies: Set<String> = emptySet()
)

/**
 * Frecency, as in Firefox's address bar: each play adds 1, halved for every [PLAY_HALF_LIFE] of
 * its age at [now]; a track in a playlist adds [PLAYLIST_BONUS], one of a lifted family
 * [FAMILY_LIFT]. The plays are summed once, so a search asks only a map per match.
 */
fun frecencyWeight(signals: RankingSignals, now: Instant): HistoryWeight {
    val halfLifeMs = PLAY_HALF_LIFE.toMillis().toDouble()
    val plays = HashMap<String, Double>()
    for (play in signals.plays) {
        // A play stamped after now (the clock moved back) counts as a fresh one.
        val ageMs = Duration.between(play.playedAt, now).toMillis().coerceAtLeast(0)
        plays.merge(play.trackId, 0.5.pow(ageMs / halfLifeMs), Double::plus)
    }
    return HistoryWeight { track ->
        (plays[track.id] ?: 0.0) +
            (if (track.id in signals.inPlaylists) PLAYLIST_BONUS else 0.0) +
            (if (track.familyId in signals.liftedFamilies) FAMILY_LIFT else 0.0)
    }
}

/** Two weeks: what was played this month still counts, what was played last season barely. */
val PLAY_HALF_LIFE: Duration = Duration.ofDays(14)

/**
 * Three fresh plays: adding a track is a choice, while plays also come from letting a whole
 * family play through.
 */
const val PLAYLIST_BONUS = 3.0

/** Half a fresh play: the family on screen or in the ears wins over plays older than two weeks. */
const val FAMILY_LIFT = 0.5
