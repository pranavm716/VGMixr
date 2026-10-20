package io.github.pranavm716.vgmixr.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.playback.StreamResolution
import io.github.pranavm716.vgmixr.domain.playback.StreamResolver
import java.io.IOException
import kotlinx.coroutines.runBlocking

/** This source can't play; the track's next source may. */
class SourceUnavailableException(reason: String) : IOException(reason)

/** Nothing can resolve right now, whatever the source. */
class ResolveFailedException(val failure: ResolveFailure) : IOException(failure.name)

/**
 * Turns a `vgmixr://stream/<videoId>` URI into the video's audio URL when the player opens it.
 * Runs on the player's loading thread, so it may block.
 */
@UnstableApi
class StreamUriResolver(private val resolver: StreamResolver) : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri.toString()
        if (uri == NO_SOURCE_URI) throw SourceUnavailableException("no playable source")
        if (!uri.startsWith(STREAM_URI_PREFIX)) return dataSpec
        val videoId = uri.removePrefix(STREAM_URI_PREFIX)
        return when (val result = runBlocking { resolver.resolve(videoId) }) {
            is StreamResolution.Resolved -> dataSpec.withUri(Uri.parse(result.url))
            is StreamResolution.Unavailable -> throw SourceUnavailableException(result.reason)
            is StreamResolution.Failed -> throw ResolveFailedException(result.failure)
        }
    }
}
