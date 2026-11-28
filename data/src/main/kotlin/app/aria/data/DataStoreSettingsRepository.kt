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
import androidx.datastore.preferences.core.stringPreferencesKey
import app.aria.domain.AppSettings
import app.aria.domain.NowPlayingSkin
import app.aria.domain.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.File
import java.io.IOException

/**
 * A corrupt settings file is replaced by defaults; settings are never worth a crash.
 *
 * Contract (DataStore allows only one live instance per file, and a second one throws on first use):
 * - create exactly one instance per [file] and keep it for the whole app lifetime (an app-level singleton);
 * - [scope] must be app-lifetime too, in the app `SupervisorJob() + Dispatchers.IO`; never cancel it while the app runs;
 * - [file] must live under `filesDir/datastore/`, the only directory the backup rules exclude from cloud backup and device transfer.
 */
fun createSettingsDataStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { file },
    )

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private companion object { const val RETRY_DELAY_MS = 1_000L }

    private object Keys {
        val historyThresholdMs = longPreferencesKey("history_threshold_ms")
        val endlessRadio = booleanPreferencesKey("endless_radio")
        val skipSilence = booleanPreferencesKey("skip_silence")
        val normalizeLoudness = booleanPreferencesKey("normalize_loudness")
        val sleepDefaultMinutes = intPreferencesKey("sleep_default_minutes")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val nowPlayingSkin = stringPreferencesKey("now_playing_skin")
    }

    /** A read failure emits defaults and then retries, so the flow never completes because of an IOException. */
    override val settings: Flow<AppSettings> = store.data
        .retryWhen { e, _ ->
            if (e is IOException) { emit(emptyPreferences()); delay(RETRY_DELAY_MS); true } else false
        }
        .map { it.toSettings() }
        .distinctUntilChanged()

    /** A storage failure (IOException) drops the write; settings are never worth a crash. */
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        try { write(transform) } catch (_: IOException) { }
    }

    private suspend fun write(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val next = transform(p.toSettings()).normalized()
            p[Keys.historyThresholdMs] = next.historyThresholdMs
            p[Keys.endlessRadio] = next.endlessRadio
            p[Keys.skipSilence] = next.skipSilence
            p[Keys.normalizeLoudness] = next.normalizeLoudness
            p[Keys.sleepDefaultMinutes] = next.sleepTimerDefaultMinutes
            p[Keys.onboardingDone] = next.onboardingDone
            p[Keys.nowPlayingSkin] = next.nowPlayingSkin.name
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
            // An unknown stored value (a newer or damaged file) falls back to the default skin.
            nowPlayingSkin = NowPlayingSkin.entries.firstOrNull { it.name == this[Keys.nowPlayingSkin] } ?: d.nowPlayingSkin,
        ).normalized()
    }
}
