// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.cipher

/** Persists the last verified cipher config and the version it carried (rollback protection). */
interface CipherConfigStore {
    fun readConfig(): String?
    fun lastVersion(): Long
    fun write(json: String, version: Long)
}
