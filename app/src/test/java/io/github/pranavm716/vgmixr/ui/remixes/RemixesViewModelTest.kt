package io.github.pranavm716.vgmixr.ui.remixes

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.model.Source
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrack
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakeUserTrackRepository
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RemixesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val gold = game("gold", familyId = "gsc", shortName = "G")

    private fun userTrack(
        videoId: String,
        title: String,
        familyId: String? = null,
        unplayable: BrokenReason? = null,
        thumbnail: String? = null
    ) = CatalogTrack(
        id = "yt:$videoId",
        familyId = familyId,
        games = if (familyId != null) listOf(gold) else emptyList(),
        title = title,
        order = 0,
        sources = listOf(Source.Stream(videoId, 0, null)),
        unplayable = unplayable,
        channel = "GlitchxCity",
        thumbnail = thumbnail
    )

    // Newest first, as the catalog lists them.
    private val catalog = Catalog(
        games = listOf(gold),
        families = listOf(Family("gsc", "Gold", "G")),
        tracks = emptyList(),
        userTracks = listOf(
            userTrack(
                "AAAAAAAAAAA",
                "Olivine Lighthouse (Remix)",
                familyId = "gsc",
                thumbnail = File("thumbnails/AAAAAAAAAAA.jpg").path
            ),
            userTrack("BBBBBBBBBBB", "Broken Remix", unplayable = BrokenReason.UNAVAILABLE),
            userTrack("CCCCCCCCCCC", "Some Remix")
        )
    )

    private val playback = FakePlaybackController()
    private val messages = UserMessages()

    private val someRemix = UserTrack(
        videoId = "CCCCCCCCCCC",
        title = "Some Remix",
        channel = "GlitchxCity",
        durationMs = null,
        addedAt = Instant.EPOCH
    )
    private val userTracks = FakeUserTrackRepository(listOf(someRemix))

    private fun viewModel(catalog: Catalog = this.catalog) = RemixesViewModel(
        FakeCatalogRepository(catalog),
        playback,
        messages,
        userTracks,
        main.dispatcher
    )

    @Test
    fun `lists user tracks newest first, with their original's chips, channel and thumbnail`() =
        runTest {
            viewModel().uiState.test {
                assertEquals(
                    RemixesUiState.Loaded(
                        listOf(
                            RemixRowUi(
                                "yt:AAAAAAAAAAA",
                                "Olivine Lighthouse (Remix)",
                                listOf(ChipUi("G", "#336699")),
                                channel = "GlitchxCity",
                                thumbnail = File("thumbnails/AAAAAAAAAAA.jpg").path
                            ),
                            RemixRowUi(
                                "yt:BBBBBBBBBBB",
                                "Broken Remix",
                                emptyList(),
                                channel = "GlitchxCity",
                                thumbnail = null,
                                unplayable = true
                            ),
                            RemixRowUi(
                                "yt:CCCCCCCCCCC",
                                "Some Remix",
                                emptyList(),
                                channel = "GlitchxCity",
                                thumbnail = null
                            )
                        )
                    ),
                    awaitItem()
                )
            }
        }

    @Test
    fun `marks the playing track`() = runTest {
        playback.state.value = PlaybackState(trackId = "yt:CCCCCCCCCCC", isPlaying = true)

        viewModel().uiState.test {
            val rows = (awaitItem() as RemixesUiState.Loaded).rows
            assertEquals(listOf(false, false, true), rows.map { it.playing })
        }
    }

    @Test
    fun `no user tracks is an empty list`() = runTest {
        viewModel(catalog.copy(userTracks = emptyList())).uiState.test {
            assertEquals(RemixesUiState.Loaded(emptyList()), awaitItem())
        }
    }

    @Test
    fun `a tap plays Remixes from that track, leaving out tracks that can't play`() = runTest {
        viewModel().onTrackClick("yt:CCCCCCCCCCC")

        assertEquals(
            listOf(
                FakePlaybackController.PlayFrom(
                    listOf("yt:AAAAAAAAAAA", "yt:CCCCCCCCCCC"),
                    index = 1,
                    context = QueueContext.Remixes
                )
            ),
            playback.calls
        )
    }

    @Test
    fun `a tap on a greyed track says why it can't play, and plays nothing`() = runTest {
        messages.messages.test {
            viewModel().onTrackClick("yt:BBBBBBBBBBB")

            assertEquals(
                UserMessage.TrackUnplayable(
                    "yt:BBBBBBBBBBB",
                    "Broken Remix",
                    BrokenReason.UNAVAILABLE
                ),
                awaitItem()
            )
            assertEquals(emptyList<Any>(), playback.calls)
        }
    }

    @Test
    fun `swiping a track queues it and says so`() = runTest {
        messages.messages.test {
            viewModel().onQueueTrack("yt:CCCCCCCCCCC")

            assertEquals(
                listOf(FakePlaybackController.AddToQueue(listOf("yt:CCCCCCCCCCC"))),
                playback.calls
            )
            assertEquals(UserMessage.AddedToQueue(1), awaitItem())
        }
    }

    @Test
    fun `a greyed track can't be queued`() = runTest {
        viewModel().onQueueTrack("yt:BBBBBBBBBBB")

        assertEquals(emptyList<Any>(), playback.calls)
    }

    @Test
    fun `the menu's Delete turns delete mode on, and Done turns it off`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            assertEquals(false, (awaitItem() as RemixesUiState.Loaded).deleting)
            viewModel.onStartDeleting()
            assertEquals(true, (awaitItem() as RemixesUiState.Loaded).deleting)
            viewModel.onDone()
            assertEquals(false, (awaitItem() as RemixesUiState.Loaded).deleting)
        }
    }

    @Test
    fun `deleting a remix removes it at once and offers to undo it`() = runTest {
        messages.messages.test {
            viewModel().onDelete("yt:CCCCCCCCCCC")

            assertEquals(UserMessage.RemixDeleted(someRemix, "Some Remix"), awaitItem())
            assertEquals(emptyList<UserTrack>(), userTracks.userTracks.first())
        }
    }

    @Test
    fun `the menu's Link turns link mode on, and Done turns it off`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            assertEquals(false, (awaitItem() as RemixesUiState.Loaded).linking)
            viewModel.onStartLinking()
            val linking = awaitItem() as RemixesUiState.Loaded
            assertEquals(true, linking.linking)
            assertEquals(false, linking.deleting)
            viewModel.onDone()
            assertEquals(false, (awaitItem() as RemixesUiState.Loaded).linking)
        }
    }
}
