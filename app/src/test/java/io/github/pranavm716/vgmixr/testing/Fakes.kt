package io.github.pranavm716.vgmixr.testing

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogImportResult
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogSync
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.PlaybackProgress
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCatalogRepository(initial: Catalog = Catalog(emptyList(), emptyList(), emptyList())) :
    CatalogRepository {
    override val catalog = MutableStateFlow(initial)
}

class FakeCatalogSync(initial: CatalogImportResult? = null) : CatalogSync {
    override val status = MutableStateFlow(initial)
}

class FakePlaybackController : PlaybackController {
    data class PlayFrom(val trackIds: List<String>, val index: Int)

    data class AddToQueue(val trackIds: List<String>)

    data object TogglePlayPause

    data object SkipToNext

    data object SkipToPrevious

    override val state = MutableStateFlow(PlaybackState())
    override val progress = MutableStateFlow(PlaybackProgress(positionMs = 0, durationMs = null))
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

    override fun playFrom(trackIds: List<String>, index: Int) {
        calls += PlayFrom(trackIds, index)
    }

    override fun addToQueue(trackIds: List<String>) {
        calls += AddToQueue(trackIds)
    }
}
