package io.github.pranavm716.vgmixr.ui.remixes

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.sort.SortDirection
import io.github.pranavm716.vgmixr.domain.sort.SortKey
import io.github.pranavm716.vgmixr.domain.sort.SortKeys
import io.github.pranavm716.vgmixr.domain.sort.SortSetting
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakePlaybackController.PlayAll
import io.github.pranavm716.vgmixr.testing.FakePlaybackController.PlayFrom
import io.github.pranavm716.vgmixr.testing.FakePlaybackController.Resort
import io.github.pranavm716.vgmixr.testing.FakeSortSettings
import io.github.pranavm716.vgmixr.testing.FakeUserTrackRepository
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.components.SortUi
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RemixesSortTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun remix(videoId: String, channel: String, added: Long) = CatalogTrack(
        id = "yt:$videoId",
        familyId = null,
        games = emptyList(),
        title = videoId,
        order = 0,
        sources = listOf(Source.Stream(videoId, 0, null)),
        channel = channel,
        addedAt = Instant.ofEpochSecond(added)
    )

    // Newest first, as the catalog lists them.
    private val catalog = Catalog(
        games = emptyList(),
        families = emptyList(),
        tracks = emptyList(),
        userTracks = listOf(
            remix("CCCCCCCCCCC", "Beta", 3),
            remix("BBBBBBBBBBB", "Gamma", 2),
            remix("AAAAAAAAAAA", "Alpha", 1)
        )
    )
    private val playback = FakePlaybackController()
    private val sortSettings = FakeSortSettings()
    private val byChannel = SortSetting(SortKey.Channel, SortDirection.Ascending)
    private val channelOrder = listOf("yt:AAAAAAAAAAA", "yt:CCCCCCCCCCC", "yt:BBBBBBBBBBB")

    private fun viewModel() = RemixesViewModel(
        FakeCatalogRepository(catalog),
        playback,
        UserMessages(),
        FakeUserTrackRepository(),
        sortSettings,
        main.dispatcher
    )

    private suspend fun RemixesViewModel.loaded(): RemixesUiState.Loaded {
        var state: RemixesUiState.Loaded? = null
        uiState.test { state = awaitItem() as RemixesUiState.Loaded }
        return checkNotNull(state)
    }

    @Test
    fun `remixes start newest first and offer every remix key`() = runTest {
        val state = viewModel().loaded()

        assertEquals(
            listOf("yt:CCCCCCCCCCC", "yt:BBBBBBBBBBB", "yt:AAAAAAAAAAA"),
            state.rows.map { it.id }
        )
        assertEquals(SortUi(SortKeys.remixesDefault, SortKeys.remixes), state.sort)
    }

    @Test
    fun `picking channel saves it, sorts the list and tells the player`() = runTest {
        val viewModel = viewModel()

        viewModel.onSortKey(SortKey.Channel)

        assertEquals(byChannel, sortSettings.remixes.value)
        assertEquals(channelOrder, viewModel.loaded().rows.map { it.id })
        assertEquals(listOf(Resort(channelOrder, QueueContext.Remixes)), playback.calls)
    }

    @Test
    fun `the arrow reverses the direction`() = runTest {
        viewModel().onReverseSort()

        assertEquals(
            SortSetting(SortKey.RecentlyAdded, SortDirection.Ascending),
            sortSettings.remixes.value
        )
    }

    @Test
    fun `a tap and play all play remixes in their sort`() = runTest {
        sortSettings.setRemixes(byChannel)
        val viewModel = viewModel()

        viewModel.onTrackClick("yt:CCCCCCCCCCC")
        viewModel.onPlayAll()

        assertEquals(
            listOf(
                PlayFrom(channelOrder, 1, QueueContext.Remixes),
                PlayAll(channelOrder, QueueContext.Remixes)
            ),
            playback.calls
        )
    }
}
