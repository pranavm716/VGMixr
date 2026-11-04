package io.github.pranavm716.vgmixr.domain.history

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The playing track once its visit has counted as a play (see [PlayCounter]), else null. */
@Singleton
class CurrentPlay @Inject constructor() {
    private val state = MutableStateFlow<String?>(null)
    val counted: StateFlow<String?> = state.asStateFlow()

    /** Written by the playback service after each [PlayCounter] update. */
    fun set(trackId: String?) {
        state.value = trackId
    }
}
