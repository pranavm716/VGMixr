package io.github.pranavm716.vgmixr.domain.model

enum class Category {
    BATTLE,
    ROUTE,
    TOWN,
    DUNGEON,
    EVENT,
    OTHER;

    companion object {
        fun fromManifest(value: String): Category =
            Category.entries.firstOrNull { it.name.lowercase() == value }
                ?: OTHER
    }
}

data class Track(
    val id: String,
    val familyId: String,
    val games: List<String>?,
    val title: String,
    val category: Category,
    val order: Int,
    val sources: List<Source>,
    val replaces: List<String> = emptyList()
)
