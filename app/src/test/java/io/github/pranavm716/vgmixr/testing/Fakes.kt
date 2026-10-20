package io.github.pranavm716.vgmixr.testing

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogImportResult
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogSync
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
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
    data class Play(val trackIds: List<String>, val startIndex: Int)

    override val state = MutableStateFlow(PlaybackState())
    val calls = mutableListOf<Any>()

    override fun play(trackIds: List<String>, startIndex: Int) {
        calls += Play(trackIds, startIndex)
    }
}
