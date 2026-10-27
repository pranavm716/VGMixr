package io.github.pranavm716.vgmixr.domain.usertrack

/**
 * The names the credit dialog offers [own] besides its own channel (which comes first, on its
 * own): every name a Remixes row shows, its credit or its channel, each once ignoring case (the
 * first spelling, newest first), A to Z. Picking from these keeps one artist from splitting in two.
 */
fun creditChoices(tracks: List<UserTrack>, own: UserTrack): List<String> = tracks
    .map { it.artist }
    .distinctBy { it.lowercase() }
    .filterNot { it.equals(own.channel, ignoreCase = true) }
    .sortedWith(String.CASE_INSENSITIVE_ORDER)

/**
 * The credit to store for [own] when [name] is picked or typed: trimmed, in a matching choice's
 * spelling (ignoring case). Null, which shows the channel, for its own channel or a blank name.
 */
fun creditFor(name: String, own: UserTrack, choices: List<String>): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || trimmed.equals(own.channel, ignoreCase = true)) return null
    return choices.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: trimmed
}
