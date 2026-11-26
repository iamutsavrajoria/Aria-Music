// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.settings

import app.aria.domain.AppSettings
import app.aria.domain.testing.FakeSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    // runTest must share this dispatcher, or backgroundScope collectors would run on a second scheduler.
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private fun env(scope: CoroutineScope, initial: AppSettings = AppSettings()): Pair<FakeSettingsRepository, SettingsViewModel> {
        val repo = FakeSettingsRepository(initial); val vm = SettingsViewModel(repo); scope.launch { vm.state.collect { } }; return repo to vm
    }

    @Test fun showsStoredValues() = runTest(dispatcher) {
        val (_, vm) = env(backgroundScope, AppSettings(historyThresholdMs = 60_000, skipSilence = true))
        val c = vm.state.value as SettingsUiState.Content; assertEquals(60_000L, c.settings.historyThresholdMs); assertTrue(c.settings.skipSilence)
    }

    @Test fun isLoadingUntilTheRepositoryEmits() = runTest(dispatcher) {
        val gate = MutableSharedFlow<AppSettings>()
        val repo = object : app.aria.domain.SettingsRepository {
            override val settings: Flow<AppSettings> = gate
            override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
        }
        val vm = SettingsViewModel(repo); backgroundScope.launch { vm.state.collect { } }
        assertEquals(SettingsUiState.Loading, vm.state.value)
        gate.emit(AppSettings()); assertTrue(vm.state.value is SettingsUiState.Content)
    }

    @Test fun everyChangeIsPersistedAndTheScreenFollows() = runTest(dispatcher) {
        val (repo, vm) = env(backgroundScope)
        vm.setHistorySeconds(45); vm.setEndlessRadio(false); vm.setSkipSilence(true); vm.setNormalizeLoudness(false); vm.setSleepDefaultMinutes(30)
        val expected = AppSettings(historyThresholdMs = 45_000, endlessRadio = false, skipSilence = true, normalizeLoudness = false, sleepTimerDefaultMinutes = 30)
        assertEquals(expected, repo.current); assertEquals(SettingsUiState.Content(expected), vm.state.value)
    }

    @Test fun historyIsClampedToTenThroughOneTwentySeconds() = runTest(dispatcher) {
        val (repo, vm) = env(backgroundScope)
        vm.setHistorySeconds(1); assertEquals(10_000L, repo.current.historyThresholdMs)
        vm.setHistorySeconds(9_999); assertEquals(120_000L, repo.current.historyThresholdMs)
    }

    @Test fun eachSettingChangesOnlyItself() = runTest(dispatcher) {
        val (repo, vm) = env(backgroundScope, AppSettings(historyThresholdMs = 50_000, endlessRadio = true, skipSilence = true, normalizeLoudness = true, sleepTimerDefaultMinutes = 45))
        val before = repo.current
        vm.setEndlessRadio(false); assertEquals(before.copy(endlessRadio = false), repo.current)
        vm.setEndlessRadio(true); vm.setSkipSilence(false); assertEquals(before.copy(skipSilence = false), repo.current)
        vm.setSkipSilence(true); vm.setNormalizeLoudness(false); assertEquals(before.copy(normalizeLoudness = false), repo.current)
    }

    @Test fun onboardingFlagIsNotTouchedBySettingsChanges() = runTest(dispatcher) {
        val (repo, vm) = env(backgroundScope, AppSettings(onboardingDone = true))
        vm.setEndlessRadio(false); vm.setHistorySeconds(20); vm.setSleepDefaultMinutes(5)
        assertTrue(repo.current.onboardingDone)
    }

    @Test fun theChoicesAreWhatTheScreenOffers() {
        assertEquals(listOf(5, 10, 15, 30, 45, 60, 90), SettingsChoices.SLEEP_MINUTES)
        assertEquals(10, SettingsChoices.HISTORY_MIN_S); assertEquals(120, SettingsChoices.HISTORY_MAX_S); assertEquals(5, SettingsChoices.HISTORY_STEP_S)
    }
}
