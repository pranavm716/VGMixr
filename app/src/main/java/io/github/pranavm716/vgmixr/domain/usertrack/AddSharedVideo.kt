package io.github.pranavm716.vgmixr.domain.usertrack

import io.github.pranavm716.vgmixr.domain.playback.BrokenSources
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** What sharing a video to the app did. */
sealed interface AddVideoResult {
    /** The video is now user track [trackId]. */
    data class Added(val trackId: String, val title: String) : AddVideoResult

    /** The video was added before; nothing changed. */
    data class AlreadyAdded(val trackId: String, val title: String) : AddVideoResult

    /** The shared text holds no YouTube video link. */
    data object NotAVideoLink : AddVideoResult

    /** YouTube gives the app no audio it can play for this video. */
    data object NoAudio : AddVideoResult

    /** The video is removed, private or restricted. */
    data class Unavailable(val reason: String) : AddVideoResult

    /** YouTube couldn't be asked (network, bot check, extractor). */
    data class Failed(val failure: ResolveFailure) : AddVideoResult
}

/**
 * Adds the video in text shared to the app as a user track, as YouTube has it now, remixing
 * nothing yet. One lookup per new video, none for a video already added; a video the app can't
 * play isn't added. Its thumbnail comes along if it can be downloaded. The lookup is the request
 * playback makes, so a video found playable loses any broken mark left from an earlier add.
 */
class AddSharedVideo @Inject constructor(
    private val lookup: VideoLookup,
    private val userTracks: UserTrackRepository,
    private val thumbnails: ThumbnailStore,
    private val broken: BrokenSources,
    private val clock: Clock
) {
    suspend operator fun invoke(sharedText: String): AddVideoResult {
        val videoId = sharedVideoId(sharedText) ?: return AddVideoResult.NotAVideoLink
        userTracks.userTracks.first().firstOrNull { it.videoId == videoId }?.let {
            return AddVideoResult.AlreadyAdded(it.id, it.title)
        }
        val details = when (val result = lookup.lookUp(videoId)) {
            is VideoLookupResult.Found -> result.details
            VideoLookupResult.NoAudio -> return AddVideoResult.NoAudio
            is VideoLookupResult.Unavailable -> return AddVideoResult.Unavailable(result.reason)
            is VideoLookupResult.Failed -> return AddVideoResult.Failed(result.failure)
        }
        broken.clear(videoId)
        // Before the track, so it shows with its thumbnail; a missing one is fetched again later.
        thumbnails.download(videoId)
        val track = UserTrack(
            videoId = videoId,
            title = details.title,
            channel = details.channel,
            durationMs = details.durationMs,
            addedAt = clock.instant()
        )
        // Shared twice at once: the second add finds the first one's row.
        return if (userTracks.add(track)) {
            AddVideoResult.Added(track.id, track.title)
        } else {
            AddVideoResult.AlreadyAdded(track.id, track.title)
        }
    }
}
