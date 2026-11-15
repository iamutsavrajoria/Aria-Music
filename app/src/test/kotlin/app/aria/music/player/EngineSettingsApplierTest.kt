// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.player

import app.aria.domain.AppSettings
import app.aria.domain.testing.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EngineSettingsApplierTest {
    @Test fun storedValuesReachTheEngineWithoutAnyScreen() = runTest {
        val engine = testEngine(StandardTestDispatcher(testScheduler))
        val repo = FakeSettingsRepository(AppSettings(historyThresholdMs = 45_000, endlessRadio = false, skipSilence = true, normalizeLoudness = false))
        EngineSettingsApplier(engine, repo, backgroundScope).start(); runCurrent()
        val s = engine.state.value
        assertEquals(45_000L, s.historyThresholdMs); assertFalse(s.endlessRadio); assertTrue(s.skipSilence); assertFalse(s.normalizeLoudness)
        engine.release()
    }

    @Test fun laterChangesFollow() = runTest {
        val engine = testEngine(StandardTestDispatcher(testScheduler)); val repo = FakeSettingsRepository()
        EngineSettingsApplier(engine, repo, backgroundScope).start(); runCurrent()
        assertEquals(30_000L, engine.state.value.historyThresholdMs); assertTrue(engine.state.value.endlessRadio)
        repo.update { it.copy(historyThresholdMs = 90_000, endlessRadio = false) }; runCurrent()
        assertEquals(90_000L, engine.state.value.historyThresholdMs); assertFalse(engine.state.value.endlessRadio)
        engine.release()
    }

    @Test fun outOfRangeStoredValuesAreClampedByTheEngine() = runTest {
        val engine = testEngine(StandardTestDispatcher(testScheduler))
        EngineSettingsApplier(engine, FakeSettingsRepository(), backgroundScope).apply(AppSettings(historyThresholdMs = 5)); runCurrent()
        assertEquals(10_000L, engine.state.value.historyThresholdMs)
        engine.release()
    }
}
