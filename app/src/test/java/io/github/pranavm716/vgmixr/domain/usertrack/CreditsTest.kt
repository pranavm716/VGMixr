package io.github.pranavm716.vgmixr.domain.usertrack

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CreditsTest {
    private fun track(videoId: String, channel: String, credit: String? = null) = UserTrack(
        videoId = videoId,
        title = "Title $videoId",
        channel = channel,
        durationMs = null,
        addedAt = Instant.EPOCH,
        credit = credit
    )

    private val brawlRip = track("AAAAAAAAAAA", "pkmnrips4u", credit = "Super Smash Bros. Brawl")
    private val glitch = track("BBBBBBBBBBB", "GlitchxCity")
    private val glitchAgain = track("CCCCCCCCCCC", "glitchxcity")
    private val braxton = track("DDDDDDDDDDD", "Braxton Burks")
    private val all = listOf(brawlRip, glitch, glitchAgain, braxton)

    @Test
    fun `choices are the names rows show, each once ignoring case, A to Z`() {
        assertEquals(
            listOf("Braxton Burks", "GlitchxCity", "Super Smash Bros. Brawl"),
            creditChoices(all, own = track("EEEEEEEEEEE", "someone"))
        )
    }

    @Test
    fun `choices leave out the track's own channel, which comes first on its own`() {
        assertEquals(
            listOf("Braxton Burks", "Super Smash Bros. Brawl"),
            creditChoices(all, own = glitchAgain)
        )
    }

    @Test
    fun `a credited track's own credit is still a choice`() {
        assertEquals(
            listOf("Braxton Burks", "GlitchxCity", "Super Smash Bros. Brawl"),
            creditChoices(all, own = brawlRip)
        )
    }

    @Test
    fun `a new name matching a choice ignoring case takes the choice's spelling`() {
        val choices = listOf("Braxton Burks", "Super Smash Bros. Brawl")
        assertEquals(
            "Super Smash Bros. Brawl",
            creditFor("  super smash bros. BRAWL ", own = glitch, choices)
        )
    }

    @Test
    fun `a new name is trimmed`() {
        assertEquals("Pokémon Masters", creditFor(" Pokémon Masters ", own = glitch, emptyList()))
    }

    @Test
    fun `the track's own channel, in any case, clears the credit`() {
        assertEquals(null, creditFor("GLITCHXCITY", own = glitch, emptyList()))
    }

    @Test
    fun `a blank name clears the credit`() {
        assertEquals(null, creditFor("   ", own = brawlRip, emptyList()))
    }
}
