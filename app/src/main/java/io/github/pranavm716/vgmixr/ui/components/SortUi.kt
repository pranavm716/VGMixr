package io.github.pranavm716.vgmixr.ui.components

import io.github.pranavm716.vgmixr.domain.sort.SortKey
import io.github.pranavm716.vgmixr.domain.sort.SortSetting

/** A collection's sort row: the [selected] sort and the [keys] its menu offers. */
data class SortUi(val selected: SortSetting, val keys: List<SortKey>)
