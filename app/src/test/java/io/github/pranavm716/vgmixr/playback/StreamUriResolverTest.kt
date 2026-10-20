package io.github.pranavm716.vgmixr.playback

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.playback.StreamResolution
import io.github.pranavm716.vgmixr.domain.playback.StreamResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamUriResolverTest {
    private var result: StreamResolution = StreamResolution.Resolved(
        "https://g/a",
        "audio/webm",
        160
    )
    private val asked = mutableListOf<String>()
    private val resolver = StreamUriResolver(object : StreamResolver {
        override suspend fun resolve(videoId: String): StreamResolution {
            asked += videoId
            return result
        }
    })

    private fun spec(uri: String) =
        DataSpec.Builder().setUri(Uri.parse(uri)).setPosition(4096).build()

    @Test
    fun `a stream URI becomes the video's audio URL, keeping the read position`() {
        val resolved = resolver.resolveDataSpec(spec("vgmixr://stream/VzG7oP2oSUQ"))

        assertEquals(listOf("VzG7oP2oSUQ"), asked)
        assertEquals("https://g/a", resolved.uri.toString())
        assertEquals(4096, resolved.position)
    }

    @Test
    fun `an unavailable video fails this source only`() {
        result = StreamResolution.Unavailable("removed")

        assertThrows(SourceUnavailableException::class.java) {
            resolver.resolveDataSpec(spec("vgmixr://stream/gone"))
        }
    }

    @Test
    fun `a failed resolve carries its reason`() {
        result = StreamResolution.Failed(ResolveFailure.BOT_CHECK)

        val e = assertThrows(ResolveFailedException::class.java) {
            resolver.resolveDataSpec(spec("vgmixr://stream/any"))
        }
        assertEquals(ResolveFailure.BOT_CHECK, e.failure)
    }

    @Test
    fun `a track without a playable source fails without asking YouTube`() {
        assertThrows(SourceUnavailableException::class.java) {
            resolver.resolveDataSpec(spec(NO_SOURCE_URI))
        }
        assertEquals(emptyList<String>(), asked)
    }
}
