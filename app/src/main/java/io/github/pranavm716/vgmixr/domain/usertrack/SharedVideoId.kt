package io.github.pranavm716.vgmixr.domain.usertrack

import java.net.URI
import java.net.URISyntaxException

/**
 * The video id in text shared to the app: a YouTube link, alone or among other words as share
 * sheets send it, or a bare id. Null when there is none. Mirrors the pipeline's `video_id_from`;
 * only the id is ever kept, never the link.
 */
fun sharedVideoId(text: String): String? =
    videoIdFrom(text.trim()) ?: text.split(WHITESPACE).firstNotNullOfOrNull(::videoIdFrom)

private fun videoIdFrom(text: String): String? {
    if (VIDEO_ID.matches(text)) return text
    val uri = try {
        URI(if ("//" in text) text else "https://$text")
    } catch (e: URISyntaxException) {
        return null
    }
    val host = uri.host?.removePrefix("www.") ?: return null
    val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
    val candidate = when {
        host == "youtu.be" -> segments.firstOrNull()
        host in HOSTS && segments == listOf("watch") -> queryParameter(uri.rawQuery, "v")
        host in HOSTS && segments.size == 2 && segments[0] in ID_PATHS -> segments[1]
        else -> null
    }
    return candidate?.takeIf(VIDEO_ID::matches)
}

private fun queryParameter(query: String?, name: String): String? = query
    ?.split('&')
    ?.map { it.substringBefore('=') to it.substringAfter('=', "") }
    ?.firstOrNull { it.first == name }
    ?.second

private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
private val WHITESPACE = Regex("\\s+")
private val HOSTS =
    setOf("youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com")

// Paths whose next segment is the id: /shorts/ID, /embed/ID, /live/ID.
private val ID_PATHS = setOf("shorts", "embed", "live")
