package io.github.pranavm716.vgmixr.domain.catalog

/** The bundled manifest's text. */
fun interface ManifestSource {
    /** The manifest JSON, or null if there is none. Blocking: call it off the main thread. */
    fun read(): String?
}
