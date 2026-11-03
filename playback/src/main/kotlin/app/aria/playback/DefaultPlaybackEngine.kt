// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random

/**
 * All playback behaviour, pure Kotlin. Every method must be called on [dispatcher] (main in production).
 * The engine owns queue order and content; the player owns position and play/pause (spec section 2).
 */
class DefaultPlaybackEngine(
    private val streams: StreamSource,
    private val store: QueueStore,
    private val radio: RadioSource,
    private val history: PlayEventRepository,
    private val network: NetworkMonitor,
    dispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    random: Random = Random.Default,
    /** Where stream resolution runs (CPU-heavy cipher work stays off the main thread). Tests pass the test dispatcher. */
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PlaybackEngine, PlayerListener {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    internal val model = QueueModel(random)
    private var output: PlayerOutput? = null

    private var repeatMode = RepeatMode.OFF
    private var wantPlay = false
    private var buffering = false
    private var waiting = false
    private var errorMsg: String? = null
    private var positionMs = 0L
    private var sleepTimer: SleepTimer? = null
    private var skipSilence = false
    private var normalizeLoudness = true
    private var endlessRadio = true

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    private var tickJob: Job? = null

    private fun status(): PlaybackStatus = when {
        errorMsg != null -> PlaybackStatus.Error(errorMsg!!)
        model.items.isEmpty() -> PlaybackStatus.Idle
        waiting -> PlaybackStatus.WaitingForNetwork
        !wantPlay -> PlaybackStatus.Paused
        buffering -> PlaybackStatus.Buffering
        else -> PlaybackStatus.Playing
    }

    private fun publish() {
        _state.value = PlaybackState(
            queue = model.items, currentIndex = model.index, status = status(), positionMs = positionMs,
            durationMs = output?.durationMs()?.takeIf { it > 0 } ?: model.current?.track?.durationMs ?: 0L,
            shuffle = model.shuffle, repeat = repeatMode, sleepTimer = sleepTimer,
            skipSilence = skipSilence, normalizeLoudness = normalizeLoudness, endlessRadio = endlessRadio,
        )
    }

    // ---- hooks extended by later tasks -------------------------------------------------------------
    /** The current track changed (command, transition or skip). */
    private fun onCurrentChanged() { positionMs = 0L; publish() }

    /** Queue items, order, shuffle or repeat changed. */
    private fun onStructureChanged() { publish() }

    // ---- lifecycle -----------------------------------------------------------------------------------
    fun attach(out: PlayerOutput) {
        output = out; out.listener = this
        out.setRepeatOne(repeatMode == RepeatMode.ONE); out.setSkipSilence(skipSilence)
        if (model.items.isNotEmpty()) out.setPlaylist(model.items, model.index, positionMs, wantPlay)
        updateTicker(); publish()
    }

    fun detach(out: PlayerOutput) {
        if (output !== out) return
        positionMs = out.positionMs(); out.listener = null; output = null
        wantPlay = false; buffering = false; updateTicker(); publish()
    }

    fun release() { scope.cancel() }

    private fun updateTicker() {
        val shouldRun = wantPlay && !waiting && output != null
        if (shouldRun && tickJob?.isActive != true) tickJob = scope.launch { while (true) { delay(TICK_MS); onTick() } }
        else if (!shouldRun) { tickJob?.cancel(); tickJob = null }
    }

    private fun onTick() {
        val out = output ?: return
        positionMs = out.positionMs()
        publish()
    }

    // ---- queue commands -------------------------------------------------------------------------------
    override fun playNow(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        model.replace(tracks, startIndex)
        errorMsg = null; wantPlay = true; buffering = true
        output?.setPlaylist(model.items, model.index, 0L, true)
        updateTicker(); onCurrentChanged(); onStructureChanged()
    }

    override fun playNext(track: Track) {
        if (model.items.isEmpty()) return playNow(listOf(track))
        val pos = model.insertNext(track)
        output?.insert(pos, listOf(model.items[pos])); onStructureChanged()
    }

    override fun addToEnd(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (model.items.isEmpty()) return playNow(tracks)
        val start = model.items.size
        model.append(tracks)
        output?.insert(start, model.items.subList(start, model.items.size)); onStructureChanged()
    }

    override fun removeAt(index: Int) {
        if (index !in model.items.indices) return
        val wasCurrent = index == model.index
        model.removeAt(index); output?.remove(index)
        if (model.items.isEmpty()) { wantPlay = false; buffering = false; updateTicker(); onCurrentChanged() }
        else if (wasCurrent) goTo(model.index, wantPlay)
        onStructureChanged()
    }

    override fun move(from: Int, to: Int) {
        if (from !in model.items.indices || to !in model.items.indices || from == to) return
        model.move(from, to); output?.move(from, to); onStructureChanged()
    }

    override fun clear() {
        model.clear(); output?.clear(); wantPlay = false; buffering = false; errorMsg = null
        updateTicker(); onCurrentChanged(); onStructureChanged()
    }

    override fun skipTo(index: Int) { if (index in model.items.indices) goTo(index, true) }

    private fun goTo(index: Int, play: Boolean) {
        model.jumpTo(index)
        errorMsg = null; wantPlay = play; buffering = play
        output?.seekTo(index, 0L); output?.setPlayWhenReady(play)
        updateTicker(); onCurrentChanged()
    }

    // ---- transport ------------------------------------------------------------------------------------
    override fun play() {
        if (model.items.isEmpty()) return
        if (errorMsg != null) { goTo(model.index, true); return }
        wantPlay = true; output?.setPlayWhenReady(true); updateTicker(); publish()
    }

    override fun pause() {
        wantPlay = false; output?.setPlayWhenReady(false); updateTicker(); publish()
    }

    private fun nextIndexOrNull(): Int? = when {
        model.items.isEmpty() -> null
        model.index < model.items.lastIndex -> model.index + 1
        repeatMode == RepeatMode.ALL -> 0
        else -> null
    }

    override fun next() {
        val target = nextIndexOrNull()
        if (target != null) goTo(target, true) else stopAtEnd()
    }

    override fun previous() {
        if (model.items.isEmpty()) return
        val played = output?.positionMs() ?: positionMs
        when {
            played > PREVIOUS_RESTART_MS -> seekTo(0L)
            model.index > 0 -> goTo(model.index - 1, true)
            repeatMode == RepeatMode.ALL -> goTo(model.items.lastIndex, true)
            else -> seekTo(0L)
        }
    }

    override fun seekTo(positionMs: Long) {
        this.positionMs = positionMs; output?.seekTo(model.index, positionMs); publish()
    }

    private fun stopAtEnd() {
        wantPlay = false; buffering = false; positionMs = 0L
        output?.setPlayWhenReady(false); output?.seekTo(model.index, 0L)
        updateTicker(); publish()
    }

    // ---- modes ----------------------------------------------------------------------------------------
    override fun setShuffle(on: Boolean) {
        if (on == model.shuffle) return
        model.setShuffle(on); output?.reorder(model.items, model.index); onStructureChanged()
    }

    override fun setRepeat(mode: RepeatMode) {
        repeatMode = mode; output?.setRepeatOne(mode == RepeatMode.ONE); onStructureChanged()
    }

    override fun setSleepTimer(durationMs: Long?) { /* Task 5 */ }
    override fun setSleepAtEndOfTrack(on: Boolean) { /* Task 5 */ }
    override fun setSkipSilence(on: Boolean) { /* Task 5 */ }
    override fun setNormalizeLoudness(on: Boolean) { /* Task 5 */ }
    override fun setEndlessRadio(on: Boolean) { /* Task 5 */ }

    // ---- PlayerListener -------------------------------------------------------------------------------
    override fun onItemTransition(index: Int) {
        if (index !in model.items.indices) return
        model.jumpTo(index); onCurrentChanged()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
        if (wantPlay == playWhenReady) return
        wantPlay = playWhenReady; updateTicker(); publish()
    }

    override fun onBufferingChanged(buffering: Boolean) { this.buffering = buffering; publish() }

    override fun onEnded() {
        if (repeatMode == RepeatMode.ALL && model.items.isNotEmpty()) goTo(0, true) else stopAtEnd()
    }

    override fun onError(error: PlayerError) { errorMsg = "Playback error"; wantPlay = false; updateTicker(); publish() }

    companion object {
        const val TICK_MS = 500L
        const val PREVIOUS_RESTART_MS = 3_000L
    }
}
