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
}
