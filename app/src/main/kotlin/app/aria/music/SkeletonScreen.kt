// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.aria.designsystem.GlassSurface
import app.aria.domain.PlayEventRepository
import app.aria.domain.SearchSource
import app.aria.domain.StreamSource
import app.aria.domain.Track
import app.aria.playback.AriaPlaybackService
import app.aria.playback.PlaybackHost
import app.aria.playback.PlayerController
import app.aria.playback.ResumeStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Walking skeleton: search -> resolve -> play -> record, crossing every module boundary. */
@Composable
fun SkeletonScreen(
    search: SearchSource,
    streams: StreamSource,
    events: PlayEventRepository,
    resume: ResumeStore,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf<Track?>(null) }
    var plays by remember { mutableIntStateOf(0) }
    // One controller per playback session: it remembers the start time that Stop needs.
    var controller by remember { mutableStateOf<PlayerController?>(null) }
    // Android 13+: ask once at first play. Playback continues if the user says no (no notification then).
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    GlassSurface(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(BuildConfig.BRAND_NAME)
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") })
            Button(onClick = {
                scope.launch {
                    search.searchTracks(query)
                        .onSuccess { results = it; status = "${it.size} results" }
                        .onFailure { status = "Search failed: ${it.javaClass.simpleName}" }
                }
            }) { Text("Search") }
            Text(status)
            playing?.let { t ->
                Text("Playing: ${t.title} (plays recorded: $plays)")
                Button(onClick = {
                    scope.launch {
                        controller?.stopAndRecord(t)
                        plays = events.playCount(t.id)
                        playing = null
                        controller = null
                    }
                }) { Text("Stop") }
            }
            LazyColumn {
                items(results) { t ->
                    Button(onClick = {
                        scope.launch {
                            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            // startService, not startForegroundService: Media3 promotes the service itself once playback
                            // starts, whereas startForegroundService crashes (ForegroundServiceDidNotStartInTimeException)
                            // if playback never starts, for example when stream resolution fails.
                            context.startService(Intent(context, AriaPlaybackService::class.java))
                            delay(500) // skeleton shortcut: service creates PlaybackHost.player on start
                            val player = PlaybackHost.player ?: run { status = "Player not ready"; return@launch }
                            val c = PlayerController(streams, events, player, resume, System::currentTimeMillis)
                            c.start(t)
                                .onSuccess { controller = c; playing = t; plays = events.playCount(t.id) }
                                .onFailure { status = "Cannot play: ${it.message}" }
                        }
                    }) { Text("${t.title} - ${t.artists.joinToString()}") }
                }
            }
        }
    }
}
