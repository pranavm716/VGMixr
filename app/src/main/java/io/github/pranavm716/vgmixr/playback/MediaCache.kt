package io.github.pranavm716.vgmixr.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * The key a stream's audio is stored under: its video id, so a download and a played stream of
 * one video are the same bytes, and an album video's slices share them.
 */
fun streamCacheKey(uri: String): String =
    if (uri.startsWith(STREAM_URI_PREFIX)) uri.removePrefix(STREAM_URI_PREFIX) else uri

/**
 * The one store of downloaded and cached audio, in app storage (`files/`, which Android doesn't
 * clear on its own). It never evicts: the offline rules decide what stays (roadmap step 13).
 * Only one instance may exist per folder.
 */
@UnstableApi
fun mediaCache(context: Context, database: DatabaseProvider): Cache = SimpleCache(
    File(context.filesDir, "media-cache"),
    NoOpCacheEvictor(),
    database
)
