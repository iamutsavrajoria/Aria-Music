### Task 8: Media3 output, service and minimal app wiring (replaces the skeleton player)

**Files:**
- Create: `playback/src/main/kotlin/app/aria/playback/{Media3PlayerOutput,EngineForwardingPlayer}.kt`, `app/src/main/kotlin/app/aria/music/ConnectivityNetworkMonitor.kt`
- Modify: `playback/src/main/kotlin/app/aria/playback/AriaPlaybackService.kt`, `app/src/main/kotlin/app/aria/music/{AriaApp,MainActivity}.kt`, `app/src/main/kotlin/app/aria/music/di/AppModule.kt`, `app/src/main/AndroidManifest.xml`, `playback/build.gradle.kts` (only if CI cannot see `ResolvingDataSource`)
- Delete: `PlayerController.kt`, `ResumeStore.kt` (both `ResumeStore` and `InMemoryResumeStore`), `PlayerControllerTest.kt`, the `PlaybackHost` object, `app/.../SkeletonScreen.kt`, `app/.../SharedPrefsResumeStore.kt`

**Interfaces:**
- Consumes: Task 7 policies, `DefaultPlaybackEngine.attach/detach/streamFor`, `PlayerOutput`, `PlayerListener`, `PlaybackEngine`, `QueueStore` (Task 6), and the spike's Media3 signatures.
- Produces: `Media3PlayerOutput`, `EngineForwardingPlayer`, `AriaPlaybackService.start(context)`, the engine as a Hilt singleton bound as both `DefaultPlaybackEngine` and `PlaybackEngine`, `AriaApp : PlaybackEngineProvider`, and a minimal real `MainActivity` (search, tap a result to play, now-playing line with play/pause). No `:app` shim: the activity uses the real engine from this task on, and Task 12 replaces its content with `PlayerScreen`.

**Acceptance Criteria:**
- ExoPlayer is built with audio focus handling, becoming-noisy pause and network wake mode; stream URLs are resolved through `ResolvingDataSource` using `engine.streamFor`; `Media3PlayerOutput.resolve()` throws `PermanentStreamException("unsupported stream scheme")` for a URL `StreamUrlPolicy` rejects, so the engine skips the track.
- Notification, Bluetooth and session play, pause, next, previous and seeks go to the engine (`EngineForwardingPlayer`); the session callback uses `TrustPolicy`.
- `PlaybackHost`, `PlayerController`, the 500 ms delay and every `TODO(sub-project 2)` in `:playback` are gone.
- `AriaApp` implements `PlaybackEngineProvider` with the injected engine and calls `engine.restore()` once at startup on the main thread.
- `ACCESS_NETWORK_STATE` is declared. Both flavors assemble; `checkModuleGraph`, lint and the license check pass.
- `RadioSource` is bound to an empty stand-in in `:app` (it returns no tracks, so endless radio, which defaults to on, finds nothing until then); Task 12 replaces it with `YouTubeRadioSource`.

- [ ] **Step 1: Build check (no new JVM test: the adapter is exercised by the emulator tests in Task 13).** Push the compile of the code below.

- [ ] **Step 2: Implement the Media3 adapter and service.** (Delete the old `:playback` files first.)

```kotlin
// Media3PlayerOutput.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.aria.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** ExoPlayer behind the engine's PlayerOutput. Items are lightweight (`aria://track/<id>`); URLs resolve at load time. */
@UnstableApi
class Media3PlayerOutput(
    context: Context,
    private val engine: DefaultPlaybackEngine,
    private val allowLocalStreams: Boolean = false,
) : PlayerOutput, Player.Listener {
    override var listener: PlayerListener? = null

    val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                ResolvingDataSource.Factory(DefaultDataSource.Factory(context)) { spec -> resolve(spec) },
            ),
        )
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    init { exo.addListener(this) }

    /** Runs on ExoPlayer's loader thread, never on the main thread. */
    private fun resolve(spec: DataSpec): DataSpec {
        if (spec.uri.scheme != SCHEME) return spec
        val raw = spec.uri.lastPathSegment ?: throw IOException("Bad track URI")
        val id = try { TrackId.parse(raw) } catch (e: IllegalArgumentException) { throw PermanentStreamException(e) }
        val stream = try {
            runBlocking { engine.streamFor(id) }.getOrThrow()
        } catch (e: CancellationException) { throw e
        } catch (e: StreamUnavailableException) { throw PermanentStreamException(e)
        } catch (e: Exception) { throw IOException(e) }
        if (!StreamUrlPolicy.isAllowed(stream.url, allowLocalStreams)) throw PermanentStreamException(IllegalStateException("unsupported stream scheme"))
        return spec.withUri(stream.url.toUri())
    }

    private fun item(q: QueueItem): MediaItem = MediaItem.Builder()
        .setMediaId(q.queueId.toString())
        .setUri(Uri.Builder().scheme(SCHEME).authority("track").appendPath(q.track.id.raw).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(q.track.title).setArtist(q.track.artists.joinToString())
                .setArtworkUri(q.track.thumbnailUrl?.toUri())
                .setIsPlayable(true).setIsBrowsable(false)
                .build(),
        ).build()

    // ---- PlayerOutput ---------------------------------------------------------------------------------
    override fun setPlaylist(items: List<QueueItem>, startIndex: Int, startPositionMs: Long, playWhenReady: Boolean) {
        exo.setMediaItems(items.map(::item), startIndex, startPositionMs)
        exo.playWhenReady = playWhenReady
        exo.prepare()
    }
    override fun insert(index: Int, items: List<QueueItem>) { exo.addMediaItems(index, items.map(::item)) }
    override fun remove(index: Int) { exo.removeMediaItem(index) }
    override fun move(from: Int, to: Int) { exo.moveMediaItem(from, to) }
    override fun reorder(items: List<QueueItem>, currentIndex: Int) {
        val cur = exo.currentMediaItemIndex
        if (cur + 1 < exo.mediaItemCount) exo.removeMediaItems(cur + 1, exo.mediaItemCount)
        if (cur > 0) exo.removeMediaItems(0, cur)
        exo.addMediaItems(0, items.subList(0, currentIndex).map(::item))
        exo.addMediaItems(exo.mediaItemCount, items.subList(currentIndex + 1, items.size).map(::item))
    }
    override fun clear() { exo.clearMediaItems() }
    override fun seekTo(index: Int, positionMs: Long) { exo.seekTo(index, positionMs) }
    override fun setPlayWhenReady(play: Boolean) { exo.playWhenReady = play }
    override fun retry(positionMs: Long) { exo.seekTo(exo.currentMediaItemIndex, positionMs); exo.prepare() }
    override fun setRepeatOne(on: Boolean) { exo.repeatMode = if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    override fun setSkipSilence(on: Boolean) { exo.skipSilenceEnabled = on }
    override fun setVolume(volume: Float) { exo.volume = volume }
    override fun positionMs(): Long = exo.currentPosition
    override fun durationMs(): Long = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L

    fun release() { exo.removeListener(this); exo.release() }

    // ---- Player.Listener ------------------------------------------------------------------------------
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            listener?.onItemTransition(exo.currentMediaItemIndex)
        }
    }
    override fun onPlaybackStateChanged(state: Int) {
        when (state) {
            Player.STATE_BUFFERING -> listener?.onBufferingChanged(true)
            Player.STATE_READY -> listener?.onBufferingChanged(false)
            Player.STATE_ENDED -> listener?.onEnded()
        }
    }
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { listener?.onPlayWhenReadyChanged(playWhenReady) }
    override fun onPlayerError(error: PlaybackException) { listener?.onError(PlayerErrorMapper.map(error)) }

    private companion object { const val SCHEME = "aria" }
}
```

```kotlin
// EngineForwardingPlayer.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.aria.domain.PlaybackEngine

/** Routes every transport command from the session, notification and headset buttons to the engine. */
@UnstableApi
class EngineForwardingPlayer(player: Player, private val engine: PlaybackEngine) : ForwardingPlayer(player) {
    override fun play() = engine.play()
    override fun pause() = engine.pause()
    override fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) engine.play() else engine.pause()
    override fun seekToNext() = engine.next()
    override fun seekToNextMediaItem() = engine.next()
    override fun seekToPrevious() = engine.previous()
    override fun seekToPreviousMediaItem() = engine.previous()
    override fun seekTo(positionMs: Long) = engine.seekTo(positionMs)
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex == currentMediaItemIndex) engine.seekTo(positionMs) else engine.skipTo(mediaItemIndex)
    }
}
```

```kotlin
// AriaPlaybackService.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.aria.domain.PlaybackStatus

@UnstableApi
class AriaPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var output: Media3PlayerOutput? = null
    private lateinit var engine: DefaultPlaybackEngine

    override fun onCreate() {
        super.onCreate()
        engine = (application as PlaybackEngineProvider).playbackEngine
        val allowLocal = (application as PlaybackEngineProvider).allowLocalStreams
        val out = Media3PlayerOutput(this, engine, allowLocal).also { output = it }
        session = MediaSession.Builder(this, EngineForwardingPlayer(out.exo, engine))
            .setCallback(TrustedCallback(packageName))
            .build()
        engine.attach(out)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val status = engine.state.value.status
        if (status != PlaybackStatus.Playing && status != PlaybackStatus.Buffering) stopSelf()
    }

    override fun onDestroy() {
        output?.let { engine.detach(it) }
        session?.release(); session = null
        output?.release(); output = null
        super.onDestroy()
    }

    private class TrustedCallback(private val own: String) : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            if (TrustPolicy.isAllowed(controller.packageName, own, controller.isTrusted, session.isMediaNotificationController(controller))) {
                MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            } else MediaSession.ConnectionResult.reject()
    }

    companion object {
        /** startService, not startForegroundService: Media3 promotes the service itself once playback starts. */
        fun start(context: Context) { context.startService(Intent(context, AriaPlaybackService::class.java)) }
    }
}
```

- [ ] **Step 3: Wire `:app`.** Delete `SkeletonScreen.kt` and `SharedPrefsResumeStore.kt`. Manifest: add `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`.

```kotlin
// ConnectivityNetworkMonitor.kt
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.aria.domain.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ConnectivityNetworkMonitor(context: Context) : NetworkMonitor {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val state = MutableStateFlow(currentlyOnline())
    override val online: StateFlow<Boolean> = state

    private fun currentlyOnline(): Boolean =
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { state.value = true }
            override fun onLost(network: Network) { state.value = false }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        })
    }
}
```

`AppModule.kt`: delete the `resume` provider and the `ResumeStore`/`SharedPrefsResumeStore` imports; keep `database`, `playEvents`, `search` and the current `streams()`; add:

```kotlin
    @Provides @Singleton fun network(@ApplicationContext c: Context): NetworkMonitor = ConnectivityNetworkMonitor(c)
    @Provides @Singleton fun queueStore(db: AriaDatabase): QueueStore = QueueStoreImpl(db.queueDao())
    /** Stand-in until Task 12 binds YouTubeRadioSource. */
    @Provides @Singleton fun radio(): RadioSource = object : RadioSource {
        override suspend fun related(seed: Track, limit: Int): Result<List<Track>> = Result.success(emptyList())
    }

    @Provides @Singleton
    fun engine(
        streams: StreamSource, store: QueueStore, radio: RadioSource, history: PlayEventRepository, network: NetworkMonitor,
    ): DefaultPlaybackEngine = DefaultPlaybackEngine(streams, store, radio, history, network, Dispatchers.Main.immediate)

    @Provides @Singleton fun playbackEngine(e: DefaultPlaybackEngine): PlaybackEngine = e
```

`AriaApp.kt`: add `import javax.inject.Inject`, `kotlinx.coroutines.MainScope`, `kotlinx.coroutines.launch`; class header `class AriaApp : Application(), PlaybackEngineProvider`; members:

```kotlin
    @Inject lateinit var engine: DefaultPlaybackEngine
    override val playbackEngine: DefaultPlaybackEngine get() = engine
```
and at the end of `onCreate()`: `MainScope().launch { engine.restore() }`.

`MainActivity.kt` (minimal, real):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aria.domain.*
import app.aria.playback.AriaPlaybackService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var search: SearchSource
    @Inject lateinit var engine: PlaybackEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by engine.state.collectAsState()
                val scope = rememberCoroutineScope()
                var query by remember { mutableStateOf("") }
                var results by remember { mutableStateOf<List<Track>>(emptyList()) }
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(BuildConfig.BRAND_NAME, style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(query, { query = it }, label = { Text("Search") }, modifier = Modifier.weight(1f))
                        Button(onClick = { scope.launch { search.searchTracks(query).onSuccess { results = it } } }) { Text("Search") }
                    }
                    Text(state.current?.track?.title ?: "Nothing playing")
                    Button(
                        enabled = state.current != null,
                        onClick = { if (state.status == PlaybackStatus.Playing) engine.pause() else engine.play() },
                    ) { Text("Play/Pause") }
                    LazyColumn {
                        itemsIndexed(results) { i, t ->
                            TextButton(onClick = { AriaPlaybackService.start(this@MainActivity); engine.playNow(results, i) }) {
                                Text("${t.title} - ${t.artists.joinToString()}", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4:** Push; fix compile and lint findings from CI. The Media3 names used here (`isTrusted`, `isMediaNotificationController`, `ConnectionResult.reject`, the `ForwardingPlayer` overrides, the `ResolvingDataSource.Factory` lambda form) were verified against Media3 1.10.1 in the spike (item 3); `@OptIn(UnstableApi::class)` is needed for `isTrusted`, `isMediaNotificationController`, `AcceptedResultBuilder`, `ResolvingDataSource`, `DataSpec`, `DefaultMediaSourceFactory(DataSource.Factory)` and `ForwardingPlayer`, and not for `onConnect`, `reject()` or the `MediaMetadata`/`ExoPlayer` builders. Expected: PASS, both flavors assemble, all existing jobs green.
- [ ] **Step 5: Commit** `feat(playback): Media3 output and service driven by the engine; remove skeleton host`.

---

