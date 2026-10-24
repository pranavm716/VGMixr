package io.github.pranavm716.vgmixr.ui.share

import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.usertrack.AddVideoResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareToastTest {
    @Test
    fun `an added or already added video is named`() {
        assertEquals(
            ShareToast(R.string.share_added, "Route 101 [Remix]"),
            shareToast(AddVideoResult.Added("yt:AAAAAAAAAAA", "Route 101 [Remix]"))
        )
        assertEquals(
            ShareToast(R.string.share_already_added, "Route 101 [Remix]"),
            shareToast(AddVideoResult.AlreadyAdded("yt:AAAAAAAAAAA", "Route 101 [Remix]"))
        )
    }

    @Test
    fun `a video that wasn't added says why, longer`() {
        mapOf(
            AddVideoResult.NotAVideoLink to R.string.share_not_a_video,
            AddVideoResult.NoAudio to R.string.share_no_audio,
            AddVideoResult.Unavailable("Private video") to R.string.share_unavailable,
            AddVideoResult.Failed(ResolveFailure.NETWORK) to R.string.share_failed_network,
            AddVideoResult.Failed(ResolveFailure.BOT_CHECK) to R.string.share_failed_bot_check,
            AddVideoResult.Failed(ResolveFailure.EXTRACTOR) to R.string.share_failed_extractor
        ).forEach { (result, text) ->
            assertEquals(result.toString(), ShareToast(text, long = true), shareToast(result))
        }
    }
}
