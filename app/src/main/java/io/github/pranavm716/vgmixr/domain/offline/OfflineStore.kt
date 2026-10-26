package io.github.pranavm716.vgmixr.domain.offline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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

    /** Records that [videoId]'s audio is missing after all, so it is downloaded again. */
    suspend fun setIncomplete(videoId: String)

    companion object {
        /** Nothing on the phone: for screens and tests that don't care. */
        val NONE: OfflineStore = object : OfflineStore {
            override val entries: Flow<Map<String, OfflineEntry>> = flowOf(emptyMap())
            override val playableOffline: Flow<Set<String>> = flowOf(emptySet())
            override val usage: Flow<OfflineUsage> = flowOf(OfflineUsage(0, 0))
            override val onPhone: Flow<Set<String>> = flowOf(emptySet())

            override suspend fun apply(plan: OfflinePlan) = Unit

            override suspend fun setComplete(videoId: String, bytes: Long) = Unit

            override suspend fun setIncomplete(videoId: String) = Unit
        }
    }
}
