package io.github.pranavm716.vgmixr.domain.usertrack

import io.github.pranavm716.vgmixr.domain.playback.BrokenMark
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.testing.FakeBrokenSources
import io.github.pranavm716.vgmixr.testing.FakeThumbnailStore
import io.github.pranavm716.vgmixr.testing.FakeUserTrackRepository
import io.github.pranavm716.vgmixr.testing.FakeVideoLookup
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AddSharedVideoTest {
    private val now = Instant.parse("2026-10-23T12:00:00Z")
    private val details = VideoDetails("Route 101 [Remix]", "GlitchxCity", 185_000)
    private val repository = FakeUserTrackRepository()
    private val thumbnails = FakeThumbnailStore()
    private val broken = FakeBrokenSources()

    private fun addSharedVideo(
        lookup: FakeVideoLookup =
            FakeVideoLookup(mapOf("AAAAAAAAAAA" to VideoLookupResult.Found(details))),
        thumbnails: FakeThumbnailStore = this.thumbnails
    ) = AddSharedVideo(lookup, repository, thumbnails, broken, Clock.fixed(now, ZoneOffset.UTC))

    private val shared = "Route 101 remix https://youtu.be/AAAAAAAAAAA?si=xyz"

    @Test
    fun `a shared video is added as the video was, with its thumbnail`() = runTest {
        val result = addSharedVideo()(shared)

        assertEquals(AddVideoResult.Added("yt:AAAAAAAAAAA", "Route 101 [Remix]"), result)
        assertEquals(
            listOf(
                UserTrack(
                    videoId = "AAAAAAAAAAA",
                    title = "Route 101 [Remix]",
                    channel = "GlitchxCity",
                    durationMs = 185_000,
                    addedAt = now
                )
            ),
            repository.userTracks.first()
        )
        assertEquals(setOf("AAAAAAAAAAA"), thumbnails.downloaded)
    }

    @Test
    fun `a video without a thumbnail is added all the same`() = runTest {
        val result = addSharedVideo(thumbnails = FakeThumbnailStore(failing = true))(shared)

        assertEquals(AddVideoResult.Added("yt:AAAAAAAAAAA", "Route 101 [Remix]"), result)
        assertEquals(1, repository.userTracks.first().size)
    }

    @Test
    fun `a video added again after it broke and was deleted plays again`() = runTest {
        val mark =
            BrokenMark("AAAAAAAAAAA", "yt:AAAAAAAAAAA", BrokenReason.NO_AUDIO, null, now, null)
        broken.marks.value = mapOf("AAAAAAAAAAA" to mark)

        addSharedVideo()(shared)

        assertEquals(emptyMap<String, BrokenMark>(), broken.marks.value)
    }

    @Test
    fun `text without a video link adds nothing and asks YouTube nothing`() = runTest {
        val lookup = FakeVideoLookup()

        assertEquals(AddVideoResult.NotAVideoLink, addSharedVideo(lookup)("a remix I like"))
        assertEquals(emptyList<String>(), lookup.lookedUp)
    }

    @Test
    fun `a video already added isn't looked up or changed`() = runTest {
        val first = addSharedVideo()(shared)
        val lookup = FakeVideoLookup()

        assertEquals(
            AddVideoResult.AlreadyAdded("yt:AAAAAAAAAAA", "Route 101 [Remix]"),
            addSharedVideo(lookup)(shared)
        )
        assertEquals(emptyList<String>(), lookup.lookedUp)
        assertEquals(AddVideoResult.Added("yt:AAAAAAAAAAA", "Route 101 [Remix]"), first)
    }

    @Test
    fun `a video the app can't play isn't added`() = runTest {
        listOf(
            VideoLookupResult.NoAudio to AddVideoResult.NoAudio,
            VideoLookupResult.Unavailable("Private video") to
                AddVideoResult.Unavailable("Private video"),
            VideoLookupResult.Failed(ResolveFailure.BOT_CHECK) to
                AddVideoResult.Failed(ResolveFailure.BOT_CHECK)
        ).forEach { (lookedUp, expected) ->
            val lookup = FakeVideoLookup(mapOf("AAAAAAAAAAA" to lookedUp))

            assertEquals(expected, addSharedVideo(lookup)(shared))
        }
        assertEquals(emptyList<UserTrack>(), repository.userTracks.first())
        assertEquals(emptySet<String>(), thumbnails.downloaded)
    }
}
