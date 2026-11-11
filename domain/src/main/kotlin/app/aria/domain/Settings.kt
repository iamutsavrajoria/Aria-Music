// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val historyThresholdMs: Long = DEFAULT_HISTORY_THRESHOLD_MS,
    val endlessRadio: Boolean = true,
    val skipSilence: Boolean = false,
    val normalizeLoudness: Boolean = true,
    val sleepTimerDefaultMinutes: Int = 15,
    val onboardingDone: Boolean = false,
) {
    fun normalized(): AppSettings = copy(
        historyThresholdMs = historyThresholdMs.coerceIn(MIN_HISTORY_THRESHOLD_MS, MAX_HISTORY_THRESHOLD_MS),
        sleepTimerDefaultMinutes = sleepTimerDefaultMinutes.coerceIn(1, 240),
    )

    companion object {
        const val MIN_HISTORY_THRESHOLD_MS = 10_000L
        const val MAX_HISTORY_THRESHOLD_MS = 120_000L
        const val DEFAULT_HISTORY_THRESHOLD_MS = 30_000L
    }
}

interface SettingsRepository {
    /** Emits the stored settings (defaults when nothing is stored), already normalized. */
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
