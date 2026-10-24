package io.github.pranavm716.vgmixr.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.pranavm716.vgmixr.di.ApplicationScope
import io.github.pranavm716.vgmixr.domain.usertrack.AddSharedVideo
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The share target for YouTube links. It shows nothing and finishes at once, leaving the user in
 * the app they shared from; the video is added in the background and a toast says how it went.
 */
@AndroidEntryPoint
class ShareActivity : ComponentActivity() {
    @Inject lateinit var addSharedVideo: AddSharedVideo

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
        // Theme.NoDisplay: the activity must finish before it would be shown.
        finish()
        if (text == null) return
        // The application's context outlives this activity, which is already gone.
        val app = applicationContext
        scope.launch {
            val toast = shareToast(addSharedVideo(text))
            withContext(Dispatchers.Main) {
                val message = app.getString(toast.text, toast.title)
                val length = if (toast.long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                Toast.makeText(app, message, length).show()
            }
        }
    }
}
