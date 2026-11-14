// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import app.aria.domain.AppSettings
import app.aria.domain.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException

/** A corrupt settings file is replaced by defaults; settings are never worth a crash. */
fun createSettingsDataStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { file },
    )

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private object Keys {
        val historyThresholdMs = longPreferencesKey("history_threshold_ms")
        val endlessRadio = booleanPreferencesKey("endless_radio")
        val skipSilence = booleanPreferencesKey("skip_silence")
        val normalizeLoudness = booleanPreferencesKey("normalize_loudness")
        val sleepDefaultMinutes = intPreferencesKey("sleep_default_minutes")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    override val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val next = transform(p.toSettings()).normalized()
            p[Keys.historyThresholdMs] = next.historyThresholdMs
            p[Keys.endlessRadio] = next.endlessRadio
            p[Keys.skipSilence] = next.skipSilence
            p[Keys.normalizeLoudness] = next.normalizeLoudness
            p[Keys.sleepDefaultMinutes] = next.sleepTimerDefaultMinutes
            p[Keys.onboardingDone] = next.onboardingDone
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            historyThresholdMs = this[Keys.historyThresholdMs] ?: d.historyThresholdMs,
            endlessRadio = this[Keys.endlessRadio] ?: d.endlessRadio,
            skipSilence = this[Keys.skipSilence] ?: d.skipSilence,
            normalizeLoudness = this[Keys.normalizeLoudness] ?: d.normalizeLoudness,
            sleepTimerDefaultMinutes = this[Keys.sleepDefaultMinutes] ?: d.sleepTimerDefaultMinutes,
            onboardingDone = this[Keys.onboardingDone] ?: d.onboardingDone,
        ).normalized()
    }
}
