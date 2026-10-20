package io.github.pranavm716.vgmixr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import dagger.hilt.android.AndroidEntryPoint
import io.github.pranavm716.vgmixr.ui.AppScreen
import io.github.pranavm716.vgmixr.ui.VGMixrApp
import io.github.pranavm716.vgmixr.ui.theme.Background
import io.github.pranavm716.vgmixr.ui.theme.VGMixrTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = SystemBarStyle.dark(Background.toArgb())
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            VGMixrTheme {
                VGMixrApp(screen = { destination, nav -> AppScreen(destination, nav) })
            }
        }
    }
}
