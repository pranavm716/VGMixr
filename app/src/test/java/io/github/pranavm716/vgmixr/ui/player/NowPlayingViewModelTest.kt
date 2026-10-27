package io.github.pranavm716.vgmixr.ui.player

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playback.PlayQueue
import io.github.pranavm716.vgmixr.domain.playback.PlaybackProgress
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.QueueEntry
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakePlaylistRepository
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NowPlayingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val ruby = game("ruby", shortName = "R")
    private val sapphire = game("sapphire", shortName = "S")

    private val catalog = Catalog(
        games = listOf(ruby, sapphire),
        families = listOf(Family("rse", "Ruby / Sapphire", "RS")),
        tracks = listOf(
            CatalogTrack(
                "rse-a",
                "rse",
                listOf(ruby, sapphire),
                "Route 101",
                0,
                emptyList()
            )
        ),
        userTracks = listOf(
            // A remix of an RSE track, and a video that remixes nothing in the catalog.
            CatalogTrack("yt:AAAAAAAAAAA", "rse", listOf(ruby), "Route 101 Remix", 0, emptyList()),
            CatalogTrack("yt:BBBBBBBBBBB", null, emptyList(), "Some Remix", 0, emptyList())
        )
    )

    private val playback = FakePlaybackController()
    private val playlists = FakePlaylistRepository(
        initialPlaylists = listOf(PlaylistSummary(1, "Favorites", 1, isFavorites = true))
    )
    private val viewModel by lazy {
        NowPlayingViewModel(FakeCatalogRepository(catalog), playback, playlists)
    }

    private fun playing(
        context: QueueContext,
        isLoading: Boolean = false,
        trackId: String = "rse-a"
    ) = PlaybackState(
        queue = PlayQueue(listOf(QueueEntry(trackId, context)), current = 0),
        trackId = trackId,
        isPlaying = !isLoading,
        isLoading = isLoading
    )

    @Test
    fun `shows the track, its games, and the family it plays from`() = runTest {
        playback.state.value = playing(QueueContext.Family("rse"))

        viewModel.uiState.test {
            assertEquals(
                NowPlayingUiState.Visible(
                    title = "Route 101",
                    chips = listOf(ChipUi("R", "#336699"), ChipUi("S", "#336699")),
                    familyId = "rse",
                    playingFrom = PlayingFromUi.Family("rse", "Ruby / Sapphire"),
                    isPlaying = true,
                    isLoading = false
                ),
                awaitItem()
            )
        }
    }

    @Test
    fun `a swiped-in track plays from the queue, and loading shows`() = runTest {
        playback.state.value = playing(QueueContext.Queue, isLoading = true)

        viewModel.uiState.test {
            val state = awaitItem() as NowPlayingUiState.Visible
            assertEquals(PlayingFromUi.Queue, state.playingFrom)
            assertEquals(true, state.isLoading)
        }
    }

    @Test
    fun `a track tapped in a playlist plays from it`() = runTest {
        playback.state.value = playing(QueueContext.Playlist(1))

        viewModel.uiState.test {
            assertEquals(
                PlayingFromUi.Playlist(1, "Favorites"),
                (awaitItem() as NowPlayingUiState.Visible).playingFrom
            )
        }
    }

    @Test
    fun `a track playing from a playlist still knows its own family`() = runTest {
        playback.state.value = playing(QueueContext.Playlist(1))

        viewModel.uiState.test {
            assertEquals("rse", (awaitItem() as NowPlayingUiState.Visible).familyId)
        }
    }

    @Test
    fun `a track from a deleted playlist plays from its family`() = runTest {
        playback.state.value = playing(QueueContext.Playlist(9))

        viewModel.uiState.test {
            assertEquals(
                PlayingFromUi.Family("rse", "Ruby / Sapphire"),
                (awaitItem() as NowPlayingUiState.Visible).playingFrom
            )
        }
    }

    @Test
    fun `a remix tapped in Remixes plays from Remixes and knows its original's family`() = runTest {
        playback.state.value = playing(QueueContext.Remixes, trackId = "yt:AAAAAAAAAAA")

        viewModel.uiState.test {
            val state = awaitItem() as NowPlayingUiState.Visible
            assertEquals(PlayingFromUi.Remixes, state.playingFrom)
            assertEquals("rse", state.familyId)
            assertEquals(listOf(ChipUi("R", "#336699")), state.chips)
        }
    }

    @Test
    fun `a user track that remixes nothing has no family or chips`() = runTest {
        playback.state.value = playing(QueueContext.Remixes, trackId = "yt:BBBBBBBBBBB")

        viewModel.uiState.test {
            val state = awaitItem() as NowPlayingUiState.Visible
            assertEquals(null, state.familyId)
            assertEquals(emptyList<ChipUi>(), state.chips)
        }
    }

    @Test
    fun `a user track from a deleted playlist plays from Remixes, not its original's family`() =
        runTest {
            playback.state.value = playing(QueueContext.Playlist(9), trackId = "yt:AAAAAAAAAAA")

            viewModel.uiState.test {
                assertEquals(
                    PlayingFromUi.Remixes,
                    (awaitItem() as NowPlayingUiState.Visible).playingFrom
                )
            }
        }

    @Test
    fun `hidden while nothing plays`() = runTest {
        viewModel.uiState.test {
            assertEquals(NowPlayingUiState.Hidden, awaitItem())
        }
    }

    @Test
    fun `the heart shows whether the track is a favorite`() = runTest {
        playlists.favorites.value = setOf("rse-a")
        playback.state.value = playing(QueueContext.Family("rse"))

        viewModel.uiState.test {
            assertEquals(true, (awaitItem() as NowPlayingUiState.Visible).isFavorite)
        }
    }

    @Test
    fun `the heart favorites the playing track, and a second tap unfavorites it`() = runTest {
        playback.state.value = playing(QueueContext.Family("rse"))

        viewModel.uiState.test {
            assertEquals(false, (awaitItem() as NowPlayingUiState.Visible).isFavorite)

            viewModel.onToggleFavorite()
            assertEquals(true, (awaitItem() as NowPlayingUiState.Visible).isFavorite)
            assertEquals(setOf("rse-a"), playlists.favorites.value)

            viewModel.onToggleFavorite()
            assertEquals(false, (awaitItem() as NowPlayingUiState.Visible).isFavorite)
            assertEquals(emptySet<String>(), playlists.favorites.value)
        }
    }

    @Test
    fun `the shuffle button shows the setting and flips it`() = runTest {
        playback.state.value = playing(QueueContext.Family("rse"))

        viewModel.uiState.test {
            assertEquals(false, (awaitItem() as NowPlayingUiState.Visible).shuffle)

            viewModel.onShuffle()
            assertEquals(true, (awaitItem() as NowPlayingUiState.Visible).shuffle)

            viewModel.onShuffle()
            assertEquals(false, (awaitItem() as NowPlayingUiState.Visible).shuffle)
        }
        assertEquals(
            listOf(
                FakePlaybackController.SetShuffle(true),
                FakePlaybackController.SetShuffle(false)
            ),
            playback.calls
        )
    }

    @Test
    fun `the seek bar follows playback`() = runTest {
        viewModel.progress.test {
            assertEquals(PlaybackProgress(0, null), awaitItem())
            playback.progress.value = PlaybackProgress(30_000, 76_000)
            assertEquals(PlaybackProgress(30_000, 76_000), awaitItem())
        }
    }

    @Test
    fun `the controls and the seek bar reach the player`() {
        viewModel.onPlayPause()
        viewModel.onPrevious()
        viewModel.onNext()
        viewModel.onSeek(42_000)

        assertEquals(
            listOf(
                FakePlaybackController.TogglePlayPause,
                FakePlaybackController.RestartOrSkipToPrevious,
                FakePlaybackController.SkipToNext,
                FakePlaybackController.SeekTo(42_000)
            ),
            playback.calls
        )
    }

    @Test
    fun `previous restarts a track that has played a while, as the notification's button does`() {
        viewModel.onPrevious()

        assertEquals(listOf(FakePlaybackController.RestartOrSkipToPrevious), playback.calls)
    }

    @Test
    fun `the swipe's previous always goes back a track, never restarting this one`() {
        viewModel.onSwipePrevious()
        viewModel.onPrevious()

        assertEquals(
            listOf(
                FakePlaybackController.SkipToPrevious,
                FakePlaybackController.RestartOrSkipToPrevious
            ),
            playback.calls
        )
    }

    @Test
    fun `the art beside the current one is the previous and next tracks' art`() = runTest {
        val remix = CatalogTrack(
            "yt:AAAAAAAAAAA",
            null,
            emptyList(),
            "Some Remix",
            0,
            emptyList(),
            thumbnail = "thumbnails/AAAAAAAAAAA.jpg"
        )
        val viewModel = NowPlayingViewModel(
            FakeCatalogRepository(catalog.copy(userTracks = listOf(remix))),
            playback,
            playlists
        )
        val entries = listOf("yt:AAAAAAAAAAA", "rse-a", "rse-a").map {
            QueueEntry(it, QueueContext.Queue)
        }
        fun at(current: Int) = PlaybackState(
            queue = PlayQueue(entries, current),
            trackId = entries[current].trackId
        )
        playback.state.value = at(1)

        viewModel.uiState.test {
            val middle = awaitItem() as NowPlayingUiState.Visible
            assertEquals(NowPlayingArt(null, "thumbnails/AAAAAAAAAAA.jpg"), middle.previous)
            assertEquals(NowPlayingArt("rse", null), middle.next)

            playback.state.value = at(0)
            assertEquals(null, (awaitItem() as NowPlayingUiState.Visible).previous)

            playback.state.value = at(2)
            assertEquals(null, (awaitItem() as NowPlayingUiState.Visible).next)
        }
    }

    @Test
    fun `knows the playing track's id`() = runTest {
        playback.state.value = playing(QueueContext.Family("rse"))

        viewModel.trackId.test {
            assertEquals("rse-a", awaitItem())
        }
    }

    @Test
    fun `times read as minutes and seconds, with hours only when needed`() {
        assertEquals("0:07", formatTime(7_400))
        assertEquals("1:49", formatTime(109_000))
        assertEquals("1:02:03", formatTime(3_723_000))
        assertEquals("0:00", formatTime(-5))
    }

    @Test
    fun `a user track's thumbnail is the art`() = runTest {
        val remix = CatalogTrack(
            "yt:AAAAAAAAAAA",
            null,
            emptyList(),
            "Some Remix",
            0,
            emptyList(),
            channel = "GlitchxCity",
            thumbnail = "thumbnails/AAAAAAAAAAA.jpg"
        )
        playback.state.value = playing(QueueContext.Remixes, trackId = "yt:AAAAAAAAAAA")
        val viewModel = NowPlayingViewModel(
            FakeCatalogRepository(catalog.copy(userTracks = listOf(remix))),
            playback,
            playlists
        )

        viewModel.uiState.test {
            assertEquals(
                "thumbnails/AAAAAAAAAAA.jpg",
                (awaitItem() as NowPlayingUiState.Visible).thumbnail
            )
        }
    }

    @Test
    fun `a user track shows its channel after its chips`() = runTest {
        playback.state.value = playing(QueueContext.Remixes, trackId = "yt:AAAAAAAAAAA")
        val remix = CatalogTrack(
            "yt:AAAAAAAAAAA",
            "rse",
            listOf(ruby),
            "Route 101",
            0,
            emptyList(),
            channel = "GlitchxCity"
        )
        val viewModel = NowPlayingViewModel(
            FakeCatalogRepository(catalog.copy(userTracks = listOf(remix))),
            playback,
            playlists
        )

        viewModel.uiState.test {
            assertEquals("GlitchxCity", (awaitItem() as NowPlayingUiState.Visible).subtitle)
        }
    }

    @Test
    fun `a catalog track has no subtitle`() = runTest {
        playback.state.value = playing(QueueContext.Family("rse"))

        viewModel.uiState.test {
            assertEquals(null, (awaitItem() as NowPlayingUiState.Visible).subtitle)
        }
    }

    @Test
    fun `a user track's title opens Remixes, a catalog track's its family`() = runTest {
        playback.state.value = playing(QueueContext.Remixes, trackId = "yt:AAAAAAAAAAA")
        viewModel.uiState.test {
            assertEquals(true, (awaitItem() as NowPlayingUiState.Visible).opensRemixes)
            playback.state.value = playing(QueueContext.Family("rse"))
            assertEquals(false, (awaitItem() as NowPlayingUiState.Visible).opensRemixes)
        }
    }

    @Test
    fun `a playing track that left the catalog stays shown`() = runTest {
        val retired =
            CatalogTrack("rse-old", "rse", listOf(ruby), "Old", 2, emptyList(), retired = true)
        playback.state.value = playing(QueueContext.Family("rse"), trackId = "rse-old")

        NowPlayingViewModel(
            FakeCatalogRepository(catalog.copy(retiredTracks = listOf(retired))),
            playback,
            playlists
        ).uiState.test {
            assertEquals("Old", (awaitItem() as NowPlayingUiState.Visible).title)
        }
    }
}
