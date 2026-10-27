package io.github.pranavm716.vgmixr.domain.usertrack

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * User tracks' video thumbnails, kept as files so they show offline. A thumbnail's address follows
 * from its video id, so a missing one can be downloaded again any time.
 */
interface ThumbnailStore {
    /** [videoId]'s thumbnail file, or null if it isn't downloaded. */
    fun file(videoId: String): File?

    /** Downloads [videoId]'s thumbnail unless it is there already; false if it couldn't. */
    suspend fun download(videoId: String): Boolean

    suspend fun delete(videoId: String)

    /**
     * Goes up each time a thumbnail file lands, so the catalog re-reads paths that no database
     * change would refresh.
     */
    val version: StateFlow<Int>

    companion object {
        /** No thumbnails at all, for code that doesn't show them. */
        val NONE = object : ThumbnailStore {
            override val version: StateFlow<Int> = MutableStateFlow(0)

            override fun file(videoId: String): File? = null

            override suspend fun download(videoId: String) = false

            override suspend fun delete(videoId: String) = Unit
        }
    }
}
