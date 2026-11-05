// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aria.domain.PlaybackEngine
import app.aria.domain.PlaybackStatus
import app.aria.domain.SearchSource
import app.aria.domain.Track
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
                // Android 13+: ask at first play. Playback continues if the user says no (no notification then).
                val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
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
                            TextButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    AriaPlaybackService.start(this@MainActivity)
                                    engine.playNow(results, i)
                                },
                            ) {
                                Text("${t.title} - ${t.artists.joinToString()}", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
