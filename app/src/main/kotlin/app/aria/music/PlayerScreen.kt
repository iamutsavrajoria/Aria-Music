// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aria.designsystem.GlassSurface
import app.aria.domain.PlaybackEngine
import app.aria.domain.PlaybackEvent
import app.aria.domain.PlaybackStatus
import app.aria.domain.RepeatMode
import app.aria.domain.SearchSource
import app.aria.domain.Track
import kotlinx.coroutines.launch

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun PlayerScreen(search: SearchSource, engine: PlaybackEngine, startService: () -> Unit) {
    val state by engine.state.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var drag by remember { mutableStateOf<Float?>(null) }
    // Android 13+: ask at first play. Playback continues if the user says no (no notification then).
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(engine) {
        engine.events.collect { e ->
            message = when (e) {
                is PlaybackEvent.Skipped -> "Skipped ${e.track.title}: ${e.reason}"
                is PlaybackEvent.Stopped -> e.reason
            }
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
                    Text(
                        current?.let { "${it.track.title} - ${it.track.artists.joinToString()}" } ?: "Nothing playing",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        when (val s = state.status) {
                            PlaybackStatus.Idle -> "Idle"
                            PlaybackStatus.Buffering -> "Buffering"
                            PlaybackStatus.Playing -> "Playing"
                            PlaybackStatus.Paused -> "Paused"
                            PlaybackStatus.WaitingForNetwork -> "Waiting for network"
                            is PlaybackStatus.Error -> s.message
                        },
                    )
                    val duration = state.durationMs.coerceAtLeast(1L)
                    Slider(
                        value = drag ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f),
                        onValueChange = { drag = it },
                        onValueChangeFinished = {
                            drag?.let { engine.seekTo((it * duration).toLong()) }
                            drag = null
                        },
                        enabled = current != null,
                    )
                    Text("${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { engine.previous() }, enabled = current != null) { Text("Prev") }
                        Button(
                            onClick = {
                                if (state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Buffering || state.status == PlaybackStatus.WaitingForNetwork) engine.pause() else engine.play()
                            },
                            enabled = current != null,
                        ) { Text("Play/Pause") }
                        Button(onClick = { engine.next() }, enabled = current != null) { Text("Next") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = state.shuffle, onClick = { engine.setShuffle(!state.shuffle) }, label = { Text("Shuffle") })
                        FilterChip(
                            selected = state.repeat != RepeatMode.OFF,
                            onClick = {
                                engine.setRepeat(
                                    when (state.repeat) {
                                        RepeatMode.OFF -> RepeatMode.ALL
                                        RepeatMode.ALL -> RepeatMode.ONE
                                        RepeatMode.ONE -> RepeatMode.OFF
                                    },
                                )
                            },
                            label = { Text("Repeat ${state.repeat.name.lowercase()}") },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.sleepTimer?.endsAtEpochMs != null,
                            onClick = { engine.setSleepTimer(15 * 60_000L) },
                            label = { Text("Sleep 15 min") },
                        )
                        FilterChip(
                            selected = state.sleepTimer?.endOfTrack == true,
                            onClick = { engine.setSleepAtEndOfTrack(true) },
                            label = { Text("End of track") },
                        )
                        FilterChip(selected = false, onClick = { engine.setSleepTimer(null) }, label = { Text("Sleep off") })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = state.skipSilence, onCheckedChange = { engine.setSkipSilence(it) })
                        Text(" Skip silence")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = state.normalizeLoudness, onCheckedChange = { engine.setNormalizeLoudness(it) })
                        Text(" Loudness normalization")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = state.endlessRadio, onCheckedChange = { engine.setEndlessRadio(it) })
                        Text(" Endless radio")
                    }
                }
            }
            item { Text("Results", style = MaterialTheme.typography.titleSmall) }
            itemsIndexed(results) { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = { begin(); engine.playNow(results, i) }) {
                        Text("${t.title} - ${t.artists.joinToString()}", maxLines = 1)
                    }
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
