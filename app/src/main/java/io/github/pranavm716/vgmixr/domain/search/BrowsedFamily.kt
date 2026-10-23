package io.github.pranavm716.vgmixr.domain.search

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The family page opened last, which search lifts a little. Kept in memory only: it is the
 * session's context, so it is gone once the app's process is.
 */
@Singleton
class BrowsedFamily @Inject constructor() {
    private val opened = MutableStateFlow<String?>(null)

    val familyId: StateFlow<String?> = opened.asStateFlow()

    fun opened(familyId: String) {
        opened.value = familyId
    }
}
