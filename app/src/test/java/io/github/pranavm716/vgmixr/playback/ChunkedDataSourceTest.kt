package io.github.pranavm716.vgmixr.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChunkedDataSourceTest {
    private val content = ByteArray(2_500) { (it % 251).toByte() }

    /** Serves [content] by range, as YouTube's media servers do, and records each request. */
    private inner class FakeServer : DataSource {
        val requests = mutableListOf<Pair<Long, Long>>()
        private var position = 0
        private var end = 0
        private var uri: Uri? = null

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            requests += dataSpec.position to dataSpec.length
            uri = dataSpec.uri
            position = dataSpec.position.toInt()
            val length = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                content.size - position
            } else {
                minOf(dataSpec.length.toInt(), content.size - position)
            }
            end = position + length
            return length.toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= end) return C.RESULT_END_OF_INPUT
            val n = minOf(length, end - position)
            content.copyInto(buffer, offset, position, position + n)
            position += n
            return n
        }

        override fun getUri(): Uri? = uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            mapOf("Content-Range" to listOf("bytes 0-0/${content.size}"))

        override fun close() = Unit
    }

    private fun readAll(source: DataSource, spec: DataSpec): Pair<Long, ByteArray> {
        val length = source.open(spec)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(300)
        while (true) {
            val n = source.read(buffer, 0, buffer.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, n)
        }
        source.close()
        return length to out.toByteArray()
    }

    private val uri = Uri.parse("https://example.com/audio")

    @Test
    fun `an open-ended read is fetched in bounded ranges and yields every byte`() {
        val server = FakeServer()

        val (length, bytes) = readAll(ChunkedDataSource(server, chunkBytes = 1_000), DataSpec(uri))

        assertEquals(2_500L, length)
        assertArrayEquals(content, bytes)
        assertEquals(listOf(0L to 1_000L, 1_000L to 1_000L, 2_000L to 500L), server.requests)
    }

    @Test
    fun `a read from the middle reports what is left`() {
        val server = FakeServer()
        val spec = DataSpec.Builder().setUri(uri).setPosition(1_800).build()

        val (length, bytes) = readAll(ChunkedDataSource(server, chunkBytes = 1_000), spec)

        assertEquals(700L, length)
        assertArrayEquals(content.copyOfRange(1_800, 2_500), bytes)
    }

    @Test
    fun `a bounded read stays within its own length`() {
        val server = FakeServer()
        val spec = DataSpec.Builder().setUri(uri).setPosition(100).setLength(1_500).build()

        val (length, bytes) = readAll(ChunkedDataSource(server, chunkBytes = 1_000), spec)

        assertEquals(1_500L, length)
        assertArrayEquals(content.copyOfRange(100, 1_600), bytes)
        assertEquals(listOf(100L to 1_000L, 1_100L to 500L), server.requests)
    }
}
