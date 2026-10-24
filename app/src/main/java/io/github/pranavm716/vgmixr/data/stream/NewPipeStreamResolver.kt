package io.github.pranavm716.vgmixr.data.stream

import io.github.pranavm716.vgmixr.di.IoDispatcher
import io.github.pranavm716.vgmixr.domain.playback.PlaybackDiagnostics
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.playback.StreamResolution
import io.github.pranavm716.vgmixr.domain.playback.StreamResolver
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.stream.StreamInfo

@Singleton
class NewPipeStreamResolver @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
    private val diagnostics: PlaybackDiagnostics = PlaybackDiagnostics.NONE
) : StreamResolver {
    init {
        NewPipeSetup.ensure()
    }

    override suspend fun resolve(videoId: String): StreamResolution = withContext(io) {
        val start = System.nanoTime()
        try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, "$WATCH_URL$videoId")
            val stream = pickAudioStream(info.audioStreams)
            val ms = (System.nanoTime() - start) / 1_000_000
            if (stream == null) {
                diagnostics.log("resolve $videoId: no playable audio stream (${ms}ms)")
                StreamResolution.NoAudio
            } else {
                diagnostics.log(
                    "resolve $videoId: ${stream.format?.mimeType} ${stream.averageBitrate}kbps " +
                        "(${ms}ms)"
                )
                StreamResolution.Resolved(
                    stream.content,
                    // Never null: the picker keeps only known formats.
                    stream.format?.mimeType.orEmpty(),
                    stream.averageBitrate
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toResolution(e).also { diagnostics.log("resolve $videoId: $it from ${e.describe()}") }
        }
    }
}

internal const val WATCH_URL = "https://www.youtube.com/watch?v="

/** Tells a video that can't play (try the next source) from a failure that stops everything. */
fun toResolution(e: Exception): StreamResolution = when (e) {
    is ContentNotAvailableException, is ContentNotSupportedException ->
        StreamResolution.Unavailable(e.message ?: e.javaClass.simpleName)

    is ReCaptchaException, is SignInConfirmNotBotException ->
        StreamResolution.Failed(ResolveFailure.BOT_CHECK)

    is IOException -> StreamResolution.Failed(ResolveFailure.NETWORK)

    else -> StreamResolution.Failed(ResolveFailure.EXTRACTOR)
}

/** An exception and its causes, one line each joined by " <- ", for the diagnostics log. */
fun Throwable.describe(): String = generateSequence(this) { it.cause }
    .take(5)
    .joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }
