package io.github.pranavm716.vgmixr.testing

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogImportResult
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogSync
import io.github.pranavm716.vgmixr.domain.playback.BrokenMark
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.BrokenSources
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.PlaybackEvent
import io.github.pranavm716.vgmixr.domain.playback.PlaybackProgress
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.TryAgainResult
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCatalogRepository(initial: Catalog = Catalog(emptyList(), emptyList(), emptyList())) :
    CatalogRepository {
    override val catalog = MutableStateFlow(initial)
}

class FakeCatalogSync(initial: CatalogImportResult? = null) : CatalogSync {
    override val status = MutableStateFlow(initial)
}

class FakePlaybackController : PlaybackController {
    data class PlayFrom(val trackIds: List<String>, val index: Int, val context: QueueContext)

    data class AddToQueue(val trackIds: List<String>)

    data object TogglePlayPause

    data object SkipToNext

    data object SkipToPrevious

    data object RestartOrSkipToPrevious

    data class SeekTo(val positionMs: Long)

    data class JumpTo(val index: Int)

    data class RemoveFromQueue(val index: Int)

    data object ClearQueue

    data class TryAgain(val trackId: String)

    override val state = MutableStateFlow(PlaybackState())
    override val progress = MutableStateFlow(PlaybackProgress(positionMs = 0, durationMs = null))
    override val events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
    val calls = mutableListOf<Any>()

    override fun togglePlayPause() {
        calls += TogglePlayPause
    }

    override fun skipToNext() {
        calls += SkipToNext
    }

    override fun skipToPrevious() {
        calls += SkipToPrevious
    }

    override fun restartOrSkipToPrevious() {
        calls += RestartOrSkipToPrevious
    }

    override fun seekTo(positionMs: Long) {
        calls += SeekTo(positionMs)
    }

    override fun jumpTo(index: Int) {
        calls += JumpTo(index)
    }

    override fun removeFromQueue(index: Int) {
        calls += RemoveFromQueue(index)
    }

    override fun clearQueue() {
        calls += ClearQueue
    }

    var tryAgainResult: TryAgainResult = TryAgainResult.Back

    override suspend fun tryAgain(trackId: String): TryAgainResult {
        calls += TryAgain(trackId)
        return tryAgainResult
    }

    override fun playFrom(trackIds: List<String>, index: Int, context: QueueContext) {
        calls += PlayFrom(trackIds, index, context)
    }

    override fun addToQueue(trackIds: List<String>) {
        calls += AddToQueue(trackIds)
    }
}

class FakeBrokenSources(initial: Map<String, BrokenMark> = emptyMap()) : BrokenSources {
    override val marks = MutableStateFlow(initial)

    val marked = mutableListOf<BrokenMark>()

    override suspend fun mark(
        videoId: String,
        trackId: String,
        reason: BrokenReason,
        detail: String?
    ): Boolean {
        if (videoId in marks.value) return false
        val mark = BrokenMark(videoId, trackId, reason, detail, Instant.EPOCH, null)
        marked += mark
        marks.value += videoId to mark
        return true
    }

    override suspend fun clear(videoId: String) {
        marks.value -= videoId
    }
}
