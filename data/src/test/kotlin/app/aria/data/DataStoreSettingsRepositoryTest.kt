// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.domain.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun open(file: java.io.File, scope: CoroutineScope) = DataStoreSettingsRepository(createSettingsDataStore(file, scope))

    @Test fun defaultsWhenNothingIsStored() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try { assertEquals(AppSettings(), open(tmp.newFile("a.preferences_pb").also { it.delete() }, scope).settings.first()) } finally { scope.cancel() }
    }

    @Test fun updatesPersistAcrossInstances() = runBlocking {
        val file = tmp.newFile("b.preferences_pb").also { it.delete() }
        val s1 = CoroutineScope(Job() + Dispatchers.IO)
        try {
            open(file, s1).update { it.copy(historyThresholdMs = 45_000, endlessRadio = false, skipSilence = true, normalizeLoudness = false, sleepTimerDefaultMinutes = 30, onboardingDone = true) }
        } finally { s1.cancel() }
        s1.coroutineContext[Job]!!.join()
        val s2 = CoroutineScope(Job() + Dispatchers.IO)
        try {
            assertEquals(
                AppSettings(45_000, endlessRadio = false, skipSilence = true, normalizeLoudness = false, sleepTimerDefaultMinutes = 30, onboardingDone = true),
                open(file, s2).settings.first(),
            )
        } finally { s2.cancel() }
    }

    @Test fun updatesAreClamped() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val repo = open(tmp.newFile("c.preferences_pb").also { it.delete() }, scope)
            repo.update { it.copy(historyThresholdMs = 1) }
            assertEquals(10_000L, repo.settings.first().historyThresholdMs)
            repo.update { it.copy(historyThresholdMs = 99_999_999) }
            assertEquals(120_000L, repo.settings.first().historyThresholdMs)
        } finally { scope.cancel() }
    }

    @Test fun corruptFileGivesDefaults() = runBlocking {
        val file = tmp.newFile("d.preferences_pb").also { it.writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try { assertEquals(AppSettings(), open(file, scope).settings.first()) } finally { scope.cancel() }
    }

    @Test fun updateAfterCorruptionReplacesTheFile() = runBlocking {
        val file = tmp.newFile("e.preferences_pb").also { it.writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val repo = open(file, scope)
            repo.update { it.copy(endlessRadio = false, historyThresholdMs = 60_000) }
            val s = repo.settings.first()
            assertEquals(false, s.endlessRadio); assertEquals(60_000L, s.historyThresholdMs)
        } finally { scope.cancel() }
    }

    @Test fun outOfRangeStoredValuesAreClampedOnRead() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        try {
            val store = createSettingsDataStore(tmp.newFile("f.preferences_pb").also { it.delete() }, scope)
            store.edit { it[longPreferencesKey("history_threshold_ms")] = 1L }
            assertEquals(10_000L, DataStoreSettingsRepository(store).settings.first().historyThresholdMs)
            store.edit { it[longPreferencesKey("history_threshold_ms")] = 99_999_999L }
            assertEquals(120_000L, DataStoreSettingsRepository(store).settings.first().historyThresholdMs)
        } finally { scope.cancel() }
    }

    @Test fun settingsFlowKeepsCollectingAfterAReadIOException() = runBlocking {
        val attempts = AtomicInteger()
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                if (attempts.getAndIncrement() == 0) throw IOException("disk")
                emit(preferencesOf(longPreferencesKey("history_threshold_ms") to 60_000L))
                awaitCancellation()
            }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw IOException("disk")
        }
        val repo = DataStoreSettingsRepository(store)
        val seen = withTimeout(10_000) { repo.settings.take(2).toList() }
        assertEquals(AppSettings(), seen[0]); assertEquals(60_000L, seen[1].historyThresholdMs)
        repo.update { it.copy(endlessRadio = false) } // a failed write must not throw
    }
}
