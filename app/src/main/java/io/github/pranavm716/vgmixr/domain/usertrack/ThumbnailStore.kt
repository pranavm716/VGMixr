package io.github.pranavm716.vgmixr.domain.usertrack

import java.io.File

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
}
