package io.github.pranavm716.vgmixr.playback

import androidx.media3.common.C
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.model.Category
import io.github.pranavm716.vgmixr.domain.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackMediaItemTest {
    private fun track(sources: List<Source>) = CatalogTrack(
        id = "sv-across-the-skies-of-paldea",
        familyId = "sv",
        games = emptyList(),
        title = "Across the Skies of Paldea",
        category = Category.OTHER,
        order = 1,
        sources = sources
    )

    private val slice = Source.Stream("VzG7oP2oSUQ", 133_000, 209_000)
    private val whole = Source.Stream("7a_BW6_q8P0", 0, null)

    @Test
    fun `an item plays its track's first stream, clipped to its slice`() {
        val item = trackMediaItem(track(listOf(slice, whole)), familyName = "Scarlet / Violet")

        assertEquals("sv-across-the-skies-of-paldea", item.mediaId)
        assertEquals("vgmixr://stream/VzG7oP2oSUQ", item.localConfiguration?.uri.toString())
        assertEquals(133_000, item.clippingConfiguration.startPositionMs)
        assertEquals(209_000, item.clippingConfiguration.endPositionMs)
        assertEquals(0, item.sourceIndex)
    }

    @Test
    fun `the title and family show in the notification`() {
        val item = trackMediaItem(track(listOf(slice)), familyName = "Scarlet / Violet")

        assertEquals("Across the Skies of Paldea", item.mediaMetadata.title.toString())
        assertEquals("Scarlet / Violet", item.mediaMetadata.artist.toString())
    }

    @Test
    fun `a later source plays unclipped when it has no slice`() {
        val item = trackMediaItem(track(listOf(slice, whole)), "Scarlet / Violet", sourceIndex = 1)

        assertEquals("vgmixr://stream/7a_BW6_q8P0", item.localConfiguration?.uri.toString())
        assertEquals(0, item.clippingConfiguration.startPositionMs)
        assertEquals(C.TIME_END_OF_SOURCE, item.clippingConfiguration.endPositionMs)
        assertEquals(1, item.sourceIndex)
    }

    @Test
    fun `local files are skipped until local playback`() {
        val local = Source.Local("sv/across-the-skies.flac", null)
        val item = trackMediaItem(track(listOf(local, whole)), "Scarlet / Violet")

        assertEquals("vgmixr://stream/7a_BW6_q8P0", item.localConfiguration?.uri.toString())
    }

    @Test
    fun `a track with nothing playable gets an item that fails, so it is skipped`() {
        val item = trackMediaItem(track(emptyList()), "Scarlet / Violet")

        assertEquals(NO_SOURCE_URI, item.localConfiguration?.uri.toString())
    }
}
