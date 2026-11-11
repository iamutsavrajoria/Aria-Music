<!-- SPDX-License-Identifier: GPL-3.0-only -->
# UI shell: dependency and API spike (Task 0)

Date: 2026-11-11. Author: implementer of Task 0 of `2026-11-08-aria-ui-shell`.

## How this was read

- Maven registries (`dl.google.com/android/maven2`, `repo1.maven.org`) answer 403 through the sandbox proxy, so `maven-metadata.xml` was not reachable.
- Coil and kotlinx.serialization versions were chosen from the newest stable git tags (`git ls-remote --tags`); AndroidX versions are the plan candidates (androidx tags are not exposed per artifact).
- Every version was then **resolved by CI**: a wrong coordinate fails with "Could not resolve". CI run 42963032834 resolved all of them (the only errors were Kotlin compile errors, see Plan changes).
- Coil signatures below were read from the `coil-kt/coil` source at tag `3.6.3` (shallow clone). Navigation, DataStore, Hilt-navigation, lifecycle-compose and Material3 `PullToRefreshBox` were **not read; the CI compile of `Canary.kt` is the arbiter** (it compiled in run 42964145048, see below).
- `Canary.kt` (throwaway, deleted in Task 12 Step 4) exercises every new API in the shape Tasks 11-22 use it.

## Coordinates

| Coordinate | Version | Source |
|---|---|---|
| `androidx.datastore:datastore-preferences` | 1.2.0 | resolved by CI run 42963032834 |
| `io.coil-kt.coil3:coil-compose` | 3.6.3 | git tag 3.6.3 (newest stable); resolved by CI run 42963032834 |
| `io.coil-kt.coil3:coil-network-okhttp` | 3.6.3 | same |
| `androidx.navigation:navigation-compose` | 2.9.6 (type-safe routes, `composable<T>`, `toRoute<T>()`) | resolved by CI run 42963032834 |
| `androidx.hilt:hilt-navigation-compose` | 1.3.0 | resolved by CI run 42963032834 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.10.0 | resolved by CI run 42963032834 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.10.0 | resolved by CI run 42963032834 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | git tag v1.11.0 (v1.12.0 exists; 1.11.0 kept for Kotlin 2.4.10 safety); resolved by CI run 42963032834 |

Coil 3.6.3 itself is built with Kotlin 2.4.10 and Compose 1.12.0, the versions this project uses.

## Signatures read (Coil 3.6.3)

```kotlin
// coil3.intercept.Interceptor
fun interface Interceptor {
    suspend fun intercept(chain: Chain): ImageResult
    interface Chain {
        val request: ImageRequest
        val size: Size
        fun withRequest(request: ImageRequest): Chain
        fun withSize(size: Size): Chain
        suspend fun proceed(): ImageResult
    }
}
// coil3.request.ErrorResult
class ErrorResult(override val image: Image?, override val request: ImageRequest, val throwable: Throwable) : ImageResult
// coil3.disk.DiskCache.Builder
fun directory(directory: okio.Path); fun fileSystem(fs: FileSystem); fun maxSizeBytes(size: Long); fun cleanupCoroutineContext(c: CoroutineContext); fun build(): DiskCache
// coil3.ImageLoader.Builder
inline fun components(builder: ComponentRegistry.Builder.() -> Unit); fun components(components: ComponentRegistry)
fun diskCache(diskCache: DiskCache?); fun diskCache(initializer: () -> DiskCache?)
fun memoryCache(memoryCache: MemoryCache?); fun memoryCache(initializer: () -> MemoryCache?)
// coil3.ComponentRegistry.Builder
fun add(interceptor: Interceptor); fun add(factory: Decoder.Factory)
// coil3.network.okhttp
fun OkHttpNetworkFetcherFactory(); fun OkHttpNetworkFetcherFactory(callFactory: Call.Factory); fun OkHttpNetworkFetcherFactory(callFactory: () -> Call.Factory = ::OkHttpClient, cacheStrategy: () -> CacheStrategy = ...)
// coil3.compose.AsyncImage (note: imageLoader is REQUIRED, third parameter)
fun AsyncImage(model: Any?, contentDescription: String?, imageLoader: ImageLoader, modifier: Modifier = Modifier,
    placeholder: Painter? = null, error: Painter? = null, fallback: Painter? = error, onLoading: ..., onSuccess: ..., onError: ...,
    alignment: Alignment = Center, contentScale: ContentScale = Fit, alpha: Float = DefaultAlpha, colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DefaultFilterQuality, clipToBounds: Boolean = true)
// ImageRequest.Builder.size(Int) / size(Int, Int) / size(Size) / size(SizeResolver)
```

`okio` is an `api` dependency of `coil-core`, so `okio.Path.Companion.toOkioPath` is available without another dependency.

## Findings already read (planner, 2026-11-08; re-check line numbers)

| Need | Real API in `:innertube` | Notes for the mapping tasks |
|---|---|---|
| Home | `YouTube.home(continuation: String? = null, params: String? = null): Result<HomePage>`; `HomePage(chips: List<Chip>?, sections: List<Section>, continuation: String?)`; `Section(title: String, label: String?, thumbnail: String?, endpoint: BrowseEndpoint?, items: List<YTItem>)` | Items can be `SongItem`, `AlbumItem`, `PlaylistItem`, `ArtistItem`, `PodcastItem`, `EpisodeItem`. Podcasts and episodes are not mapped in this sub-project. |
| Album | `YouTube.album(browseId: String, withSongs: Boolean = true): Result<AlbumPage>`; `AlbumPage(album: AlbumItem, songs: List<SongItem>, otherVersions: List<AlbumItem>)` | `AlbumPage.getSong` and the private-release branch use `!!` (album id, thumbnail): one odd row can throw inside `runCatching` and fail the whole album. The adapter must tolerate `Result.failure`; tests cover mapping null-safety only. |
| Artist | `YouTube.artist(browseId: String): Result<ArtistPage>`; `ArtistPage(artist: ArtistItem, sections: List<ArtistSection(title, items: List<YTItem>, moreEndpoint)>, description: String?, subscriberCountText: String?, monthlyListenerCount: String?, descriptionRuns, isSubscribed)` | |
| Playlist | `YouTube.playlist(playlistId: String): Result<PlaylistPage>`; `PlaylistPage(playlist: PlaylistItem, songs: List<SongItem>, songsContinuation: String?, continuation: String?)`; `YouTube.playlistContinuation(continuation: String): Result<PlaylistContinuationPage(songs, continuation)>` | `PlaylistItem.songCountText` is display text ("12 songs"), parse digits or null. |
| Search | `YouTube.searchSummary(query: String, incognito: Boolean = false): Result<SearchSummaryPage(summaries: List<SearchSummary(title, items: List<YTItem>)>)>`; `YouTube.searchSuggestions(query: String): Result<SearchSuggestions(queries: List<String>, recommendedItems: List<YTItem>)>`; `YouTube.search(query, SearchFilter)` | Group by item type, not by the localized `title`. |
| Library | `YouTube.library(browseId, tabIndex)` uses `setLogin = true` | **Not used**: the library is local-only (ADR-0003). |
| Mapping hazard | `SongItem.toTrack()` (existing, `adapter/Mapping.kt`) calls `TrackId.youtube(id)`, which `require`s an 11-character ID | Browse mapping must use a null-safe variant (Task 6). |
| Singleton | `object YouTube` owns the `InnerTube` client; no constructor injection | Adapters take function parameters with defaults (pattern of `YouTubeRadioSource`) so tests inject lambdas. |

(Not re-verified against `:innertube` in Task 0; Task 6 reads the code before mapping.)

## Compile canary results

- CI run 42963032834 (commit 669a9a1): every coordinate resolved. The only compile errors were in `Canary.kt` and all concerned `androidx.datastore`, because the dependency was on `:data` only.
- CI run 42964145048 (commit 4d375c8): see the report for the final result. Calls verified to compile in `:app` main: `@Serializable` route objects and data classes; `NavHost(nav, startDestination = ...) { composable<T> { } }`; `rememberNavController()`; `navigate(route) { popUpTo(findStartDestination().id) { saveState = true }; launchSingleTop; restoreState }`; `currentBackStackEntryAsState()`; `destination.hierarchy`; `backStackEntry.toRoute<T>()`; `AsyncImage(model, contentDescription, imageLoader = loader, modifier, contentScale)`; `ImageLoader.Builder(context).components { add(Interceptor); add(OkHttpNetworkFetcherFactory()) }.diskCache { DiskCache.Builder().directory(path).maxSizeBytes(n).build() }.memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }`; `PullToRefreshBox(isRefreshing, onRefresh) { }` (with `@OptIn(ExperimentalMaterial3Api::class)`, Material3 1.5.0-alpha27); `PreferenceDataStoreFactory.create(corruptionHandler, scope, produceFile)`, `store.data.map { it[key] }`, `store.edit { it[key] = v }`; `@HiltViewModel` with `hiltViewModel()`; `collectAsStateWithLifecycle()`; `Modifier.semantics { contentDescription = "x" }` and `Modifier.testTag`.

## Plan changes needed

1. **Task 0 / Task 9:** `androidx.datastore:datastore-preferences` must be on the `:app` classpath as well as `:data` (the `DataStore<Preferences>` type appears in `:app` DI code). Done in `app/build.gradle.kts` (`implementation(libs.datastore.preferences)`). Alternative if preferred: make it `api` in `:data`.
2. **Task 11:** `AsyncImage` requires `imageLoader` as a required third positional parameter (no default); the plan's call `AsyncImage(model = url, contentDescription = ..., imageLoader = loader, ...)` is already correct. Coil version is 3.6.3, not the candidate 3.3.0.
3. **Task 17:** `PullToRefreshBox` needs `@OptIn(ExperimentalMaterial3Api::class)` if the symbol is marked experimental in 1.5.0-alpha27; the canary carries the opt-in, so later tasks keep it (an unneeded opt-in is only a warning).
4. No other signature differs from what Tasks 11-22 assume.
