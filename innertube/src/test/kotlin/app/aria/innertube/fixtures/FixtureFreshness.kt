// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.fixtures

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object FixtureFreshness {
    const val DIR = "src/test/resources/fixtures"
    const val MAX_AGE_DAYS = 60L
    fun check(capturedAt: LocalDate, today: LocalDate): String? {
        val age = ChronoUnit.DAYS.between(capturedAt, today)
        return if (age > MAX_AGE_DAYS) "Live fixtures are $age days old (limit $MAX_AGE_DAYS). Run scripts/capture-innertube-fixtures on a maintainer machine, review the diff and commit." else null
    }

    /** Null when [dir] holds no fixtures at all (gate open) or a manifest that is fresh; a message otherwise. Fixture JSON without a manifest is a failure. */
    fun checkDir(dir: java.io.File, today: LocalDate): String? {
        val manifest = java.io.File(dir, "manifest.json")
        if (!manifest.exists()) {
            val others = dir.listFiles { f -> f.isFile && f.extension == "json" }.orEmpty()
            return if (others.isEmpty()) null
            else "Fixture files exist (${others.joinToString { it.name }}) but manifest.json is missing, so their age cannot be checked. Re-run scripts/capture-innertube-fixtures."
        }
        val date = Regex("\"capturedAt\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"").find(manifest.readText())?.groupValues?.get(1)
            ?: return "manifest.json has no capturedAt date."
        return check(LocalDate.parse(date), today)
    }
}
