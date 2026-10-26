package io.github.pranavm716.vgmixr.data.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pranavm716.vgmixr.domain.offline.RecentCutoff
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [RecentCutoff] in SharedPreferences, as epoch millis. */
@Singleton
class PrefsRecentCutoff @Inject constructor(
    @ApplicationContext context: Context,
    private val clock: Clock
) : RecentCutoff {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(Instant.ofEpochMilli(prefs.getLong(SINCE, 0)))
    override val since: StateFlow<Instant> = state

    override fun clearNow() {
        val now = clock.instant()
        prefs.edit { putLong(SINCE, now.toEpochMilli()) }
        state.value = now
    }

    private companion object {
        const val PREFS = "settings"
        const val SINCE = "recentClearedAt"
    }
}
