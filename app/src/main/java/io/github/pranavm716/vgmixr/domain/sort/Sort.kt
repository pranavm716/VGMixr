package io.github.pranavm716.vgmixr.domain.sort

import io.github.pranavm716.vgmixr.domain.catalog.Catalog
import io.github.pranavm716.vgmixr.domain.catalog.CatalogTrack
import io.github.pranavm716.vgmixr.domain.usertrack.UserTrackId
import java.text.Normalizer
import java.time.Instant

/** What a collection sorts by. Which keys a collection offers is in [SortKeys]. */
enum class SortKey { Order, Title, Game, RecentlyAdded, Channel }

enum class SortDirection { Ascending, Descending }

/** A collection's sort: [key] in [direction]. Descending only reverses the key, never ties. */
data class SortSetting(val key: SortKey, val direction: SortDirection) {
    /** Picking a key selects it, ascending. */
    fun withKey(key: SortKey) = SortSetting(key, SortDirection.Ascending)

    /** The arrow: the same key the other way. */
    fun reversed() = copy(
        direction = if (direction == SortDirection.Ascending) {
            SortDirection.Descending
        } else {
            SortDirection.Ascending
        }
    )
}

/** The keys each collection offers, and its default (today's order before sorting existed). */
object SortKeys {
    val family = listOf(SortKey.Order, SortKey.Title)
    val playlist = listOf(SortKey.Title, SortKey.Game, SortKey.RecentlyAdded)
    val remixes = listOf(SortKey.Title, SortKey.Channel, SortKey.Game, SortKey.RecentlyAdded)

    val familyDefault = SortSetting(SortKey.Order, SortDirection.Ascending)
    val playlistDefault = SortSetting(SortKey.RecentlyAdded, SortDirection.Descending)
    val remixesDefault = SortSetting(SortKey.RecentlyAdded, SortDirection.Descending)
}

/**
 * A track as sorting sees it. [title] is the one its row shows. [familyPosition] and [order] place
 * it in the catalog: a linked remix has its original's, and an unlinked one a null
 * [familyPosition]. [addedAt] is when it went into the collection (a playlist entry's, or a user
 * track's own in Remixes); null on a family page.
 */
data class SortRow(
    val id: String,
    val title: String,
    val familyPosition: Int?,
    val order: Int,
    val isUserTrack: Boolean = false,
    val channel: String? = null,
    val addedAt: Instant? = null
)

/** [rows] in [setting]'s order, by the roadmap's step 15b rules. */
fun List<SortRow>.sortedBy(setting: SortSetting): List<SortRow> {
    val descending = setting.direction == SortDirection.Descending
    fun <T : Comparable<T>> main(selector: (SortRow) -> T?): Comparator<SortRow> =
        compareBy(selector).let { if (descending) it.reversed() else it }

    val comparator = when (setting.key) {
        SortKey.Order -> main { it.order }
        SortKey.Title -> main { it.titleKey }.then(game(reverseFamilies = false)).then(newest)
        SortKey.Game -> game(reverseFamilies = descending)
        SortKey.Channel -> main { it.channel?.let(::fold) }.then(game(reverseFamilies = false))
        SortKey.RecentlyAdded -> main { it.addedAt }.then(game(reverseFamilies = false))
    }
    return sortedWith(comparator.thenBy { it.id })
}

private val newest: Comparator<SortRow> = compareByDescending { it.addedAt }

/**
 * Family (reversed if [reverseFamilies]), then track order, an original before its remixes and
 * remixes of one original newest first; unlinked remixes after every family, by title.
 */
private fun game(reverseFamilies: Boolean): Comparator<SortRow> {
    val family: Comparator<SortRow> = compareBy { it.familyPosition }
    return compareBy<SortRow> { it.familyPosition == null }
        .then(if (reverseFamilies) family.reversed() else family)
        .thenBy { it.order }
        .thenBy { it.isUserTrack }
        .then(newest)
        .thenBy { it.titleKey }
}

private val SortRow.titleKey: String get() = fold(title)

/** [text] lowercased without accents, so "Élite" sorts with "elite". */
private fun fold(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")

private val MARKS = Regex("\\p{Mn}+")

/**
 * [track] as a [SortRow], added to its collection at [addedAt]. A linked remix takes its original's
 * family and order; an unlinked one has no family.
 */
fun Catalog.sortRow(track: CatalogTrack, addedAt: Instant?): SortRow {
    val placed = track.remixOf?.let(::track) ?: track
    return SortRow(
        id = track.id,
        title = track.title,
        familyPosition = families.indexOfFirst { it.id == placed.familyId }.takeIf { it >= 0 },
        order = placed.order,
        isUserTrack = UserTrackId.isUserTrack(track.id),
        channel = track.channel,
        addedAt = addedAt
    )
}

/**
 * A sort stored by its [key] and [direction] names; unset, or a key [keys] doesn't offer, is
 * [default].
 */
fun sortSettingOf(
    key: String?,
    direction: String?,
    keys: List<SortKey>,
    default: SortSetting
): SortSetting {
    val sortKey = keys.firstOrNull { it.name == key }
    val sortDirection = SortDirection.entries.firstOrNull { it.name == direction }
    return if (sortKey != null && sortDirection != null) {
        SortSetting(sortKey, sortDirection)
    } else {
        default
    }
}
