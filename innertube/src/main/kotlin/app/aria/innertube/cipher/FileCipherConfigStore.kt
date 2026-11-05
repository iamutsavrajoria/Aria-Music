// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.cipher

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Stores the config and its version as two files in [dir], each replaced by an atomic rename. */
class FileCipherConfigStore(private val dir: File) : CipherConfigStore {
    private val configFile get() = File(dir, "config.json")
    private val versionFile get() = File(dir, "version.txt")

    override fun readConfig(): String? =
        try {
            if (configFile.isFile) configFile.readText() else null
        } catch (_: java.io.IOException) {
            null
        }

    override fun lastVersion(): Long =
        try {
            if (versionFile.isFile) versionFile.readText().trim().toLongOrNull() ?: 0L else 0L
        } catch (_: java.io.IOException) {
            0L
        }

    @Synchronized
    override fun write(json: String, version: Long) {
        dir.mkdirs()
        atomicWrite(configFile, json)
        atomicWrite(versionFile, version.toString())
    }

    private fun atomicWrite(target: File, text: String) {
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(text)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
