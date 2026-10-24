package io.github.pranavm716.vgmixr.ui.playlist

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.PlayQueue
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.QueueEntry
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
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

class PlaylistViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val ruby = game("ruby", shortName = "R")
    private val sapphire = game("sapphire", shortName = "S")

    private val catalog = Catalog(
        games = listOf(ruby, sapphire),
        families = listOf(Family("rse", "Ruby / Sapphire", "RS")),
        tracks = listOf(
            CatalogTrack("rse-a", "rse", listOf(ruby, sapphire), "Route 101", 1, emptyList()),
            CatalogTrack("rse-b", "rse", listOf(ruby), "Battle! (Team Magma)", 2, emptyList()),
            CatalogTrack(
                "rse-c",
                "rse",
                listOf(sapphire),
                "Battle! (Team Aqua)",
                3,
                emptyList(),
                BrokenReason.UNAVAILABLE
            )
        )
    )

    private val playlists = FakePlaylistRepository().apply {
        // Newest first; "rse-gone" is no longer in the catalog.
        contents.value = mapOf(
            1L to Playlist(1, "Favorites", true, listOf("rse-b", "rse-gone", "rse-c", "rse-a"))
        )
    }
    private val playback = FakePlaybackController()
    private val messages = UserMessages()

    private fun viewModel(playlistId: Long = 1) = PlaylistViewModel(
        SavedStateHandle(mapOf("playlistId" to playlistId)),
        FakeCatalogRepository(catalog),
        playlists,
        playback,
        messages,
        main.dispatcher
    )

    private fun chip(shortName: String) = ChipUi(shortName, "#336699")

    @Test
    fun `lists the playlist's tracks newest first, with all of their games`() = runTest {
        viewModel().uiState.test {
            assertEquals(
                PlaylistUiState.Loaded(
                    name = "Favorites",
                    isFavorites = true,
                    rows = listOf(
                        PlaylistRowUi("rse-b", "Battle! (Team Magma)", listOf(chip("R"))),
                        PlaylistRowUi(
                            "rse-c",
                            "Battle! (Team Aqua)",
                            listOf(chip("S")),
                            unplayable = true
                        ),
                        PlaylistRowUi(
                            "rse-a",
                            "Route 101",
                            listOf(chip("R"), chip("S"))
                        )
                    )
                ),
                awaitItem()
            )
        }
    }

    @Test
    fun `the playing track's row is marked`() = runTest {
        playback.state.value = PlaybackState(
            queue = PlayQueue(listOf(QueueEntry("rse-a", QueueContext.Playlist(1))), 0),
            trackId = "rse-a",
            isPlaying = true
        )

        viewModel().uiState.test {
            val state = awaitItem() as PlaylistUiState.Loaded
            assertEquals(listOf("rse-a"), state.rows.filter { it.playing }.map { it.id })
            assertEquals(2, state.playingIndex)
        }
    }

    @Test
    fun `a tap plays the playlist's playable tracks from it, in the playlist`() = runTest {
        viewModel().onTrackClick("rse-a")

        assertEquals(
            listOf(
                FakePlaybackController.PlayFrom(
                    listOf("rse-b", "rse-a"),
                    index = 1,
                    context = QueueContext.Playlist(1)
                )
            ),
            playback.calls
        )
    }

    @Test
    fun `a tap on a greyed track says why it can't play, and plays nothing`() = runTest {
        messages.messages.test {
            viewModel().onTrackClick("rse-c")

            assertEquals(
                UserMessage.TrackUnplayable(
                    "rse-c",
                    "Battle! (Team Aqua)",
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
            viewModel().onQueueTrack("rse-b")

            assertEquals(listOf(FakePlaybackController.AddToQueue(listOf("rse-b"))), playback.calls)
            assertEquals(UserMessage.AddedToQueue(1), awaitItem())
        }
    }

    @Test
    fun `an empty playlist has no rows`() = runTest {
        playlists.contents.value = mapOf(2L to Playlist(2, "Mix", false, emptyList()))

        viewModel(playlistId = 2).uiState.test {
            assertEquals(PlaylistUiState.Loaded("Mix", false, emptyList()), awaitItem())
        }
    }

    @Test
    fun `a playlist that doesn't exist says so`() = runTest {
        viewModel(playlistId = 9).uiState.test {
            assertEquals(PlaylistUiState.NotFound, awaitItem())
        }
    }

    @Test
    fun `renaming trims the name`() = runTest {
        playlists.contents.value += 2L to Playlist(2, "Mix", false, emptyList())

        viewModel(playlistId = 2).onRename("  Gym leaders ")

        assertEquals("Gym leaders", playlists.contents.value[2]?.name)
    }

    @Test
    fun `a blank name renames nothing`() = runTest {
        playlists.contents.value += 2L to Playlist(2, "Mix", false, emptyList())

        viewModel(playlistId = 2).onRename("   ")

        assertEquals("Mix", playlists.contents.value[2]?.name)
    }

    @Test
    fun `deleting the playlist removes it and closes the page`() = runTest {
        playlists.contents.value += 2L to Playlist(2, "Mix", false, listOf("rse-a"))
        val viewModel = viewModel(playlistId = 2)

        viewModel.events.test {
            viewModel.onDelete()

            assertEquals(PlaylistEvent.Closed, awaitItem())
        }
        assertEquals(null, playlists.contents.value[2])
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
        playlists.contents.value =
            mapOf(2L to Playlist(2, "Mix", false, listOf("yt:AAAAAAAAAAA")))
        val viewModel = PlaylistViewModel(
            SavedStateHandle(mapOf("playlistId" to 2L)),
            FakeCatalogRepository(catalog.copy(userTracks = listOf(remix))),
            playlists,
            playback,
            messages,
            main.dispatcher
        )

        viewModel.uiState.test {
            val row = (awaitItem() as PlaylistUiState.Loaded).rows.single()
            assertEquals("thumbnails/AAAAAAAAAAA.jpg", row.thumbnail)
            assertEquals("GlitchxCity", row.subtitle)
        }
    }
}
