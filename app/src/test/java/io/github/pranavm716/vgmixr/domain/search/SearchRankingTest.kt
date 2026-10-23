package io.github.pranavm716.vgmixr.domain.search

import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.history.Play
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.testing.FakeCatalogRepository
import io.github.pranavm716.vgmixr.testing.FakePlayHistory
import io.github.pranavm716.vgmixr.testing.FakePlaybackController
import io.github.pranavm716.vgmixr.testing.FakePlaylistRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRankingTest {
    private val ruby = game("ruby", familyId = "rse")
    private val heartGold = game("heartgold", familyId = "hgss")
    private val catalog = Catalog(
        games = listOf(ruby, heartGold),
        families = listOf(Family("rse", "Ruby", "R"), Family("hgss", "HeartGold", "HG")),
        tracks = listOf(
            CatalogTrack("rse-route-101", "rse", listOf(ruby), "Route 101", 1, emptyList()),
            CatalogTrack("hgss-route-29", "hgss", listOf(heartGold), "Route 29", 1, emptyList())
        )
    )
    private val history = FakePlayHistory()
    private val playlists = FakePlaylistRepository()
    private val playback = FakePlaybackController()
    private val browsed = BrowsedFamily()
    private val ranking =
        SearchRanking(history, playlists, playback, FakeCatalogRepository(catalog), browsed)

    @Test
    fun `without any listening there are no signals`() = runTest {
        assertEquals(RankingSignals(), ranking.signals.first())
    }

    @Test
    fun `the signals carry the plays and the tracks in playlists`() = runTest {
        val plays = listOf(Play("rse-route-101", Instant.parse("2026-10-20T08:00:00Z")))
        history.plays.value = plays
        playlists.setFavorite("hgss-route-29", true)

        val signals = ranking.signals.first()

        assertEquals(plays, signals.plays)
        assertEquals(setOf("hgss-route-29"), signals.inPlaylists)
    }

    @Test
    fun `the current track's family is lifted, also while paused`() = runTest {
        playback.state.value = PlaybackState(trackId = "hgss-route-29", isPlaying = false)

        assertEquals(setOf("hgss"), ranking.signals.first().liftedFamilies)
    }

    @Test
    fun `the last family page opened is lifted along with the playing family`() = runTest {
        playback.state.value = PlaybackState(trackId = "hgss-route-29", isPlaying = true)
        browsed.opened("hgss")
        browsed.opened("rse")

        assertEquals(setOf("hgss", "rse"), ranking.signals.first().liftedFamilies)
    }

    @Test
    fun `a playing track the catalog doesn't have lifts no family`() = runTest {
        playback.state.value = PlaybackState(trackId = "yt:abcdefghijk", isPlaying = true)

        assertEquals(emptySet<String>(), ranking.signals.first().liftedFamilies)
    }
}
