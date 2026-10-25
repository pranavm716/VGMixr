package io.github.pranavm716.vgmixr.data.stream

import io.github.pranavm716.vgmixr.di.IoDispatcher
import io.github.pranavm716.vgmixr.domain.playback.PlaybackDiagnostics
import io.github.pranavm716.vgmixr.domain.playback.StreamResolution
import io.github.pranavm716.vgmixr.domain.usertrack.VideoDetails
import io.github.pranavm716.vgmixr.domain.usertrack.VideoLookup
import io.github.pranavm716.vgmixr.domain.usertrack.VideoLookupResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

/** The same request playback makes, so a video it finds is one the player can play. */
@Singleton
class NewPipeVideoLookup @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
    private val diagnostics: PlaybackDiagnostics = PlaybackDiagnostics.NONE
) : VideoLookup {
    init {
        NewPipeSetup.ensure()
    }

    override suspend fun lookUp(videoId: String): VideoLookupResult = withContext(io) {
        try {
            lookupResult(StreamInfo.getInfo(ServiceList.YouTube, "$WATCH_URL$videoId"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lookupFailure(e)
        }.also { diagnostics.log("look up $videoId: $it") }
    }
}

/** What a looked-up video is, or [VideoLookupResult.NoAudio] if no stream would play. */
fun lookupResult(info: StreamInfo): VideoLookupResult =
    if (pickAudioStream(info.audioStreams) == null) {
        VideoLookupResult.NoAudio
    } else {
        VideoLookupResult.Found(
            VideoDetails(
                title = info.name,
                // An artist's auto-generated YouTube Music channel ("GlitchxCity - Topic") is
                // named after the artist.
                channel = info.uploaderName.orEmpty().removeSuffix(TOPIC_SUFFIX),
                durationMs = info.duration.takeIf { it > 0 }?.let { it * 1_000 }
            )
        )
    }

/** A failed lookup, told apart as playback tells failed resolutions apart. */
fun lookupFailure(e: Exception): VideoLookupResult = when (val resolution = toResolution(e)) {
    is StreamResolution.Unavailable -> VideoLookupResult.Unavailable(resolution.reason)

    is StreamResolution.Failed -> VideoLookupResult.Failed(resolution.failure)

    // toResolution gives only the two above for an exception.
    StreamResolution.NoAudio -> VideoLookupResult.NoAudio

    is StreamResolution.Resolved -> error("an exception never resolves")
}

private const val TOPIC_SUFFIX = " - Topic"
