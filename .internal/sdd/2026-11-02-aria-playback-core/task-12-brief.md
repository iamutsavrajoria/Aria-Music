### Task 12: Player screen and final app wiring

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/PlayerScreen.kt`
- Modify: `app/src/main/kotlin/app/aria/music/MainActivity.kt` (the minimal Task 8 screen becomes `PlayerScreen`), `app/src/main/kotlin/app/aria/music/di/AppModule.kt` (real `RadioSource`, `PoTokenSource`, `ExtractorSession`, `StreamSource`)

**Interfaces:**
- Consumes: everything from Tasks 1 to 11; the engine, network monitor, queue store, `AriaApp` provider and manifest permission already exist from Task 8.
- Produces: the finished player UI.

**Acceptance Criteria:**
- Both flavors assemble; `checkModuleGraph`, lint (including `NewApi`) and the license check pass.
- The screen offers: search; tap a result to play it with the result list as queue; "Next" and "Add" per result; now-playing block with seek slider, previous/play-pause/next, shuffle and repeat toggles; queue list with remove and move up/down; sleep timer (15 min, end of track, off); switches for skip silence, loudness normalization and endless radio; skip and stop events shown as a message.
- Starting playback calls `AriaPlaybackService.start(context)` first and requests `POST_NOTIFICATIONS` on API 33+ (playback continues if declined).
- `AppModule` provides the real `YouTubeRadioSource`, `WebViewPoTokenSource`, `ExtractorSession` and `YouTubeStreamSource` (replacing Task 8's empty radio stand-in and Task 10's temporary `streams()` wiring).
- No `TODO(sub-project 2)` remains anywhere (`grep -rn "TODO(sub-project 2)" .` over source returns nothing).

- [ ] **Step 1: Build check (no new JVM test: the logic lives in the engine).** Push the first compile of the changes below; the emulator test in Task 13 covers the wiring.

- [ ] **Step 2: Implement.** `AppModule.kt`: replace the `radio()` stand-in and the temporary `streams()` with:

```kotlin
    @Provides @Singleton fun radio(): RadioSource = YouTubeRadioSource()
    @Provides @Singleton fun poTokens(@ApplicationContext c: Context): PoTokenSource = WebViewPoTokenSource(c)
    @Provides @Singleton fun extractorSession(p: PoTokenSource): ExtractorSession = ExtractorSession.createDefault(p)
    @Provides @Singleton fun streams(s: ExtractorSession): StreamSource = YouTubeStreamSource.create(s)
```

`MainActivity.kt` becomes:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var search: SearchSource
    @Inject lateinit var engine: PlaybackEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { PlayerScreen(search, engine) { AriaPlaybackService.start(this) } } }
    }
}
```

`PlayerScreen.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aria.designsystem.GlassSurface
import app.aria.domain.*
import kotlinx.coroutines.launch

private fun time(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

@Composable
fun PlayerScreen(search: SearchSource, engine: PlaybackEngine, startService: () -> Unit) {
    val state by engine.state.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var drag by remember { mutableStateOf<Float?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(engine) {
        engine.events.collect { e ->
            message = when (e) { is PlaybackEvent.Skipped -> "Skipped ${e.track.title}: ${e.reason}"; is PlaybackEvent.Stopped -> e.reason }
        }
    }
    fun begin() {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        startService()
    }

    GlassSurface(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(BuildConfig.BRAND_NAME, style = MaterialTheme.typography.titleLarge) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") }, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        scope.launch {
                            search.searchTracks(query)
                                .onSuccess { results = it; message = "${it.size} results" }
                                .onFailure { message = "Search failed: ${it.javaClass.simpleName}" }
                        }
                    }) { Text("Search") }
                }
            }
            if (message.isNotEmpty()) item { Text(message) }
            item {
                val current = state.current
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(current?.let { "${it.track.title} - ${it.track.artists.joinToString()}" } ?: "Nothing playing", style = MaterialTheme.typography.titleMedium)
                    Text(when (val s = state.status) {
                        PlaybackStatus.Idle -> "Idle"; PlaybackStatus.Buffering -> "Buffering"; PlaybackStatus.Playing -> "Playing"
                        PlaybackStatus.Paused -> "Paused"; PlaybackStatus.WaitingForNetwork -> "Waiting for network"; is PlaybackStatus.Error -> s.message
                    })
                    val duration = state.durationMs.coerceAtLeast(1L)
                    Slider(
                        value = drag ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f),
                        onValueChange = { drag = it },
                        onValueChangeFinished = { drag?.let { engine.seekTo((it * duration).toLong()) }; drag = null },
                        enabled = current != null,
                    )
                    Text("${time(state.positionMs)} / ${time(state.durationMs)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = engine::previous, enabled = current != null) { Text("Prev") }
                        Button(onClick = { if (state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Buffering) engine.pause() else engine.play() }, enabled = current != null) { Text("Play/Pause") }
                        Button(onClick = engine::next, enabled = current != null) { Text("Next") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = state.shuffle, onClick = { engine.setShuffle(!state.shuffle) }, label = { Text("Shuffle") })
                        FilterChip(
                            selected = state.repeat != RepeatMode.OFF,
                            onClick = { engine.setRepeat(when (state.repeat) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF }) },
                            label = { Text("Repeat ${state.repeat.name.lowercase()}") },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.sleepTimer?.endsAtEpochMs != null, onClick = { engine.setSleepTimer(15 * 60_000L) }, label = { Text("Sleep 15 min") })
                        FilterChip(selected = state.sleepTimer?.endOfTrack == true, onClick = { engine.setSleepAtEndOfTrack(true) }, label = { Text("End of track") })
                        FilterChip(selected = false, onClick = { engine.setSleepTimer(null) }, label = { Text("Sleep off") })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(state.skipSilence, engine::setSkipSilence); Text(" Skip silence") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(state.normalizeLoudness, engine::setNormalizeLoudness); Text(" Loudness normalization") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(state.endlessRadio, engine::setEndlessRadio); Text(" Endless radio") }
                }
            }
            item { Text("Results", style = MaterialTheme.typography.titleSmall) }
            itemsIndexed(results) { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = { begin(); engine.playNow(results, i) }) { Text("${t.title} - ${t.artists.joinToString()}", maxLines = 1) }
                    TextButton(onClick = { begin(); engine.playNext(t) }) { Text("Next") }
                    TextButton(onClick = { begin(); engine.addToEnd(listOf(t)) }) { Text("Add") }
                }
            }
            item { Text("Queue (${state.queue.size})", style = MaterialTheme.typography.titleSmall) }
            itemsIndexed(state.queue, key = { _, item -> item.queueId }) { i, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (i == state.currentIndex) "> " else "") + item.track.title, modifier = Modifier.weight(1f), maxLines = 1)
                    TextButton(onClick = { engine.skipTo(i) }) { Text("Play") }
                    TextButton(onClick = { engine.move(i, i - 1) }, enabled = i > 0) { Text("Up") }
                    TextButton(onClick = { engine.move(i, i + 1) }, enabled = i < state.queue.lastIndex) { Text("Down") }
                    TextButton(onClick = { engine.removeAt(i) }) { Text("Remove") }
                }
            }
        }
    }
}
```

- [ ] **Step 3:** Push; fix compile and lint findings from CI. Expected: both flavors assemble, all existing jobs green.
- [ ] **Step 4: Commit** `feat(app): engine wiring, restore at startup and player screen`.

---

