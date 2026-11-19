// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.player

import app.aria.domain.*
import app.aria.domain.testing.FakePlaybackEngine
import app.aria.domain.testing.Samples
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerControllerTest {
    private class Launcher : ServiceLauncher { var starts = 0; override fun start() { starts++ } }
    private fun TestScope.controller(engine: PlaybackEngine, launcher: ServiceLauncher = Launcher()) = PlayerController(engine, launcher, backgroundScope)
    private fun TestScope.collectMessages(c: PlayerController): List<PlayerMessage> {
        val out = mutableListOf<PlayerMessage>(); backgroundScope.launch { c.messages.collect { out += it } }; runCurrent(); return out
    }

    @Test fun commandWithNoServiceStartsItAndSetsTheQueue_fake() = runTest {
        val engine = FakePlaybackEngine(); val launcher = Launcher(); val c = controller(engine, launcher)
        val tracks = Samples.tracks(3)
        c.play(tracks, 2); runCurrent()
        assertEquals(1, launcher.starts); assertEquals(listOf(tracks to 2), engine.playNowCalls)
    }

    @Test fun commandWithNoServiceStartsItAndSetsTheQueue_realEngine() = runTest {
        val engine = testEngine(StandardTestDispatcher(testScheduler)); val launcher = Launcher(); val c = controller(engine, launcher)
        val tracks = Samples.tracks(3)
        c.play(tracks, 1); runCurrent()
        assertEquals(1, launcher.starts)
        assertEquals(tracks, engine.state.value.queue.map { it.track }); assertEquals(1, engine.state.value.currentIndex)
        assertEquals(3, c.ui.value.queue.size); assertTrue(c.ui.value.hasQueue)
        engine.release()
    }

    @Test fun uiStateIgnoresPositionWhilePositionFlowFollows() = runTest {
        val engine = FakePlaybackEngine(); val c = controller(engine)
        val seen = mutableListOf<PlayerUiState>(); backgroundScope.launch { c.ui.collect { seen += it } }; runCurrent()
        val before = seen.size
        engine.mutableState.value = engine.mutableState.value.copy(positionMs = 1_000); runCurrent()
        engine.mutableState.value = engine.mutableState.value.copy(positionMs = 2_000); runCurrent()
        assertEquals(before, seen.size); assertEquals(2_000L, c.positionMs.value)
        engine.mutableState.value = engine.mutableState.value.copy(status = PlaybackStatus.Playing); runCurrent()
        assertEquals(before + 1, seen.size); assertEquals(PlayerStatus.PLAYING, c.ui.value.status); assertTrue(c.ui.value.isPlaying)
    }

    @Test fun longListsQueueAWindowStartingAtTheTappedItem() = runTest {
        val engine = FakePlaybackEngine(); val c = controller(engine); val msgs = collectMessages(c)
        val tracks = Samples.tracks(1_200)
        c.play(tracks, 100); runCurrent()
        val (queued, start) = engine.playNowCalls.single()
        assertEquals(500, queued.size); assertEquals(tracks[100], queued.first()); assertEquals(0, start)
        assertEquals(listOf<PlayerMessage>(PlayerMessage.QueueWindowed(500, 1_200)), msgs)
        c.play(tracks, 900); runCurrent()
        assertEquals(300, engine.playNowCalls.last().first.size)
        assertEquals(PlayerMessage.QueueWindowed(300, 1_200), msgs.last())
    }

    @Test fun shortListsQueueEverythingSilently() = runTest {
        val engine = FakePlaybackEngine(); val c = controller(engine); val msgs = collectMessages(c)
        c.play(Samples.tracks(500), 250); runCurrent()
        assertEquals(500, engine.playNowCalls.single().first.size); assertEquals(250, engine.playNowCalls.single().second); assertTrue(msgs.isEmpty())
    }

    @Test fun addingBeyondTheCapReportsWhatWasDropped() = runTest {
        val engine = FakePlaybackEngine(); val c = controller(engine); val msgs = collectMessages(c)
        c.play(Samples.tracks(450), 0); runCurrent()
        c.addToQueue(Samples.tracks(100, from = 1_000)); runCurrent()
        assertEquals(PlayerMessage.QueueFull(50), msgs.single()); assertTrue("addToEnd" in engine.calls)
    }

    @Test fun engineEventsBecomeMessages() = runTest {
        val engine = FakePlaybackEngine(); val c = controller(engine); val msgs = collectMessages(c)
        val t = Samples.track(1, "Title")
        engine.mutableEvents.emit(PlaybackEvent.Skipped(t, "unavailable")); engine.mutableEvents.emit(PlaybackEvent.Skipped(t, "network error"))
        engine.mutableEvents.emit(PlaybackEvent.Skipped(t, "weird")); engine.mutableEvents.emit(PlaybackEvent.Stopped("Stopped after 3 tracks failed in a row")); runCurrent()
        assertEquals(
            listOf(PlayerMessage.TrackSkipped("Title", SkipReason.UNAVAILABLE), PlayerMessage.TrackSkipped("Title", SkipReason.NETWORK),
                PlayerMessage.TrackSkipped("Title", SkipReason.OTHER), PlayerMessage.TooManyFailures),
            msgs,
        )
    }

    @Test fun toggleWhileWaitingForTheNetworkPausesAndAnErrorRetriesWithPlay() = runTest {
        val engine = FakePlaybackEngine(); val c = controller(engine)
        val queue = listOf(QueueItem(1, Samples.track(1)))
        engine.mutableState.value = PlaybackState(queue = queue, currentIndex = 0, status = PlaybackStatus.WaitingForNetwork); runCurrent()
        assertTrue(c.ui.value.isPlaying)
        c.togglePlayPause(); assertEquals("pause", engine.calls.last())
        engine.mutableState.value = PlaybackState(queue = queue, currentIndex = 0, status = PlaybackStatus.Error("x")); runCurrent()
        assertFalse(c.ui.value.isPlaying)
        c.togglePlayPause(); assertEquals("play", engine.calls.last())
    }

    @Test fun transportCommandsDelegate() = runTest {
        val engine = FakePlaybackEngine(); val c = controller(engine)
        engine.mutableState.value = PlaybackState(queue = listOf(QueueItem(1, Samples.track(1))), currentIndex = 0, status = PlaybackStatus.Playing); runCurrent()
        c.togglePlayPause(); assertEquals("pause", engine.calls.last())
        engine.mutableState.value = engine.mutableState.value.copy(status = PlaybackStatus.Paused); runCurrent()
        c.togglePlayPause(); assertEquals("play", engine.calls.last())
        c.cycleRepeat(); assertEquals("setRepeat:ALL", engine.calls.last())
        engine.mutableState.value = engine.mutableState.value.copy(repeat = RepeatMode.ALL); runCurrent()
        c.cycleRepeat(); assertEquals("setRepeat:ONE", engine.calls.last())
        c.setSleepTimer(15); assertEquals("setSleepTimer:900000", engine.calls.last())
        c.setSleepTimer(null); assertEquals("setSleepTimer:null", engine.calls.last())
    }

    private val throwingLauncher = ServiceLauncher { throw IllegalStateException("background start not allowed") }

    @Test fun throwingLauncherDoesNotStopTheQueueOrThrow_fake() = runTest {
        val engine = FakePlaybackEngine(); val c = controller(engine, throwingLauncher)
        val tracks = Samples.tracks(3)
        c.play(tracks, 1); c.playNext(tracks[0]); c.addToQueue(tracks); runCurrent()
        assertEquals(tracks to 1, engine.playNowCalls.first())
        assertTrue("playNext" in engine.calls)
    }

    @Test fun throwingLauncherDoesNotStopTheQueueOrThrow_realEngine() = runTest {
        val engine = testEngine(StandardTestDispatcher(testScheduler)); val c = controller(engine, throwingLauncher)
        val tracks = Samples.tracks(3)
        c.play(tracks, 2); runCurrent()
        assertEquals(tracks, engine.state.value.queue.map { it.track }); assertEquals(2, engine.state.value.currentIndex)
        engine.release()
    }

    @Test fun realEngineUsesItsOwnGuardedServiceStartPath() = runTest {
        val engine = testEngine(StandardTestDispatcher(testScheduler)); val launcher = Launcher(); val c = controller(engine, launcher)
        var engineStarts = 0; engine.serviceStarter = { engineStarts++ }
        val tracks = Samples.tracks(2)
        c.play(tracks, 0); runCurrent()
        assertTrue(engineStarts >= 1); assertEquals(tracks, engine.state.value.queue.map { it.track })
        engine.serviceStarter = { throw IllegalStateException("background start not allowed") }
        c.addToQueue(Samples.tracks(1, from = 50)); runCurrent()
        assertEquals(3, engine.state.value.queue.size)
        engine.release()
    }

    @Test fun addToQueueWithEmptyQueueTakesThePlayPath() = runTest {
        val engine = FakePlaybackEngine(); val launcher = Launcher(); val c = controller(engine, launcher)
        val tracks = Samples.tracks(2)
        c.addToQueue(tracks); runCurrent()
        assertEquals(listOf(tracks to 0), engine.playNowCalls); assertTrue("addToEnd" !in engine.calls); assertEquals(1, launcher.starts)
    }

    @Test fun startIndexIsClamped() = runTest {
        val engine = FakePlaybackEngine(); val c = controller(engine)
        val tracks = Samples.tracks(3)
        c.play(tracks, 99); runCurrent(); c.play(tracks, -5); runCurrent()
        assertEquals(listOf(tracks to 2, tracks to 0), engine.playNowCalls)
    }

    @Test fun playNextDelegatesAndStartsService() = runTest {
        val engine = FakePlaybackEngine(); val launcher = Launcher(); val c = controller(engine, launcher)
        c.playNext(Samples.track(7)); runCurrent()
        assertEquals(1, launcher.starts); assertEquals("playNext", engine.calls.last())
    }
}
