package io.github.pranavm716.vgmixr.domain.usertrack

import org.junit.Assert.assertEquals
import org.junit.Test

/** The same links as the pipeline's test_video_id.py, plus what share sheets send. */
class SharedVideoIdTest {
    private val id = "ID2Iv5HP-0I"

    @Test
    fun `links and bare ids give the id`() {
        listOf(
            "  $id\n",
            "https://www.youtube.com/watch?v=$id",
            "https://www.youtube.com/watch?v=$id&list=PL2uxd6YWj7PIOlswxbt16G63Klr_3lsbR&index=3",
            "https://youtube.com/watch?feature=shared&v=$id&t=42s",
            "www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?v=$id",
            "https://music.youtube.com/watch?v=$id&si=abc",
            "https://youtu.be/$id",
            "https://youtu.be/$id?si=xyz&t=10",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id?start=5",
            "https://www.youtube-nocookie.com/embed/$id",
            "https://www.youtube.com/live/$id"
        ).forEach { assertEquals(it, id, sharedVideoId(it)) }
    }

    @Test
    fun `a link among other words gives its id`() {
        listOf(
            "Route 101 remix https://youtu.be/$id?si=xyz",
            "Watch \"Route 101 [Remix]\" on YouTube\nhttps://www.youtube.com/watch?v=$id",
            "https://youtu.be/$id via the YouTube app"
        ).forEach { assertEquals(it, id, sharedVideoId(it)) }
    }

    @Test
    fun `anything else is no id`() {
        listOf(
            "",
            "ID2Iv5HP-0",
            "ID2Iv5HP-0I0",
            "https://www.youtube.com/playlist?list=PL2uxd6YWj7PIOlswxbt16G63Klr_3lsbR",
            "https://example.com/watch?v=$id",
            "https://www.youtube.com/watch?v=not-an-id",
            "https://youtu.be/",
            "a remix I like"
        ).forEach { assertEquals(it, null, sharedVideoId(it)) }
    }
}
