// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.nowplaying

import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModelTest {
    @BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private class Env(scope: kotlinx.coroutines.CoroutineScope, libraryOverride: LibraryRepository? = null) {
        val engine = FakePlaybackEngine(); val fakeLibrary = FakeLibraryRepository(); val library: LibraryRepository = libraryOverride ?: fakeLibrary
        val settings = FakeSettingsRepository(AppSettings(sleepTimerDefaultMinutes = 20))
        val controller = PlayerController(engine, ServiceLauncher { }, scope)
        val vm = NowPlayingViewModel(controller, library, settings)
        init { scope.launch { vm.state.collect { } } }
        fun play(status: PlaybackStatus = PlaybackStatus.Playing, durationMs: Long = 180_000) {
            engine.mutableState.value = PlaybackState(queue = Samples.tracks(3).mapIndexed { i, t -> QueueItem(i + 1L, t) }, currentIndex = 1, status = status, durationMs = durationMs, shuffle = true, repeat = RepeatMode.ALL)
        }
    }

    /** A library whose writes fail, like a database error. */
    private fun failingLibrary(failure: Throwable): LibraryRepository = object : LibraryRepository by FakeLibraryRepository() {
        override suspend fun setLiked(track: Track, liked: Boolean) { throw failure }
    }

    private fun TestScope.collectMessages(c: PlayerController): List<PlayerMessage> {
        val out = mutableListOf<PlayerMessage>(); backgroundScope.launch { c.messages.collect { out += it } }; runCurrent(); return out
    }

    @Test fun emptyWithoutAQueue() = runTest { assertEquals(NowPlayingUiState.Empty, Env(backgroundScope).vm.state.value) }

    @Test fun contentMirrorsThePlayerAndSettings() = runTest {
        val e = Env(backgroundScope); e.play(); runCurrent()
        val c = e.vm.state.value as NowPlayingUiState.Content
        assertEquals("Song 2", c.track.title); assertEquals(PlayerStatus.PLAYING, c.status); assertTrue(c.shuffle); assertEquals(RepeatMode.ALL, c.repeat)
        assertEquals(3, c.queueSize); assertEquals(1, c.queueIndex); assertEquals(20, c.sleepDefaultMinutes); assertFalse(c.liked)
    }

    @Test fun likeFollowsTheLibraryAndTheActionWritesIt() = runTest {
        val e = Env(backgroundScope); e.play(); runCurrent()
        val a = e.vm.actions({}, {})
        a.onLike(); runCurrent()
        assertTrue((e.vm.state.value as NowPlayingUiState.Content).liked)
        // Liking also puts the song in the library (ADR-0003).
        assertEquals(listOf(Samples.track(2)), e.fakeLibrary.songs("", LibrarySort.TITLE).first().map { it.track })
        a.onLike(); runCurrent()
        assertFalse((e.vm.state.value as NowPlayingUiState.Content).liked)
    }

    @Test fun likeFollowsTheCurrentTrackWhenItChanges() = runTest {
        val e = Env(backgroundScope); e.play(); runCurrent()
        e.vm.actions({}, {}).onLike(); runCurrent()
        assertTrue((e.vm.state.value as NowPlayingUiState.Content).liked)
        e.engine.mutableState.value = e.engine.mutableState.value.copy(currentIndex = 2); runCurrent()
        assertFalse((e.vm.state.value as NowPlayingUiState.Content).liked)
    }

    @Test fun likeWithNothingPlayingDoesNothing() = runTest {
        val e = Env(backgroundScope)
        e.vm.actions({}, {}).onLike(); runCurrent()
        assertTrue(e.fakeLibrary.songs("", LibrarySort.TITLE).first().isEmpty())
    }

    @Test fun actionsDelegateToTheController() = runTest {
        val e = Env(backgroundScope); e.play(); runCurrent()
        val a = e.vm.actions({}, {})
        a.onToggle(); a.onNext(); a.onPrevious(); a.onSeek(5_000); a.onShuffle(false); a.onRepeat(); a.onSleepMinutes(20); a.onSleepEndOfTrack(); a.onSleepOff()
        assertEquals(
            listOf("pause", "next", "previous", "seekTo:5000", "setShuffle:false", "setRepeat:ONE", "setSleepTimer:1200000", "setSleepAtEndOfTrack:true", "setSleepTimer:null"),
            e.engine.calls,
        )
    }

    @Test fun backAndQueueCallbacksPassThrough() = runTest {
        val e = Env(backgroundScope); var back = 0; var queue = 0
        val a = e.vm.actions({ back++ }, { queue++ }); a.onBack(); a.onOpenQueue()
        assertEquals(1, back); assertEquals(1, queue)
    }

    @Test fun errorAndOfflineStatusesSurface() = runTest {
        val e = Env(backgroundScope); e.play(PlaybackStatus.WaitingForNetwork); runCurrent()
        assertEquals(PlayerStatus.WAITING_FOR_NETWORK, (e.vm.state.value as NowPlayingUiState.Content).status)
        e.play(PlaybackStatus.Error("x")); runCurrent()
        assertEquals(PlayerStatus.ERROR, (e.vm.state.value as NowPlayingUiState.Content).status)
    }

    @Test fun theMainButtonPausesWhileWaitingForTheNetworkAndPlaysAfterAnError() = runTest {
        val e = Env(backgroundScope); e.play(PlaybackStatus.WaitingForNetwork); runCurrent()
        assertTrue((e.vm.state.value as NowPlayingUiState.Content).isPlaying)
        e.vm.actions({}, {}).onToggle(); assertEquals("pause", e.engine.calls.last())
        e.play(PlaybackStatus.Error("x")); runCurrent()
        assertFalse((e.vm.state.value as NowPlayingUiState.Content).isPlaying)
        e.vm.actions({}, {}).onToggle(); assertEquals("play", e.engine.calls.last())
    }

    @Test fun slowStateDoesNotReemitOnPositionTicks() = runTest {
        val e = Env(backgroundScope); e.play(); runCurrent()
        val seen = mutableListOf<NowPlayingUiState>(); backgroundScope.launch { e.vm.state.collect { seen += it } }; runCurrent()
        val before = seen.size
        e.engine.mutableState.value = e.engine.mutableState.value.copy(positionMs = 1_000); runCurrent()
        e.engine.mutableState.value = e.engine.mutableState.value.copy(positionMs = 2_000); runCurrent()
        assertEquals(before, seen.size); assertEquals(2_000L, e.vm.positionMs.value)
    }

    @Test fun aFailedLikeIsReportedAndDoesNotCrash() = runTest {
        val e = Env(backgroundScope, failingLibrary(java.io.IOException("disk full"))); e.play(); runCurrent()
        val msgs = collectMessages(e.controller)
        e.vm.actions({}, {}).onLike(); runCurrent()
        assertEquals(listOf<PlayerMessage>(PlayerMessage.LikeFailed), msgs)
        assertFalse((e.vm.state.value as NowPlayingUiState.Content).liked)
    }

    @Test fun aCancelledLikeIsNotReportedAsAFailure() = runTest {
        val e = Env(backgroundScope, failingLibrary(kotlinx.coroutines.CancellationException("cancelled"))); e.play(); runCurrent()
        val msgs = collectMessages(e.controller)
        e.vm.actions({}, {}).onLike(); runCurrent()
        assertTrue(msgs.isEmpty())
    }

    @Test fun unlikingKeepsTheSongInTheLibrary() = runTest {
        val e = Env(backgroundScope); e.play(); runCurrent()
        val a = e.vm.actions({}, {}); val id = Samples.track(2).id
        a.onLike(); runCurrent(); a.onLike(); runCurrent()
        assertFalse((e.vm.state.value as NowPlayingUiState.Content).liked)
        assertTrue(e.library.isInLibrary(id).first())
        assertEquals(listOf(Samples.track(2)), e.fakeLibrary.songs("", LibrarySort.TITLE).first().map { it.track })
    }

    @Test fun durationFallsBackToTheTrackLengthWhileTheEngineDoesNotKnowIt() = runTest {
        val e = Env(backgroundScope); e.play(durationMs = 0); runCurrent()
        e.engine.mutableState.value = e.engine.mutableState.value.copy(queue = listOf(QueueItem(1L, Samples.track(1).copy(durationMs = 90_000L))), currentIndex = 0); runCurrent()
        assertEquals(90_000L, (e.vm.state.value as NowPlayingUiState.Content).durationMs)
        e.engine.mutableState.value = e.engine.mutableState.value.copy(durationMs = 80_000L); runCurrent()
        assertEquals(80_000L, (e.vm.state.value as NowPlayingUiState.Content).durationMs)
        e.engine.mutableState.value = e.engine.mutableState.value.copy(durationMs = 0L, queue = listOf(QueueItem(1L, Samples.track(1).copy(durationMs = null)))); runCurrent()
        assertEquals(0L, (e.vm.state.value as NowPlayingUiState.Content).durationMs)
    }

    @Test fun skinFollowsTheSetting() = runTest {
        val e = Env(backgroundScope); backgroundScope.launch { e.vm.skin.collect { } }
        assertEquals(NowPlayingSkin.MATERIAL, e.vm.skin.value)
        e.settings.update { it.copy(nowPlayingSkin = NowPlayingSkin.APPLE) }
        assertEquals(NowPlayingSkin.APPLE, e.vm.skin.value)
    }
}
