package io.github.pranavm716.vgmixr.domain.model

data class Game(
    val id: String,
    val name: String,
    val shortName: String,
    val color: String,
    val generation: Int?,
    val platform: String,
    val familyId: String
)
