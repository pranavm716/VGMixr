package io.github.pranavm716.vgmixr.playback

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.PlaybackEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackEventBundleTest {
    @Test
    fun `a source failure crosses from the service to the app intact`() {
        listOf(
            PlaybackEvent.SourceFailed("oras-a", BrokenReason.NO_AUDIO, trackSkipped = true),
            PlaybackEvent.SourceFailed("oras-a", BrokenReason.UNAVAILABLE, trackSkipped = false),
            PlaybackEvent.SourceFailed("frlg-b", null, trackSkipped = false)
        ).forEach { event ->
            assertEquals(event, playbackEvent(SOURCE_FAILED, event.toBundle()))
        }
    }

    @Test
    fun `an unknown command is no event`() {
        assertNull(playbackEvent("something.else", Bundle()))
    }
}
