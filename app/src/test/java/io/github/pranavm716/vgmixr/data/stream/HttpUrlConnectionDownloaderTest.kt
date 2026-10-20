package io.github.pranavm716.vgmixr.data.stream

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

class HttpUrlConnectionDownloaderTest {
    private lateinit var server: HttpServer
    private var received: Received? = null
    private var reply: (HttpExchange) -> Unit = { it.respond(200, "ok") }

    private class Received(
        val method: String,
        val headers: Map<String, List<String>>,
        val body: ByteArray
    )

    private val downloader = HttpUrlConnectionDownloader()
    private val url get() = "http://127.0.0.1:${server.address.port}/path"

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            received = Received(
                exchange.requestMethod,
                exchange.requestHeaders.mapKeys { it.key.lowercase() },
                exchange.requestBody.readBytes()
            )
            reply(exchange)
        }
        server.start()
    }

    @After
    fun stopServer() = server.stop(0)

    @Test
    fun `a GET returns the code, body, headers and final url`() {
        reply = {
            it.responseHeaders.add("X-Answer", "42")
            it.respond(200, "hello")
        }

        val response = downloader.get(url)

        assertEquals("GET", received?.method)
        assertEquals(200, response.responseCode())
        assertEquals("hello", response.responseBody())
        assertEquals("42", response.getHeader("X-Answer"))
        assertEquals(url, response.latestUrl())
    }

    @Test
    fun `request headers are sent with every value, and a user agent by default`() {
        downloader.get(url, mapOf("X-Multi" to listOf("a", "b")))

        val headers = received?.headers.orEmpty()
        assertEquals("a,b", headers["x-multi"]?.joinToString(","))
        assertTrue(headers["user-agent"]?.single().orEmpty().startsWith("Mozilla/5.0"))
    }

    @Test
    fun `a caller's user agent replaces the default`() {
        downloader.get(url, mapOf("User-Agent" to listOf("custom")))

        assertEquals(listOf("custom"), received?.headers?.get("user-agent"))
    }

    @Test
    fun `a POST sends its body`() {
        val body = """{"q":1}""".toByteArray()

        downloader.execute(
            Request.newBuilder()
                .post(url, body)
                .setHeader("Content-Type", "application/json")
                .build()
        )

        assertEquals("POST", received?.method)
        assertArrayEquals(body, received?.body)
    }

    @Test
    fun `an error status returns its code and body instead of throwing`() {
        reply = { it.respond(404, "missing") }

        val response = downloader.get(url)

        assertEquals(404, response.responseCode())
        assertEquals("missing", response.responseBody())
    }

    @Test(expected = ReCaptchaException::class)
    fun `too many requests is YouTube's bot check`() {
        reply = { it.respond(429, "slow down") }

        downloader.get(url)
    }

    private fun HttpExchange.respond(code: Int, body: String) {
        val bytes = body.toByteArray()
        sendResponseHeaders(code, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
