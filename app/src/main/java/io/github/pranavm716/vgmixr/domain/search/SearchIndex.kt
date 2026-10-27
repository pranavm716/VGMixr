package io.github.pranavm716.vgmixr.domain.search

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack

/**
 * The catalog's tracks and the user's tracks with their words split out once, so a keystroke only
 * compares words. Build one per catalog.
 */
class SearchIndex(catalog: Catalog) {
    internal val entries: List<IndexedTrack> = run {
        val familyPosition = catalog.families.withIndex().associate { (i, f) -> f.id to i }
        val familyWords = catalog.families.associate { family ->
            family.id to buildSet {
                add(family.id)
                add(joined(family.label))
                addAll(FAMILY_REGIONS[family.id].orEmpty())
            }
        }
        val catalogTracks = catalog.tracks.map { track ->
            IndexedTrack(
                track = track,
                title = searchWords(track.title),
                meta = (familyWords[track.familyId].orEmpty() + track.gameWords()).toList(),
                familyPosition = familyPosition[track.familyId] ?: Int.MAX_VALUE,
                order = track.order
            )
        }
        val userTracks = catalog.userTracks.map { track ->
            val original = track.remixOf?.let { id -> catalog.tracks.firstOrNull { it.id == id } }
            val meta = buildSet {
                addAll(familyWords[track.familyId].orEmpty())
                addAll(track.gameWords())
                // Linked, it shows its original's title; its own still finds it, for less.
                if (original != null) addAll(searchWords(track.videoTitle.orEmpty()))
                addAll(channelWords(track.channel.orEmpty()))
                // A credit counts like the channel it replaces, which still finds the track.
                track.credit?.let { addAll(channelWords(it)) }
                addAll(REMIX_WORDS)
            }
            IndexedTrack(
                track = track,
                title = searchWords(track.title),
                meta = meta.toList(),
                // Unlinked, it has no family and comes after every family's tracks.
                familyPosition = familyPosition[track.familyId] ?: Int.MAX_VALUE,
                order = original?.order ?: 0,
                isUserTrack = true
            )
        }
        catalogTracks + userTracks
    }
}

/**
 * [title] holds the title's words in order; [meta] its family, games, generations and region, and
 * for a user track its video's title, its channel, its credit and "remix", which match for less. [order] places
 * it in its family: a remix takes its original's, and [isUserTrack] puts it right after it.
 */
internal class IndexedTrack(
    val track: CatalogTrack,
    val title: List<String>,
    val meta: List<String>,
    val familyPosition: Int,
    val order: Int,
    val isUserTrack: Boolean = false
)

private fun CatalogTrack.gameWords(): Set<String> = buildSet {
    games.forEach { game ->
        addAll(searchWords(game.name))
        add(joined(game.shortName))
        game.generation?.let { add("gen$it") }
    }
}

// A label or short name is one word: "Z-A" is "za", "DPPt" is "dppt".
private fun joined(text: String) = searchWords(text).joinToString("")

/**
 * A channel's name as one word and its words, also split where a capital follows a small letter:
 * "GlitchxCity" is "glitchxcity", "glitchx" and "city".
 */
private fun channelWords(channel: String): Set<String> = buildSet {
    add(joined(channel))
    addAll(searchWords(channel))
    channel.split(CAMEL_CASE).forEach { addAll(searchWords(it)) }
}

private val CAMEL_CASE = Regex("(?<=\\p{Ll})(?=\\p{Lu})")

// Every user track is a remix, whatever its video's title calls it ("cover", "orchestrated").
private val REMIX_WORDS = listOf("remix", "remixes")
