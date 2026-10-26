package io.github.pranavm716.vgmixr.domain.offline

import kotlinx.coroutines.flow.Flow

/** Space taken by finished videos, per [OfflineKind]. */
data class OfflineUsage(val downloadedBytes: Long, val cachedBytes: Long)

/** Which videos are on the phone (or on their way), and why. The files themselves live elsewhere. */
interface OfflineStore {
    /** Every entry, by video id. */
    val entries: Flow<Map<String, OfflineEntry>>

    /** The tracks a finished video plays, so they play without a connection. */
    val playableOffline: Flow<Set<String>>

    val usage: Flow<OfflineUsage>

    /** The finished videos: they play from the phone, even once marked broken. */
    val onPhone: Flow<Set<String>>

    /** Adds or updates [OfflinePlan.upsert] (a finished video stays finished) and deletes the rest. */
    suspend fun apply(plan: OfflinePlan)

    /** Records that [videoId] is fully on the phone, taking [bytes]. */
    suspend fun setComplete(videoId: String, bytes: Long)
}
