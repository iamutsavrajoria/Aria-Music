// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.domain.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun open(file: java.io.File, scope: CoroutineScope) = DataStoreSettingsRepository(createSettingsDataStore(file, scope))

    @Test fun defaultsWhenNothingIsStored() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        assertEquals(AppSettings(), open(tmp.newFile("a.preferences_pb").also { it.delete() }, scope).settings.first())
        scope.cancel()
    }

    @Test fun updatesPersistAcrossInstances() = runBlocking {
        val file = tmp.newFile("b.preferences_pb").also { it.delete() }
        val s1 = CoroutineScope(Job() + Dispatchers.IO)
        open(file, s1).update { it.copy(historyThresholdMs = 45_000, endlessRadio = false, skipSilence = true, normalizeLoudness = false, sleepTimerDefaultMinutes = 30, onboardingDone = true) }
        s1.cancel(); s1.coroutineContext[Job]!!.join()
        val s2 = CoroutineScope(Job() + Dispatchers.IO)
        assertEquals(
            AppSettings(45_000, endlessRadio = false, skipSilence = true, normalizeLoudness = false, sleepTimerDefaultMinutes = 30, onboardingDone = true),
            open(file, s2).settings.first(),
        )
        s2.cancel()
    }

    @Test fun updatesAreClamped() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        val repo = open(tmp.newFile("c.preferences_pb").also { it.delete() }, scope)
        repo.update { it.copy(historyThresholdMs = 1) }
        assertEquals(10_000L, repo.settings.first().historyThresholdMs)
        repo.update { it.copy(historyThresholdMs = 99_999_999) }
        assertEquals(120_000L, repo.settings.first().historyThresholdMs)
        scope.cancel()
    }

    @Test fun corruptFileGivesDefaults() = runBlocking {
        val file = tmp.newFile("d.preferences_pb").also { it.writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        assertEquals(AppSettings(), open(file, scope).settings.first())
        scope.cancel()
    }
}
