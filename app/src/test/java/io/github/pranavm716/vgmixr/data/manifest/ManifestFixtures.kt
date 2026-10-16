package io.github.pranavm716.vgmixr.data.manifest

import java.io.File

/** Reads the shared fixtures in `docs/fixtures/`, wired in as unit-test resources. */
object ManifestFixtures {
    fun read(path: String): String = resource(path).readText()

    /** Invalid fixture file name to its expected error code, from the README's fixture table. */
    val expectedCodes: Map<String, String> by lazy {
        tableRows("Invalid fixtures").associate { (file, code) -> file to code }
    }

    /** All error codes listed in the README's error code table. */
    val errorCodes: Set<String> by lazy {
        tableRows("Error codes").map { it.first() }.toSet()
    }

    /** File names in `invalid/`. */
    val invalidFiles: Set<String> by lazy {
        File(resource("invalid").toURI()).list().orEmpty().toSet()
    }

    private fun resource(path: String) =
        checkNotNull(javaClass.classLoader?.getResource(path)) { "Missing fixture resource: $path" }

    /** Body rows of the first table under `## <heading>`, cells trimmed and unquoted from backticks. */
    private fun tableRows(heading: String): List<List<String>> = read("README.md")
        .lineSequence()
        .dropWhile { it != "## $heading" }
        .drop(1)
        .takeWhile { !it.startsWith("## ") }
        .filter { it.startsWith("|") }
        .drop(2) // header and separator rows
        .map { line ->
            line.trim('|').split('|').map { it.trim().removeSurrounding("`") }
        }.toList()
}
