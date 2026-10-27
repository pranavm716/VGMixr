package io.github.pranavm716.vgmixr.data.usertrack

import io.github.pranavm716.vgmixr.domain.usertrack.ThumbnailStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Thumbnails as `<videoId>.jpg` in [dir], from YouTube's image server, which has every video's
 * thumbnail at a fixed address per size: no lookup and no player request, so no bot check.
 */
class FileThumbnailStore(
    private val dir: File,
    private val client: OkHttpClient,
    private val io: CoroutineDispatcher,
    private val baseUrl: String = "https://i.ytimg.com/vi/"
) : ThumbnailStore {
    override val version = MutableStateFlow(0)

    override fun file(videoId: String): File? = fileFor(videoId).takeIf { it.isFile }

    override suspend fun download(videoId: String): Boolean = withContext(io) {
        if (fileFor(videoId).isFile) return@withContext true
        val image = SIZES.firstNotNullOfOrNull { fetch("$baseUrl$videoId/$it.jpg") }
            ?: return@withContext false
        // Written whole, then renamed, so a half-written file is never shown. A full disk or an
        // unwritable folder is a failed download, tried again later.
        val saved = try {
            dir.mkdirs()
            val part = File(dir, "$videoId.part")
            part.writeBytes(image)
            part.renameTo(fileFor(videoId))
        } catch (e: IOException) {
            false
        }
        saved.also { if (it) version.update { v -> v + 1 } }
    }

    override suspend fun delete(videoId: String) {
        withContext(io) { fileFor(videoId).delete() }
    }

    /** The image at [url], or null if there is none (a 404) or it can't be fetched. */
    private fun fetch(url: String): ByteArray? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    } catch (e: IOException) {
        null
    }

    private fun fileFor(videoId: String) = File(dir, "$videoId.jpg")

    private companion object {
        /**
         * Largest first. Only maxresdefault is missing for some videos; sddefault and hqdefault
         * are 4:3 with the video letterboxed in them.
         */
        val SIZES = listOf("maxresdefault", "sddefault", "hqdefault")
    }
}
