package io.github.pranavm716.vgmixr.ui.settings

import io.github.pranavm716.vgmixr.domain.catalog.Family
import io.github.pranavm716.vgmixr.domain.playback.effectivePlaylists
import io.github.pranavm716.vgmixr.domain.playlist.PlaylistSummary

/** One row in a "Hold to play" picker; [id] is a family id or a playlist id as text. */
data class HoldChoice(val id: String, val name: String, val checked: Boolean)

/** A "Hold to play" picker's rows, with the summary its Settings row shows. */
data class HoldChoices(val choices: List<HoldChoice> = emptyList(), val summary: String = "")

/** Both pickers. */
data class HoldUiState(
    val games: HoldChoices = HoldChoices(),
    val playlists: HoldChoices = HoldChoices()
)

/** Every family, checked unless [excluded]; summary "All", "None" or "n of m". */
fun gameChoices(families: List<Family>, excluded: Set<String>): HoldChoices {
    val choices = families.map { HoldChoice(it.id, it.name, it.id !in excluded) }
    val checked = choices.count { it.checked }
    val summary = when (checked) {
        choices.size -> "All"
        0 -> "None"
        else -> "$checked of ${choices.size}"
    }
    return HoldChoices(choices, summary)
}

/** Every playlist, checked as [effectivePlaylists]; summary "All", up to two names, or "n of m". */
fun playlistChoices(playlists: List<PlaylistSummary>, selected: Set<Long>): HoldChoices {
    val effective = effectivePlaylists(playlists, selected)
    val choices = playlists.map { HoldChoice(it.id.toString(), it.name, it.id in effective) }
    val checked = choices.filter { it.checked }
    val summary = when {
        checked.size == choices.size && choices.size > 1 -> "All"
        checked.size <= 2 -> checked.joinToString(", ") { it.name }
        else -> "${checked.size} of ${choices.size}"
    }
    return HoldChoices(choices, summary)
}
