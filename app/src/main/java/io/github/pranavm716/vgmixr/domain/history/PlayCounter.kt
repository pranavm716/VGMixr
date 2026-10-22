package io.github.pranavm716.vgmixr.domain.history

/**
 * Decides when a visit to a track counts as a play: once it has played [thresholdMs], or when it
 * ends sooner after its audio started (a short jingle). A visit counts at most once, so a restart
 * or seek within it doesn't count again; a track skipped before its audio started never counts.
 * The playback service feeds it and records what it returns.
 */
class PlayCounter(private val thresholdMs: Long = PLAY_THRESHOLD_MS) {
    /** Why the player moved to an item, as Media3 reports it. */
    enum class Transition {
        /** The previous item ended. */
        AUTO,

        /** A skip or a jump within the queue. */
        SKIP,

        /** The same item again, by repeat. */
        REPEAT,

        /** The queue was replaced or edited, which includes failover swapping an item's source. */
        QUEUE_CHANGED
    }

    private var trackId: String? = null
    private var started = false
    private var counted = false

    /** The player moved to [trackId]'s item (null: nothing). */
    fun onTransition(trackId: String?, reason: Transition) {
        // Failover replaces the current item with the same track's next source: the same visit.
        if (reason == Transition.QUEUE_CHANGED && trackId != null && trackId == this.trackId) return
        this.trackId = trackId
        started = false
        counted = false
    }

    /** The current item plays, at [positionMs]. Returns the track to record, once per visit. */
    fun onPlaying(positionMs: Long): String? {
        if (trackId == null) return null
        started = true
        return if (positionMs >= thresholdMs) count() else null
    }

    /** The current item played to its end. Returns the track to record, if it hadn't counted. */
    fun onEnded(): String? = if (started) count() else null

    private fun count(): String? {
        if (counted) return null
        counted = true
        return trackId
    }
}

/** How long a track must play to count as a play (see docs/roadmap.md, step 11). */
const val PLAY_THRESHOLD_MS = 10_000L
