package io.github.pranavm716.vgmixr.data.usertrack

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FileThumbnailStoreTest {
    private lateinit var server: HttpServer
    private val dir: File = Files.createTempDirectory("thumbnails").toFile()
    private val dispatcher = StandardTestDispatcher()

    /** Image bodies by path; any other path is a 404, as i.ytimg.com answers a missing size. */
    private val images = mutableMapOf<String, ByteArray>()
    private val requested = mutableListOf<String>()

    private val store by lazy {
        FileThumbnailStore(
            dir,
            OkHttpClient(),
            dispatcher,
            baseUrl = "http://127.0.0.1:${server.address.port}/vi/"
        )
    }

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requested += path
            val body = images[path]
            if (body == null) {
                exchange.sendResponseHeaders(404, -1)
            } else {
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
    }

    @After
    fun stop() {
        server.stop(0)
        dir.deleteRecursively()
    }

    @Test
    fun `downloads the largest size the video has`() = runTest(dispatcher) {
        images["/vi/AAAAAAAAAAA/maxresdefault.jpg"] = byteArrayOf(1, 2, 3)
        images["/vi/AAAAAAAAAAA/hqdefault.jpg"] = byteArrayOf(9)

        assertTrue(store.download("AAAAAAAAAAA"))

        assertArrayEquals(byteArrayOf(1, 2, 3), store.file("AAAAAAAAAAA")?.readBytes())
    }

    @Test
    fun `falls back to smaller sizes when a video has no large one`() = runTest(dispatcher) {
        images["/vi/AAAAAAAAAAA/hqdefault.jpg"] = byteArrayOf(9)

        assertTrue(store.download("AAAAAAAAAAA"))

        assertArrayEquals(byteArrayOf(9), store.file("AAAAAAAAAAA")?.readBytes())
        assertEquals(
            listOf(
                "/vi/AAAAAAAAAAA/maxresdefault.jpg",
                "/vi/AAAAAAAAAAA/sddefault.jpg",
                "/vi/AAAAAAAAAAA/hqdefault.jpg"
            ),
            requested
        )
    }

    @Test
    fun `no thumbnail at all leaves nothing behind`() = runTest(dispatcher) {
        assertFalse(store.download("AAAAAAAAAAA"))

        assertNull(store.file("AAAAAAAAAAA"))
        assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
    }

    @Test
    fun `a thumbnail already there isn't downloaded again`() = runTest(dispatcher) {
        images["/vi/AAAAAAAAAAA/maxresdefault.jpg"] = byteArrayOf(1)
        store.download("AAAAAAAAAAA")
        requested.clear()

        assertTrue(store.download("AAAAAAAAAAA"))

        assertEquals(emptyList<String>(), requested)
    }

    @Test
    fun `an unreachable server is a failed download`() = runTest(dispatcher) {
        server.stop(0)

        assertFalse(store.download("AAAAAAAAAAA"))
        assertNull(store.file("AAAAAAAAAAA"))
    }

    @Test
    fun `delete removes the thumbnail`() = runTest(dispatcher) {
        images["/vi/AAAAAAAAAAA/maxresdefault.jpg"] = byteArrayOf(1)
        store.download("AAAAAAAAAAA")

        store.delete("AAAAAAAAAAA")

        assertNull(store.file("AAAAAAAAAAA"))
    }

    @Test
    fun `the version goes up when a new thumbnail lands, and only then`() = runTest(dispatcher) {
        images["/vi/AAAAAAAAAAA/maxresdefault.jpg"] = byteArrayOf(1)

        assertEquals(0, store.version.value)
        store.download("BBBBBBBBBBB")
        assertEquals(0, store.version.value)
        store.download("AAAAAAAAAAA")
        assertEquals(1, store.version.value)
        store.download("AAAAAAAAAAA")
        assertEquals(1, store.version.value)
    }

    @Test
    fun `a thumbnail that can't be written is a failed download, not a crash`() =
        runTest(dispatcher) {
            images["/vi/AAAAAAAAAAA/maxresdefault.jpg"] = byteArrayOf(1)
            // A file where the folder should be, so nothing can be written in it.
            dir.deleteRecursively()
            dir.writeText("not a folder")

            assertFalse(store.download("AAAAAAAAAAA"))
            assertEquals(0, store.version.value)
        }
}
