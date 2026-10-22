package io.github.pranavm716.vgmixr.domain.model

data class Track(
    val id: String,
    val familyId: String,
    val games: List<String>?,
    val title: String,
    val order: Int,
    val sources: List<Source>,
    val replaces: List<String> = emptyList()
)
