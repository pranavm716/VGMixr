package io.github.pranavm716.vgmixr.ui.queue

import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playback.PlayQueue
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.QueueEntry
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakePlaylistRepository
import io.github.pranavm716.vgmixr.testing.MainDispatcherRule
import io.github.pranavm716.vgmixr.ui.components.ChipUi
import io.github.pranavm716.vgmixr.ui.messages.UserMessage
import io.github.pranavm716.vgmixr.ui.messages.UserMessages
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QueueViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val ruby = game("ruby", shortName = "R")

    private fun track(id: String) =
        CatalogTrack(id, "rse", listOf(ruby), "Title $id", 0, emptyList())

    private val catalog = Catalog(
        games = listOf(ruby),
        families = listOf(Family("rse", "Ruby / Sapphire / Emerald", "RSE")),
        tracks = listOf("h", "c", "q1", "q2", "x", "y").map(::track),
        userTracks = listOf("yt:AAAAAAAAAAA", "yt:BBBBBBBBBBB").map(::track)
    )

    private val rse = QueueContext.Family("rse")
    private val playback = FakePlaybackController()
    private val messages = UserMessages()
    private val viewModel by lazy {
        QueueViewModel(FakeCatalogRepository(catalog), playback, messages, playlists)
    }

    private val playlists = FakePlaylistRepository(
        initialPlaylists = listOf(PlaylistSummary(1, "Favorites", 2, isFavorites = true))
    )

    private fun row(index: Int, id: String) =
        QueueRowUi(index, id, "Title $id", listOf(ChipUi("R", "#336699")), familyId = "rse")

    private fun state(entries: List<QueueEntry>, current: Int) = PlaybackState(
        queue = PlayQueue(entries, current),
        trackId = entries[current].trackId,
        isPlaying = true
    )

    @Test
    fun `now playing, then the queue, then the rest of the family, with played tracks hidden`() =
        runTest {
            playback.state.value = state(
                listOf(
                    QueueEntry("h", rse),
                    QueueEntry("c", rse),
                    QueueEntry("q1", QueueContext.Queue),
                    QueueEntry("q2", QueueContext.Queue),
                    QueueEntry("x", rse),
                    QueueEntry("y", rse)
                ),
                current = 1
            )

            viewModel.uiState.test {
                assertEquals(
                    QueueUiState(
                        nowPlaying = row(1, "c"),
                        upNext = listOf(row(2, "q1"), row(3, "q2")),
                        nextFromName = "Ruby / Sapphire / Emerald",
                        nextFrom = listOf(row(4, "x"), row(5, "y"))
                    ),
                    awaitItem()
                )
            }
        }

    @Test
    fun `with nothing queued, the queue section is empty`() = runTest {
        playback.state.value = state(listOf(QueueEntry("c", rse), QueueEntry("x", rse)), 0)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList<QueueRowUi>(), state.upNext)
            assertEquals(listOf(row(1, "x")), state.nextFrom)
        }
    }

    @Test
    fun `nothing playing, nothing listed`() = runTest {
        viewModel.uiState.test {
            assertEquals(QueueUiState.EMPTY, awaitItem())
        }
    }

    @Test
    fun `taps, swipes and the clear button reach the player`() {
        viewModel.onJump(4)
        viewModel.onRemove(2)
        viewModel.onClear()

        assertEquals(
            listOf(
                FakePlaybackController.JumpTo(4),
                FakePlaybackController.RemoveFromQueue(2),
                FakePlaybackController.ClearQueue
            ),
            playback.calls
        )
    }

    @Test
    fun `swiping a track of the family right adds it to the queue and says so`() = runTest {
        playback.state.value = state(listOf(QueueEntry("c", rse), QueueEntry("x", rse)), 0)

        viewModel.uiState.test {
            awaitItem()
            messages.messages.test {
                viewModel.onQueue(1)

                assertEquals(UserMessage.AddedToQueue(1), awaitItem())
            }
            assertEquals(listOf(FakePlaybackController.AddToQueue(listOf("x"))), playback.calls)
        }
    }

    @Test
    fun `next from names the playlist the list was tapped in`() = runTest {
        val favorites = QueueContext.Playlist(1)
        playback.state.value = state(
            listOf(QueueEntry("c", favorites), QueueEntry("x", favorites)),
            current = 0
        )

        viewModel.uiState.test {
            assertEquals("Favorites", awaitItem().nextFromName)
        }
    }

    @Test
    fun `next from a deleted playlist names the family instead`() = runTest {
        val gone = QueueContext.Playlist(9)
        playback.state.value =
            state(listOf(QueueEntry("c", gone), QueueEntry("x", gone)), current = 0)

        viewModel.uiState.test {
            assertEquals("Ruby / Sapphire / Emerald", awaitItem().nextFromName)
        }
    }

    @Test
    fun `next from Remixes names it`() = runTest {
        val remixes = QueueContext.Remixes
        playback.state.value = state(
            listOf(QueueEntry("yt:AAAAAAAAAAA", remixes), QueueEntry("yt:BBBBBBBBBBB", remixes)),
            current = 0
        )

        viewModel.uiState.test {
            assertEquals("Remixes", awaitItem().nextFromName)
        }
    }

    @Test
    fun `user tracks from a deleted playlist come from Remixes`() = runTest {
        val gone = QueueContext.Playlist(9)
        playback.state.value = state(
            listOf(QueueEntry("c", gone), QueueEntry("yt:AAAAAAAAAAA", gone)),
            current = 0
        )

        viewModel.uiState.test {
            assertEquals("Remixes", awaitItem().nextFromName)
        }
    }

    @Test
    fun `a user track's row shows its thumbnail and its channel`() = runTest {
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
        playback.state.value = state(listOf(QueueEntry("yt:AAAAAAAAAAA", QueueContext.Remixes)), 0)
        val viewModel = QueueViewModel(
            FakeCatalogRepository(catalog.copy(userTracks = listOf(remix))),
            playback,
            messages,
            playlists
        )

        viewModel.uiState.test {
            val row = awaitItem().nowPlaying
            assertEquals("thumbnails/AAAAAAAAAAA.jpg", row?.thumbnail)
            assertEquals("GlitchxCity", row?.subtitle)
        }
    }

    @Test
    fun `a row carries its track's family for the cover`() = runTest {
        playback.state.value = state(listOf(QueueEntry("c", rse)), 0)

        viewModel.uiState.test {
            assertEquals("rse", awaitItem().nowPlaying?.familyId)
        }
    }
}
