package io.github.pranavm716.vgmixr.ui.components

import android.content.res.AssetManager

/** The assets folder of the family covers the pipeline writes (`catalog/covers/<family>.webp`). */
const val COVERS_DIR = "covers"

/** The asset path of [familyId]'s cover, which exists only if the family has one. */
fun coverAsset(familyId: String): String = "$COVERS_DIR/$familyId.webp"

/** The families with a bundled cover, read from the covers folder's file names. */
fun coverFamilies(assets: AssetManager): Set<String> = assets.list(COVERS_DIR).orEmpty()
    .filter { it.endsWith(".webp") }
    .map { it.removeSuffix(".webp") }
    .toSet()
