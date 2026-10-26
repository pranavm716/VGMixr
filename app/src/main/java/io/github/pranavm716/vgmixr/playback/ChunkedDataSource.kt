package io.github.pranavm716.vgmixr.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Reads [upstream] in byte ranges of at most [chunkBytes]. YouTube's media servers slow an
 * open-ended request to about playback speed (35 KB/s measured) but serve bounded ranges at full
 * speed (18 MB/s in 1 MB ranges), so downloads read through this.
 */
@UnstableApi
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkBytes: Long = DEFAULT_CHUNK_BYTES
) : DataSource {
    private var spec: DataSpec? = null

    /** Where the next range starts. */
    private var nextPosition = 0L

    /** Where the data ends (exclusive), once known. */
    private var end = UNKNOWN
    private var chunkLeft = 0L

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        nextPosition = dataSpec.position
        end = if (dataSpec.length == UNKNOWN) UNKNOWN else dataSpec.position + dataSpec.length
        openChunk()
        if (end == UNKNOWN) totalLength(upstream.responseHeaders)?.let { end = it }
        return if (end == UNKNOWN) UNKNOWN else end - dataSpec.position
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (chunkLeft == 0L) {
            if (end != UNKNOWN && nextPosition >= end) return C.RESULT_END_OF_INPUT
            upstream.close()
            openChunk()
            if (chunkLeft == 0L) return C.RESULT_END_OF_INPUT
        }
        val read = upstream.read(buffer, offset, minOf(length.toLong(), chunkLeft).toInt())
        if (read == C.RESULT_END_OF_INPUT) {
            // The server ended this range early: that is the end of the data.
            end = nextPosition - chunkLeft
            chunkLeft = 0
            return C.RESULT_END_OF_INPUT
        }
        chunkLeft -= read
        return read
    }

    private fun openChunk() {
        val current = checkNotNull(spec)
        val wanted = if (end == UNKNOWN) chunkBytes else minOf(chunkBytes, end - nextPosition)
        val opened = upstream.open(
            current.buildUpon().setPosition(nextPosition).setLength(wanted).build()
        )
        chunkLeft = if (opened == UNKNOWN) wanted else opened
        // A short range is the last one.
        if (chunkLeft < wanted) end = nextPosition + chunkLeft
        nextPosition += chunkLeft
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        spec = null
        chunkLeft = 0
        upstream.close()
    }

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource())
    }

    private companion object {
        val UNKNOWN = C.LENGTH_UNSET.toLong()

        // Small enough for full speed, large enough that a 400 MB album is ~100 requests.
        const val DEFAULT_CHUNK_BYTES = 4L * 1024 * 1024

        /** The total from a `Content-Range: bytes 0-999/1120667` header, if there is one. */
        fun totalLength(headers: Map<String, List<String>>): Long? = headers.entries
            .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value?.firstOrNull()
            ?.substringAfterLast('/')
            ?.toLongOrNull()
    }
}
