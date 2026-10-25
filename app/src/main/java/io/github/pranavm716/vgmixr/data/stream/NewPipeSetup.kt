package io.github.pranavm716.vgmixr.data.stream

import org.schabi.newpipe.extractor.NewPipe

/** NewPipe's one global setup, done by whichever of its users comes first. */
internal object NewPipeSetup {
    private var done = false

    @Synchronized
    fun ensure() {
        if (done) return
        NewPipe.init(OkHttpDownloader(youTubeClient()))
        done = true
    }
}
