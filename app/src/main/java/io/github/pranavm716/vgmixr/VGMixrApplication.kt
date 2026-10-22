package io.github.pranavm716.vgmixr

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.pranavm716.vgmixr.data.catalog.DefaultCatalogSync
import io.github.pranavm716.vgmixr.data.diagnostics.BrokenMarksMirror
import javax.inject.Inject

@HiltAndroidApp
class VGMixrApplication : Application() {
    @Inject
    lateinit var catalogSync: DefaultCatalogSync

    @Inject
    lateinit var brokenMarksMirror: BrokenMarksMirror

    override fun onCreate() {
        super.onCreate()
        catalogSync.start()
        brokenMarksMirror.start()
    }
}
