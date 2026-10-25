package io.github.pranavm716.vgmixr.domain.search

/**
 * Short query words and the words they stand for. A query word in this table matches a track
 * through itself or through all of its expansion's words. Family ids and labels ("oras",
 * "hgss") need no entry: every track is indexed under its family's.
 */
val SEARCH_ALIASES: Map<String, List<String>> = mapOf(
    "e4" to listOf("elite", "four"),
    "vs" to listOf("battle"),
    "rt" to listOf("route"),
    "pc" to listOf("pokemon", "center"),
    // Black 2 and White 2's tracks carry the short name B2.
    "b2w2" to listOf("b2"),
    "bw2" to listOf("b2"),
    // Ultra Sun and Ultra Moon's tracks carry US.
    "usum" to listOf("us"),
    "pmd" to listOf("mystery", "dungeon"),
    "brt" to listOf("blue", "rescue", "team"),
    "eos" to listOf("explorers", "sky")
)

/**
 * Each family's main region, by family id, as extra search words. Only the main one, so "kanto"
 * doesn't list all of Gold and Silver; a title naming a place still matches by title. A family
 * missing here has no region words.
 */
val FAMILY_REGIONS: Map<String, List<String>> = mapOf(
    "rby" to listOf("kanto"),
    "frlg" to listOf("kanto"),
    "lgpe" to listOf("kanto"),
    "gsc" to listOf("johto"),
    "hgss" to listOf("johto"),
    "rse" to listOf("hoenn"),
    "oras" to listOf("hoenn"),
    "dppt" to listOf("sinnoh"),
    "bdsp" to listOf("sinnoh"),
    // Hisui is Sinnoh long ago.
    "pla" to listOf("hisui", "sinnoh"),
    "bw" to listOf("unova"),
    "xy" to listOf("kalos"),
    "plza" to listOf("kalos"),
    "sm" to listOf("alola"),
    "swsh" to listOf("galar"),
    "sv" to listOf("paldea")
)
