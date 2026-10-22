package io.github.pranavm716.vgmixr.domain.playback

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Why a stream source was marked broken. [key] is its name in broken.json (docs/broken.md). */
enum class BrokenReason(val key: String) {
    /** YouTube says the video can't play: removed, private, restricted. */
    UNAVAILABLE("unavailable"),

    /**
     * The video resolves but offers no audio the app can play. This is how YouTube refusing a
     * video to the extractor's client shows.
     */
    NO_AUDIO("no-audio");

    companion object {
        fun fromKey(key: String): BrokenReason? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A stream video found broken, for every slice of it. [trackId] is the track that found it,
 * [catalogGeneratedAt] the catalog it played from (null before the first import).
 */
data class BrokenMark(
    val videoId: String,
    val trackId: String,
    val reason: BrokenReason,
    val detail: String?,
    val markedAt: Instant,
    val catalogGeneratedAt: String?
)

/** Broken marks, runtime state keyed by video id (see docs/manifest.md, import rule 7). */
interface BrokenSources {
    /** Every mark, by video id. */
    val marks: Flow<Map<String, BrokenMark>>

    /**
     * Marks [videoId] broken; true if it wasn't marked yet. A video already marked keeps its first
     * mark.
     */
    suspend fun mark(
        videoId: String,
        trackId: String,
        reason: BrokenReason,
        detail: String?
    ): Boolean

    /**
     * Clears the marks of [trackId]'s videos (Try again), so its next play tries them again. A
     * video other tracks share is cleared for them too.
     */
    suspend fun clearTrack(trackId: String)
}
