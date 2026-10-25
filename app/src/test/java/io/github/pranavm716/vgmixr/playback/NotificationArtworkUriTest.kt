package io.github.pranavm716.vgmixr.playback

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.data.catalog.TestCatalog.game
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationArtworkUriTest {
    private val artwork = NotificationArtwork(Files.createTempDirectory("artwork").toFile())

    private fun track(thumbnail: String?) = CatalogTrack(
        "yt:AAAAAAAAAAA",
        "rse",
        listOf(game("ruby")),
        "Some Remix",
        0,
        emptyList(),
        thumbnail = thumbnail
    )

    @Test
    fun `a track with a thumbnail shows it`() {
        val thumbnail = File("thumbnails/AAAAAAAAAAA.jpg").absolutePath

        assertEquals(Uri.fromFile(File(thumbnail)), artwork.uriFor(track(thumbnail)))
    }

    @Test
    fun `a track without one shows its games' colors`() {
        val uri = artwork.uriFor(track(null))

        assertEquals(artwork.uriFor(listOf(game("ruby").color)), uri)
        assertNotEquals(null, uri.path)
    }

    @Test
    fun `a track of a family with a cover shows the cover`() {
        val withCovers = NotificationArtwork(
            Files.createTempDirectory("artwork").toFile(),
            coverFamilies = setOf("rse")
        )

        assertEquals(Uri.parse("asset:///covers/rse.webp"), withCovers.uriFor(track(null)))
    }

    @Test
    fun `a thumbnail comes before the family's cover`() {
        val withCovers = NotificationArtwork(
            Files.createTempDirectory("artwork").toFile(),
            coverFamilies = setOf("rse")
        )
        val thumbnail = File("thumbnails/AAAAAAAAAAA.jpg").absolutePath

        assertEquals(Uri.fromFile(File(thumbnail)), withCovers.uriFor(track(thumbnail)))
    }
}
