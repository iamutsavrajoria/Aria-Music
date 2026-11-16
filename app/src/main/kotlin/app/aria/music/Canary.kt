// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.size.Dimension
import coil3.size.Size
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import okio.Path.Companion.toOkioPath
import java.io.File
import javax.inject.Inject

// Throwaway compile canary (stress-test S1). Deleted in Task 12 Step 4; never referenced by production code.

@Serializable
object CanaryRoute

@Serializable
data class CanaryDetailRoute(val id: String)

@HiltViewModel
class CanaryViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(0)
    val state: StateFlow<Int> = _state
}

internal class CanaryInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data !is String) {
            return ErrorResult(image = null, request = chain.request, throwable = IllegalArgumentException("no"))
        }
        val w = (chain.size.width as? Dimension.Pixels)?.px ?: 1
        val h = (chain.size.height as? Dimension.Pixels)?.px ?: 1
        return chain.withSize(Size(w, h)).proceed()
    }
}

fun canaryImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
    .components { add(CanaryInterceptor()); add(OkHttpNetworkFetcherFactory()) }
    .diskCache { DiskCache.Builder().directory(File(context.cacheDir, "canary").toOkioPath()).maxSizeBytes(1024L * 1024).build() }
    .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
    .build()

fun canaryStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { file },
    )

private val canaryKey = booleanPreferencesKey("canary")

fun canaryRead(store: DataStore<Preferences>): Flow<Boolean?> = store.data.map { it[canaryKey] }

suspend fun canaryWrite(store: DataStore<Preferences>) {
    store.edit { it[canaryKey] = true }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanaryScreen(viewModel: CanaryViewModel = hiltViewModel(), nav: NavHostController = rememberNavController()) {
    val count by viewModel.state.collectAsStateWithLifecycle()
    var refreshing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val loader = remember { canaryImageLoader(context) }
    val entry by nav.currentBackStackEntryAsState()
    val onTop = entry?.destination?.hierarchy?.any { it.parent != null } == true
    LaunchedEffect(Unit) { nav.navigate(CanaryRoute) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
    NavHost(nav, startDestination = CanaryRoute, modifier = Modifier.padding(0.dp)) {
        composable<CanaryRoute> {
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = false }) {
                Box(Modifier.testTag("canary").semantics { contentDescription = "x" }) { // ui-string-ok: throwaway compile canary, removed in Task 12
                    Text("$count $onTop")
                    AsyncImage(model = "https://i.ytimg.com/x.jpg", contentDescription = null, imageLoader = loader, modifier = Modifier, contentScale = ContentScale.Crop)
                }
            }
        }
        composable<CanaryDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<CanaryDetailRoute>()
            Text(route.id)
        }
    }
}
