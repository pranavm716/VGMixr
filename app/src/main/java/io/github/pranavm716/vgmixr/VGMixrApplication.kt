package io.github.pranavm716.vgmixr

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.HiltAndroidApp
import io.github.pranavm716.vgmixr.data.catalog.DefaultCatalogSync
import io.github.pranavm716.vgmixr.data.diagnostics.BrokenMarksMirror
import io.github.pranavm716.vgmixr.playback.OfflineStarter
import javax.inject.Inject

@OptIn(UnstableApi::class)
@HiltAndroidApp
class VGMixrApplication : Application() {
    @Inject
    lateinit var catalogSync: DefaultCatalogSync

    @Inject
    lateinit var brokenMarksMirror: BrokenMarksMirror

    @Inject
    lateinit var offlineStarter: OfflineStarter

    override fun onCreate() {
        super.onCreate()
        catalogSync.start()
        brokenMarksMirror.start()
        offlineStarter.start()
    }
}
