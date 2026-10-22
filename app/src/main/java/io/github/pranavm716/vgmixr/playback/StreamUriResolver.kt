package io.github.pranavm716.vgmixr.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import io.github.pranavm716.vgmixr.domain.playback.BrokenMark
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.PlaybackDiagnostics
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.playback.StreamResolution
import io.github.pranavm716.vgmixr.domain.playback.StreamResolver
import java.io.IOException
import kotlinx.coroutines.runBlocking

/** This source can't play; the track's next source may. */
open class SourceUnavailableException(reason: String) : IOException(reason)

/** This source's video is dead for the app, for [reason]: it gets marked broken. */
class BrokenStreamException(val reason: BrokenReason, val detail: String?) :
    SourceUnavailableException(detail ?: reason.key)

/** Nothing can resolve right now, whatever the source. */
class ResolveFailedException(val failure: ResolveFailure) : IOException(failure.name)

/**
 * Turns a `vgmixr://stream/<videoId>` URI into the video's audio URL when the player opens it.
 * Runs on the player's loading thread, so it may block. A video with a [brokenMark] fails at once,
 * without asking YouTube.
 */
@UnstableApi
class StreamUriResolver(
    private val resolver: StreamResolver,
    private val diagnostics: PlaybackDiagnostics = PlaybackDiagnostics.NONE,
    private val brokenMark: (videoId: String) -> BrokenMark? = { null }
) : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri.toString()
        if (uri == NO_SOURCE_URI) throw SourceUnavailableException("no playable source")
        if (!uri.startsWith(STREAM_URI_PREFIX)) return dataSpec
        val videoId = uri.removePrefix(STREAM_URI_PREFIX)
        brokenMark(videoId)?.let {
            diagnostics.log("open $videoId: marked broken (${it.reason.key})")
            throw BrokenStreamException(it.reason, it.detail)
        }
        val result = runBlocking { resolver.resolve(videoId) }
        diagnostics.log(
            "open $videoId at byte ${dataSpec.position}: ${result.javaClass.simpleName}"
        )
        return when (result) {
            is StreamResolution.Resolved -> dataSpec.withUri(Uri.parse(result.url))

            is StreamResolution.Unavailable ->
                throw BrokenStreamException(BrokenReason.UNAVAILABLE, result.reason)

            StreamResolution.NoAudio ->
                throw BrokenStreamException(BrokenReason.NO_AUDIO, "no playable audio stream")

            is StreamResolution.Failed -> throw ResolveFailedException(result.failure)
        }
    }
}
