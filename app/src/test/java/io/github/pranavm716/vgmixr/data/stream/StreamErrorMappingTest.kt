package io.github.pranavm716.vgmixr.data.stream

import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.playback.StreamResolution
import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException

class StreamErrorMappingTest {
    @Test
    fun `a gone, private or restricted video is unavailable, so the next source is tried`() {
        listOf(
            ContentNotAvailableException("This video has been removed"),
            PrivateContentException("private"),
            AgeRestrictedContentException("age"),
            GeographicRestrictionException("geo"),
            ContentNotSupportedException("live")
        ).forEach {
            assertTrue(it.toString(), toResolution(it) is StreamResolution.Unavailable)
        }
    }

    @Test
    fun `no connection is a network failure`() {
        assertEquals(
            StreamResolution.Failed(ResolveFailure.NETWORK),
            toResolution(UnknownHostException("www.youtube.com"))
        )
        assertEquals(
            StreamResolution.Failed(ResolveFailure.NETWORK),
            toResolution(IOException("timeout"))
        )
    }

    @Test
    fun `YouTube's bot check stops resolving`() {
        assertEquals(
            StreamResolution.Failed(ResolveFailure.BOT_CHECK),
            toResolution(ReCaptchaException("captcha", "https://www.youtube.com"))
        )
        assertEquals(
            StreamResolution.Failed(ResolveFailure.BOT_CHECK),
            toResolution(SignInConfirmNotBotException("Sign in to confirm you're not a bot"))
        )
    }

    @Test
    fun `any other extraction error means the extractor broke, which affects every video`() {
        assertEquals(
            StreamResolution.Failed(ResolveFailure.EXTRACTOR),
            toResolution(ParsingException("Could not get player response"))
        )
    }
}
