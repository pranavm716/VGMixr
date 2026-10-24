package io.github.pranavm716.vgmixr.ui.share

import androidx.annotation.StringRes
import io.github.pranavm716.vgmixr.R
import io.github.pranavm716.vgmixr.domain.playback.ResolveFailure
import io.github.pranavm716.vgmixr.domain.usertrack.AddVideoResult

/** The toast a share ends with: [text], filled in with [title] if it names the video. */
data class ShareToast(
    @StringRes val text: Int,
    val title: String? = null,
    val long: Boolean = false
)

/** Says what sharing a video did; a video that wasn't added stays up longer, to say why. */
fun shareToast(result: AddVideoResult): ShareToast = when (result) {
    is AddVideoResult.Added -> ShareToast(R.string.share_added, result.title)

    is AddVideoResult.AlreadyAdded -> ShareToast(R.string.share_already_added, result.title)

    AddVideoResult.NotAVideoLink -> ShareToast(R.string.share_not_a_video, long = true)

    AddVideoResult.NoAudio -> ShareToast(R.string.share_no_audio, long = true)

    is AddVideoResult.Unavailable -> ShareToast(R.string.share_unavailable, long = true)

    is AddVideoResult.Failed -> ShareToast(
        when (result.failure) {
            ResolveFailure.NETWORK -> R.string.share_failed_network
            ResolveFailure.BOT_CHECK -> R.string.share_failed_bot_check
            ResolveFailure.EXTRACTOR -> R.string.share_failed_extractor
        },
        long = true
    )
}
