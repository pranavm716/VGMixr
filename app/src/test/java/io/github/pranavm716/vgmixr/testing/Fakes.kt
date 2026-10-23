package io.github.pranavm716.vgmixr.testing

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogImportResult
import io.github.pranavm716.vgmixr.domain.catalog.CatalogRepository
import io.github.pranavm716.vgmixr.domain.catalog.CatalogSync
import io.github.pranavm716.vgmixr.domain.history.Play
import io.github.pranavm716.vgmixr.domain.history.PlayHistory
import io.github.pranavm716.vgmixr.domain.playback.BrokenMark
import io.github.pranavm716.vgmixr.domain.playback.BrokenReason
import io.github.pranavm716.vgmixr.domain.playback.BrokenSources
import io.github.pranavm716.vgmixr.domain.playback.PlaybackController
import io.github.pranavm716.vgmixr.domain.playback.PlaybackEvent
import io.github.pranavm716.vgmixr.domain.playback.PlaybackProgress
import io.github.pranavm716.vgmixr.domain.playback.PlaybackState
import io.github.pranavm716.vgmixr.domain.playback.QueueContext
import io.github.pranavm716.vgmixr.domain.playback.TryAgainResult
import io.github.pranavm716.vgmixr.domain.playlist.Playlist
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistRepository
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary
import io.github.pranavm716.vgmixr.domain.settings.Accent
import io.github.pranavm716.vgmixr.domain.settings.AccentSetting
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class FakeCatalogRepository(initial: Catalog = Catalog(emptyList(), emptyList(), emptyList())) :
    CatalogRepository {
    override val catalog = MutableStateFlow(initial)
}

class FakeCatalogSync(initial: CatalogImportResult? = null) : CatalogSync {
    override val status = MutableStateFlow(initial)
}

class FakePlaybackController : PlaybackController {
    data class PlayFrom(val trackIds: List<String>, val index: Int, val context: QueueContext)

    data class AddToQueue(val trackIds: List<String>)

    data object TogglePlayPause

    data class SetShuffle(val on: Boolean)

    data object SkipToNext

    data object SkipToPrevious

    data object RestartOrSkipToPrevious

    data class SeekTo(val positionMs: Long)

    data class JumpTo(val index: Int)

    data class RemoveFromQueue(val index: Int)

    data object ClearQueue

    data class TryAgain(val trackId: String)

    override val state = MutableStateFlow(PlaybackState())
    override val progress = MutableStateFlow(PlaybackProgress(positionMs = 0, durationMs = null))
    override val events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
    val calls = mutableListOf<Any>()

    override fun togglePlayPause() {
        calls += TogglePlayPause
    }

    /** Flips the state's flag too, as the player does. */
    override fun setShuffle(on: Boolean) {
        calls += SetShuffle(on)
        state.value = state.value.copy(shuffle = on)
    }

    override fun skipToNext() {
        calls += SkipToNext
    }

    override fun skipToPrevious() {
        calls += SkipToPrevious
    }

    override fun restartOrSkipToPrevious() {
        calls += RestartOrSkipToPrevious
    }

    override fun seekTo(positionMs: Long) {
        calls += SeekTo(positionMs)
    }

    override fun jumpTo(index: Int) {
        calls += JumpTo(index)
    }

    override fun removeFromQueue(index: Int) {
        calls += RemoveFromQueue(index)
    }

    override fun clearQueue() {
        calls += ClearQueue
    }

    var tryAgainResult: TryAgainResult = TryAgainResult.Back

    override suspend fun tryAgain(trackId: String): TryAgainResult {
        calls += TryAgain(trackId)
        return tryAgainResult
    }

    override fun playFrom(trackIds: List<String>, index: Int, context: QueueContext) {
        calls += PlayFrom(trackIds, index, context)
    }

    override fun addToQueue(trackIds: List<String>) {
        calls += AddToQueue(trackIds)
    }
}

class FakePlayHistory(initial: List<Play> = emptyList()) : PlayHistory {
    override val plays = MutableStateFlow(initial)

    override suspend fun record(trackId: String) {
        plays.value += Play(trackId, Instant.EPOCH)
    }
}

class FakeBrokenSources(initial: Map<String, BrokenMark> = emptyMap()) : BrokenSources {
    override val marks = MutableStateFlow(initial)

    val marked = mutableListOf<BrokenMark>()

    override suspend fun mark(
        videoId: String,
        trackId: String,
        reason: BrokenReason,
        detail: String?
    ): Boolean {
        if (videoId in marks.value) return false
        val mark = BrokenMark(videoId, trackId, reason, detail, Instant.EPOCH, null)
        marked += mark
        marks.value += videoId to mark
        return true
    }

    override suspend fun clear(videoId: String) {
        marks.value -= videoId
    }
}

/**
 * Playlists in memory: [playlists] starts with [initialPlaylists] (null: nothing until a test
 * emits), [favorites] holds the favorited track ids, [contents] each playlist's page.
 */
class FakePlaylistRepository(
    favorites: Set<String> = emptySet(),
    initialPlaylists: List<PlaylistSummary>? = emptyList()
) : PlaylistRepository {
    override val playlists = MutableSharedFlow<List<PlaylistSummary>>(replay = 1).apply {
        initialPlaylists?.let(::tryEmit)
    }
    override val favorites = MutableStateFlow(favorites)
    val contents = MutableStateFlow<Map<Long, Playlist>>(emptyMap())

    override fun playlist(id: Long): Flow<Playlist?> = contents.map { it[id] }

    override val tracksInPlaylists: Flow<Set<String>> =
        combine(contents, this.favorites) { lists, favorites ->
            lists.values.flatMap { it.trackIds }.toSet() + favorites
        }

    /** The [contents] playlists holding [trackId], plus Favorites (id 1) if [favorites] has it. */
    override fun playlistsContaining(trackId: String): Flow<Set<Long>> =
        combine(contents, favorites) { lists, favorites ->
            lists.values.filter { trackId in it.trackIds }.map { it.id }.toSet() +
                (if (trackId in favorites) setOf(1L) else emptySet())
        }

    override suspend fun setFavorite(trackId: String, favorite: Boolean) {
        favorites.value = if (favorite) favorites.value + trackId else favorites.value - trackId
    }

    /** Adds to the front (newest first) or removes from [contents]' playlist [playlistId]. */
    override suspend fun setInPlaylist(playlistId: Long, trackId: String, inPlaylist: Boolean) {
        val playlist = contents.value[playlistId] ?: return
        val others = playlist.trackIds - trackId
        val ids = when {
            !inPlaylist -> others
            trackId in playlist.trackIds -> playlist.trackIds
            else -> listOf(trackId) + others
        }
        contents.value += playlistId to playlist.copy(trackIds = ids)
    }

    override suspend fun renamePlaylist(id: Long, name: String) {
        val playlist = contents.value[id] ?: return
        contents.value += id to playlist.copy(name = name.trim())
    }

    override suspend fun deletePlaylist(id: Long) {
        contents.value -= id
    }

    /** The names passed to [createPlaylist], in order; new ids count up from 100. */
    val created = mutableListOf<String>()

    override suspend fun createPlaylist(name: String): Long {
        created += name
        val id = 99L + created.size
        contents.value += id to Playlist(id, name, isFavorites = false, trackIds = emptyList())
        return id
    }
}

class FakeAccentSetting(initial: Accent = Accent.DEFAULT) : AccentSetting {
    override val accent = MutableStateFlow(initial)

    override fun set(accent: Accent) {
        this.accent.value = accent
    }
}
