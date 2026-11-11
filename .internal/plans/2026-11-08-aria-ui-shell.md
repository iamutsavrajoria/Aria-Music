# Aria UI Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use beads-superpowers:subagent-driven-development (recommended) or beads-superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax. (`bd` is not installed in this environment; track tasks with the task list.)

**Goal:** A usable YouTube-Music-only player: navigation shell (Home, Search, Library), persistent mini player, Now Playing (Material, then Apple-style skin), Queue, Search, Home, Library, Album/Artist/Playlist pages, Settings and first-run onboarding, on top of the playback core. Together with sub-project 2 this is the "alpha" candidate (public builds stay blocked by `docs/release-gates.md`).

**Architecture:** Five vertical slices, each CI-green and usable. Slice 1 builds the data layer (`:domain` contracts and fakes, `:data` Room repositories and DataStore settings, `:innertube` browse adapters over the already-ported `YouTube.*` methods) and `PlayerController` in `:app`. Slices 2 to 5 add Compose screens as packages of `:app` (`app.aria.music.ui.*`), one `@HiltViewModel` per screen exposing one `StateFlow<UiState>`, each screen split into a stateless `XContent(state, actions)` (tested on the emulator) and a thin `XScreen(viewModel)`. Screens depend only on `:domain` interfaces; `:data` and `:innertube` implementations are referenced only from the `app.aria.music.di` package. Spec: `.internal/specs/2026-11-08-aria-ui-shell-design.md` (stress-tested, nine binding decisions). ADR: `docs/decisions/ADR-0003-local-only-library.md`.

**Tech Stack:** Kotlin 2.4.10, Compose 1.12.0, Material3 1.5.0-alpha27, Navigation Compose (type-safe routes), Hilt 2.60.1, Room 2.8.4, Preferences DataStore, Coil 3 (+ network module), Media3 1.10.1, Ktor 3.5.2, coroutines 1.11.0, Robolectric 4.16.1 (SDK 35), AGP 9.3.1, Gradle 9.7.0, KSP 2.3.11, JVM 21.

**Verification caveat (read first):** the sandbox cannot run Gradle. **CI is the only compiler.** Code below is uncompiled; "Expected" lines are predictions. Fix code, not expectations. Read CI with `env -u GH_TOKEN gh api repos/iamutsavrajoria/Aria-Music/actions/runs?event=push&per_page=1` and failure annotations via `check-runs/<jobId>/annotations`. A task is done only when CI is green for its commit. Third-party API names marked **(verify)** were written from memory; Task 0 reads the library sources, and CI compile is the final arbiter. The planner's sandbox could not reach Maven (`dl.google.com` and `repo1.maven.org` answered 403 to CONNECT), so **no new dependency version in this plan is verified**; the versions in Task 0 are candidates.

## Global Constraints

- Versions (verbatim): AGP 9.3.1, Gradle 9.7.0, Kotlin 2.4.10, Compose 1.12.0, Material3 1.5.0-alpha27, Media3 1.10.1, Room 2.8.4, Hilt 2.60.1, Ktor 3.5.2, KSP 2.3.11, coroutines 1.11.0, JVM 21, `compileSdk 37`, `targetSdk 36`, `minSdk 26`. New libraries (DataStore, Coil 3, Navigation Compose, hilt-navigation-compose, lifecycle-viewmodel-compose, lifecycle-runtime-compose, kotlinx-serialization-json) are added to `gradle/libs.versions.toml` only with the versions Task 0 verified.
- Module rules (`ModuleGraphRules`, unchanged): `:domain` depends on nothing; `:data` on `:domain`, `:core`; `:innertube` on `:domain`, `:core`; `:playback` on `:domain` (plus DSP modules later); `:designsystem` on nothing; `:app` is unrestricted but **screens, view models and composables never import `app.aria.data.*` or `app.aria.innertube.*`**. Only files under `app/src/main/kotlin/app/aria/music/di/` may (checked by `scripts/check-screen-imports.sh`, Task 10). `checkModuleGraph` must stay green.
- Track IDs only through `TrackId`. Every new file starts with `// SPDX-License-Identifier: GPL-3.0-only` (XML: `<!-- SPDX-License-Identifier: GPL-3.0-only -->`). New original code inside modules that already have a row in `docs/provenance.csv` needs no new row.
- No third-party endpoints except YouTube and Google's image CDNs (Task 11). No analytics, Firebase or crash services. Cleartext traffic stays off. Release builds log no tokens, URLs with query strings, cookies or IPs. No new WebViews.
- Library semantics (ADR-0003): Library Songs = `song.inLibrary != null`; like sets `inLibrary`; add-to-library sets it without a like; plain plays stay in history only; songs, albums and artists are upserted on first like, add, playlist-add or play; no account sync; `libraryAddToken`/`libraryRemoveToken` stay unused.
- **Database:** the schema goes from version 1 to 2 in Task 3 (adds `home_shelf_cache`). `AppModule` never falls back to destructive migration (`scripts/check-no-destructive-migration.sh`). Version 2 ships with `AriaMigrations.MIGRATION_1_2` and a Robolectric `MigrationTestHelper` test. `data/schemas/app.aria.data.AriaDatabase/1.json` stays committed (the migration test needs it); `2.json` must be **regenerated from CI** (the `ci.yml` step "Print schema as annotations when it changed" prints gzip+base64, the step "Upload schema when it changed" uploads `room-schema`; reconstruct or `gh run download` and commit), and `data/expected-schema.txt` updated. Any later task that changes entities must say so and repeat the procedure.
- Engine rules unchanged: all engine methods run on one dispatcher (main in production); the engine never imports Android or Media3.
- UI rules: every screen has loading, empty, error and offline states; glass only through `GlassSurface` (with its API 26-30 fallback); every user-visible string, including content descriptions, lives in `app/src/main/res/values/strings.xml` (Task 10 enforces); plurals via `pluralStringResource`; numbers, durations and dates through the locale-aware helpers in `app.aria.music.ui.Formatters` (Task 12); English only; interactive targets at least 48dp; layouts must survive font scale 2.0 and the `en-XA` pseudolocale (Task 12 test helpers).
- **UI conventions (every UI task follows these):**
  - `XUiState` is a `sealed interface` with at least `Loading`, `Content(...)`, `Empty`, `Error`, `Offline` (or a `Content` that carries empty sections where the screen is mostly local). `XViewModel` is `@HiltViewModel`, takes only `:domain` interfaces and app classes, exposes `val state: StateFlow<XUiState>` built with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loading)`.
  - `@Composable fun XContent(state: XUiState, actions: XActions, modifier: Modifier = Modifier)` is stateless and is what UI tests call. `@Composable fun XScreen(viewModel: XViewModel = hiltViewModel(), ...)` collects with `collectAsStateWithLifecycle()`.
  - Test tags are constants in the screen file, named `TAG_...`. UI tests live in `app/src/androidTest/kotlin/app/aria/music/ui/` (package `app.aria.music.ui` or a sub-package) and use `createComposeRule()` with `AriaTestTheme { ... }` (Task 12), never Hilt.
  - Playback goes only through `PlayerController` (Task 8). ViewModels never touch `PlaybackEngine`, `PlaybackEngineProvider` or `DefaultPlaybackEngine`.
  - Images only through `AriaImage` (Task 11). Dates and durations only through `Formatters`.
- Media3: use `@androidx.annotation.OptIn(UnstableApi::class)` (not Kotlin's) for unstable APIs in main sources. `kotlin("test-junit")` for Android module tests; Robolectric 4.16.1 `sdk=35` for tests touching Android or Media3 classes (`@RunWith(RobolectricTestRunner::class)`); avoid Kotlin `var` plus an overriding setter clashing on the JVM (use `@JvmField` in fakes); a failing `runTest` that leaks a ticker hangs CI (use the `harness()` pattern in playback tests and release engines via `backgroundScope`); `androidx.core` may not be on every module's classpath (use `Uri.parse`); read library sources before coding against them.
- **CI facts.** The sandbox cannot run Gradle; GitHub Actions is the only compiler (about 6-9 min per run). Jobs: `verify` (static checks, `build test`, both flavors, schema-unchanged check, licenses), `glass-ui` (API 26/34, `:designsystem:connectedDebugAndroidTest`), `playback-service` (API 26/34, `:playback:connectedDebugAndroidTest :app:connectedFossDebugAndroidTest`), and from Task 12 `app-ui` (API 26/34, Compose UI tests in `app.aria.music.ui`). Each task is verified green on CI before the next starts. **CI scope per task (stress-test S2):** during a task, `app-ui` runs on API 34 only; both API 26 and 34 run at slice boundaries (after Tasks 16, 20, 22) and on the final commit. `verify`, `glass-ui` and `playback-service` are unchanged. The job matrix is selected by a commit-message tag `[full-matrix]` or by the branch being at a slice boundary, whichever Task 12 implements simply; API 26 failures found late are fixed at the boundary. All third-party Actions are pinned to a full commit SHA with the tag as a trailing comment (`scripts/check-actions-pinned.sh`); reuse the SHAs already in `ci.yml`.
- **CI iteration policy.** Read the library source (pinned tag) or the Task 0 spike before using a third-party API; do not guess. At most 4 push-and-fix CI rounds per task, then stop and report the failing annotations and what was tried. On failure change production code only: never weaken, skip or baseline a test, lint rule, `checkModuleGraph`, the license check, `scripts/check-webview-safety.sh`, `scripts/check-ui-strings.sh` or `scripts/check-screen-imports.sh` to get green (a test expectation that is itself wrong may be fixed, and the commit message says so). The single deliberate exception is the temporary legacy-file baseline in Task 10, which only shrinks and is emptied in Task 16.
- **Cut order (fixed, decision 7):** Apple skin (Task 23), then Home shelf variety (the "Home shelf variety" step of Task 17), then playlist reordering (Task 20), then onboarding polish (Task 22 steps marked CUTTABLE). Security and accessibility items are never cut.

- **Execution process (stress-test S5).** Each implementer commits locally after every checkbox step and appends a line to `.internal/sdd/2026-11-08-aria-ui-shell/progress.md`. On any agent failure (rate limit included) the controller first runs `git status`, `git log` and reviews the diff, then resumes the same agent or hands the remainder to a fresh one; it never re-dispatches blind. One task per agent, one push per task, CI green before the next. After Task 16 (usable app, end of slice 2) execution pauses for the maintainer to try a debug build and give feedback before slices 3 to 5 continue.

## File Structure

```
domain/src/main/kotlin/app/aria/domain/
  Browse.kt        (create)  AlbumRef, ArtistRef, PlaylistRef, BrowseItem, Shelf, ShelfState, AlbumDetail, ArtistDetail, PlaylistDetail, TrackPage, BrowseSource
  Search.kt        (create)  SearchResults, SearchRepository, SearchHistoryRepository
  Library.kt       (create)  LibrarySort, LibrarySong, LibraryRepository, PlaylistTrackRow, PlaylistRepository
  Home.kt          (create)  HomeLocalSource, HomeShelfCache, CachedHome, HomeRemote, HomeRefresh, HomeRepository, DefaultHomeRepository (Task 7)
  Settings.kt      (create)  AppSettings, SettingsRepository
  Playback.kt      (modify)  PlaybackEngine.setHistoryThresholdMs, PlaybackState.historyThresholdMs (Task 2)
domain/src/testFixtures/kotlin/app/aria/domain/testing/  Samples.kt, Fakes.kt, FakePlaybackEngine.kt (Task 8)
playback/.../DefaultPlaybackEngine.kt (modify, Task 2)
data/.../entities/HomeShelfCacheEntity.kt, HomeShelfCacheDao.kt, AriaMigrations.kt, LibraryDao.kt, PlaylistDao.kt, HomeLocalDao.kt, SearchHistoryDao.kt,
        TrackWriterDao.kt, LibraryRepositoryImpl.kt, PlaylistRepositoryImpl.kt, HomeLocalSourceImpl.kt, SearchHistoryRepositoryImpl.kt,
        HomeShelfCacheImpl.kt, ShelfJsonCodec.kt, DataStoreSettingsRepository.kt
innertube/.../adapter/BrowseMapping.kt, YouTubeBrowseSource.kt, YouTubeSearchRepository.kt
innertube/src/test/.../adapter/*Test.kt, fixtures/FixtureCaptureTest.kt, FixtureFreshnessTest.kt; scripts/capture-innertube-fixtures
app/src/main/kotlin/app/aria/music/
  player/ PlayerController.kt, PlayerUiState.kt, EngineSettingsApplier.kt, ServiceLauncher.kt
  di/ AppModule.kt (modify), DataModule.kt, BrowseModule.kt, UiModule.kt, Qualifiers.kt
  image/ ImageUrlPolicy.kt, ImageSizeCap.kt, AriaImageLoader.kt, AriaImage.kt
  ui/ Formatters.kt, AriaTheme.kt, shell/*, miniplayer/*, nowplaying/*, queue/*, search/*, home/*, library/*, detail/*, settings/*, onboarding/*, common/*
app/src/main/res/values/strings.xml, plurals.xml
app/src/test/kotlin/app/aria/music/... (view model and controller tests), app/src/androidTest/kotlin/app/aria/music/ui/... (Compose tests)
scripts/check-ui-strings.sh + check_ui_strings.py + test_check_ui_strings.py + ui-strings-baseline.txt; scripts/check-screen-imports.sh + test_check_screen_imports.py
docs/ spikes/2026-11-ui-shell-apis.md (Task 0), data-flow.md, providers/youtube-music.md, release-gates.md, manual-device-checklist.md, PRIVACY.md
```

---

### Task 0: Research spike and dependency verification (no Kotlin code)

Slice 1. The planner's sandbox could not reach Maven, so every new coordinate is a candidate. Settle versions and API shapes before code depends on them.

**Files:**
- Create: `docs/spikes/2026-11-ui-shell-apis.md`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `data/build.gradle.kts` (add the libraries so the `verify` job resolves them; no Kotlin sources use them yet)

**Interfaces:**
- Produces: catalog aliases used by later tasks: `libs.datastore.preferences`, `libs.coil.compose`, `libs.coil.network.okhttp`, `libs.navigation.compose`, `libs.hilt.navigation.compose`, `libs.lifecycle.viewmodel.compose`, `libs.lifecycle.runtime.compose`, `libs.kotlinx.serialization.json`.

**Acceptance Criteria:**
- The spike doc records, with source and date, for each coordinate the exact version used: `androidx.datastore:datastore-preferences`, `io.coil-kt.coil3:coil-compose`, `io.coil-kt.coil3:coil-network-okhttp`, `androidx.navigation:navigation-compose` (a release with type-safe routes, `composable<T>` and `toRoute<T>()`; 2.8.0 or newer), `androidx.hilt:hilt-navigation-compose`, `androidx.lifecycle:lifecycle-viewmodel-compose`, `androidx.lifecycle:lifecycle-runtime-compose`, `org.jetbrains.kotlinx:kotlinx-serialization-json`. Source is `maven-metadata.xml` if reachable, otherwise "resolved by CI run <id>".
- The doc contains the "Findings already read" section below (copy it), updated if the code has moved.
- The doc lists the third-party signatures read from source (Coil `Interceptor.Chain`, `ErrorResult`, `DiskCache.Builder`, `ImageLoader.Builder.components`, `OkHttpNetworkFetcherFactory`, `AsyncImage`; Navigation `NavHost`, `composable<T>`, `NavBackStackEntry.toRoute<T>()`; DataStore `PreferenceDataStoreFactory.create`), or "not read; CI compile is the arbiter".
- `verify` is green with the new libraries on the `:app` and `:data` classpaths, the `checkLicense` step included.

**Findings already read (planner, 2026-11-08; re-check the line numbers):**

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

- [ ] **Step 1: Try the registries.** Run `curl -sS -m 20 https://dl.google.com/android/maven2/androidx/datastore/datastore-preferences/maven-metadata.xml` and the same for the other `androidx` artifacts and `https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose/maven-metadata.xml`, `.../coil-network-okhttp/...`, `.../org/jetbrains/kotlinx/kotlinx-serialization-json/...`. If they answer, take `<release>` of the newest **stable** version of each (no alpha, beta, rc). If the proxy answers 403 (the planner's sandbox did), go to Step 2 and say so in the doc.
- [ ] **Step 2: Candidates, resolved by CI.** In `gradle/libs.versions.toml` add under `[versions]` (candidates, replace with Step 1 values when known): `datastore = "1.2.0"`, `coil = "3.3.0"`, `navigation = "2.9.6"`, `hiltNavigation = "1.3.0"`, `lifecycle = "2.10.0"`, `kotlinxSerialization = "1.9.0"`; under `[libraries]`:

```toml
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigation" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
```

  `app/build.gradle.kts`: add `alias(libs.plugins.kotlin.serialization)` to `plugins` (routes are `@Serializable`) and these dependencies: `implementation(libs.coil.compose)`, `implementation(libs.coil.network.okhttp)`, `implementation(libs.navigation.compose)`, `implementation(libs.hilt.navigation.compose)`, `implementation(libs.lifecycle.viewmodel.compose)`, `implementation(libs.lifecycle.runtime.compose)`, `implementation(libs.kotlinx.serialization.json)`. `data/build.gradle.kts`: `implementation(libs.datastore.preferences)`. Push. A wrong version fails `verify` with "Could not resolve"; fix the version (newest stable that resolves) and push again (counts toward the 4 rounds).
- [ ] **Step 3: Read signatures.** Shallow-clone (`git clone --depth 1 --branch <tag>`; the git proxy serves GitHub, raw and tree pages are blocked) `coil-kt/coil` at the tag matching `coil` and read: `coil-core/.../intercept/Interceptor.kt` (expects `intercept(chain)`, `Chain.request`, `Chain.size`, `Chain.withSize`, `Chain.proceed()`), `ErrorResult` constructor, `ImageRequest.Builder.size`, `DiskCache.Builder` (`directory`, `maxSizeBytes`), `ImageLoader.Builder.components { add(...) }`, `OkHttpNetworkFetcherFactory`, and `coil-compose-core/.../AsyncImage.kt` (parameter order). For Navigation and DataStore read `androidx/androidx` via sparse checkout if time allows. Copy declarations into the spike doc. Where a signature differs from this plan, list the exact edit for the affected task under "Plan changes needed".
- [ ] **Step 3b: Compile canary (stress-test decision S1).** Create `app/src/main/kotlin/app/aria/music/Canary.kt` (delete it in Task 12 Step 4) that uses every new third-party API exactly as the later tasks will, so a wrong call shape fails here and the dependent tasks are corrected before they run: a `@Serializable` route object plus `NavHost(...) { composable<CanaryRoute> { ... } }` with `rememberNavController()` and `navController.navigate(CanaryRoute)`; `AsyncImage(model, contentDescription, imageLoader = ...)` with an `ImageLoader.Builder(context)` that sets a `DiskCache.Builder` and one `Interceptor`; `PullToRefreshBox(isRefreshing, onRefresh) { }`; `DataStore<Preferences>` read (`data.map { it[key] }`) and write (`edit { }`) through `PreferenceDataStoreFactory.create`; `hiltViewModel()`; `collectAsStateWithLifecycle()`; `Modifier.semantics { contentDescription = "x" }` and `testTag`. Push; `verify` must compile it. Any signature that differs from what Tasks 11-22 write is recorded in the spike doc under "Plan changes needed" and the affected tasks are edited before they start. Task 24 greps that `Canary` no longer exists.
- [ ] **Step 4: Write the doc and commit.** `docs/spikes/2026-11-ui-shell-apis.md` (sections: how read, coordinates table, signatures, findings table above, plan changes needed). Push; `verify` green. Commit `docs(spike): UI shell dependency and API findings`. Any BLOCKED conclusion is reported to the controller before Task 1.

---

### Task 1: Domain contracts, models and fakes

Slice 1. Everything later consumes these names.

**Files:**
- Modify: `domain/build.gradle.kts`
- Create: `domain/src/main/kotlin/app/aria/domain/{Browse,Search,Library,Home,Settings}.kt` (Home.kt holds only the interfaces and data classes here; `DefaultHomeRepository` is added in Task 7)
- Create: `domain/src/testFixtures/kotlin/app/aria/domain/testing/{Samples,Fakes}.kt`
- Test: `domain/src/test/kotlin/app/aria/domain/BrowseModelsTest.kt`

**Interfaces:**
- Consumes: `Track`, `TrackId`, `NetworkMonitor`.
- Produces: the types in the code below, plus fakes in package `app.aria.domain.testing`.

**Acceptance Criteria:**
- `:domain` stays pure Kotlin; `java-test-fixtures` exposes the fakes to `:app` unit and instrumented tests (`testImplementation(testFixtures(project(":domain")))`, Task 8/12).
- `BrowseItem.key` values are unique per kind and id; `ArtistDetail.topTracks` is the songs of the first shelf that has songs; `AppSettings.normalized()` clamps the history threshold to 10-120 s and the sleep default to 1-240 minutes; `SearchResults.isEmpty` is true only when all four lists are empty.

- [ ] **Step 1: Failing test:**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowseModelsTest {
    private fun track(n: Int) = Track(TrackId.youtube("track%06d".format(n)), "T$n", listOf("A"), 1_000L, null)

    @Test fun keysAreUniquePerKindAndId() {
        val keys = listOf(
            BrowseItem.Song(track(1)).key,
            BrowseItem.Album(AlbumRef("track000001", "T", emptyList(), null, null)).key,
            BrowseItem.Artist(ArtistRef("track000001", "T", null)).key,
            BrowseItem.Playlist(PlaylistRef("track000001", "T", null, null, null)).key,
        )
        assertEquals(4, keys.toSet().size)
    }

    @Test fun topTracksComeFromTheFirstShelfWithSongs() {
        val albums = Shelf("Albums", null, listOf(BrowseItem.Album(AlbumRef("a", "A", emptyList(), null, null))))
        val songs = Shelf("Songs", null, listOf(BrowseItem.Song(track(1)), BrowseItem.Song(track(2))))
        val d = ArtistDetail(ArtistRef("UC1", "N", null), null, null, listOf(albums, songs))
        assertEquals(listOf(track(1), track(2)), d.topTracks)
        assertTrue(ArtistDetail(ArtistRef("UC1", "N", null), null, null, listOf(albums)).topTracks.isEmpty())
    }

    @Test fun settingsAreClamped() {
        assertEquals(10_000L, AppSettings(historyThresholdMs = 1).normalized().historyThresholdMs)
        assertEquals(120_000L, AppSettings(historyThresholdMs = 9_999_999).normalized().historyThresholdMs)
        assertEquals(45_000L, AppSettings(historyThresholdMs = 45_000).normalized().historyThresholdMs)
        assertEquals(1, AppSettings(sleepTimerDefaultMinutes = 0).normalized().sleepTimerDefaultMinutes)
        assertEquals(240, AppSettings(sleepTimerDefaultMinutes = 9_999).normalized().sleepTimerDefaultMinutes)
    }

    @Test fun emptySearchResults() {
        assertTrue(SearchResults(emptyList(), emptyList(), emptyList(), emptyList()).isEmpty)
        assertFalse(SearchResults(listOf(track(1)), emptyList(), emptyList(), emptyList()).isEmpty)
    }
}
```

- [ ] **Step 2:** Push; CI `:domain:test` fails (unresolved references). Expected: FAIL.
- [ ] **Step 3: Implement.** `domain/build.gradle.kts`: `plugins { id("org.jetbrains.kotlin.jvm"); id("java-test-fixtures") }` and add `testFixturesApi(libs.coroutines.core)`, `testFixturesImplementation(kotlin("test"))`. (If CI shows that Android modules cannot consume the test-fixtures variant, fall back to moving the two fixture files into `domain/src/main/kotlin/app/aria/domain/testing/` and report it; that costs one round.)

`Browse.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

data class AlbumRef(val id: String, val title: String, val artists: List<String>, val year: Int?, val thumbnailUrl: String?)
data class ArtistRef(val id: String, val name: String, val thumbnailUrl: String?)
data class PlaylistRef(val id: String, val title: String, val subtitle: String?, val thumbnailUrl: String?, val trackCount: Int?)

/** One tile or row of a shelf, search section or artist page. */
sealed interface BrowseItem {
    /** Stable, unique within a list; used as the LazyColumn/LazyRow key. */
    val key: String

    data class Song(val track: Track) : BrowseItem { override val key: String get() = "song:${track.id.raw}" }
    data class Album(val album: AlbumRef) : BrowseItem { override val key: String get() = "album:${album.id}" }
    data class Artist(val artist: ArtistRef) : BrowseItem { override val key: String get() = "artist:${artist.id}" }
    data class Playlist(val playlist: PlaylistRef) : BrowseItem { override val key: String get() = "playlist:${playlist.id}" }
}

data class Shelf(val title: String, val subtitle: String?, val items: List<BrowseItem>)

/** A remote shelf that parsed, or one that failed on its own ("section failed" state). */
sealed interface ShelfState {
    data class Ready(val shelf: Shelf) : ShelfState
    data class Failed(val title: String?) : ShelfState
}

data class AlbumDetail(val album: AlbumRef, val tracks: List<Track>, val otherVersions: List<AlbumRef>)

data class ArtistDetail(val artist: ArtistRef, val description: String?, val subscriberText: String?, val shelves: List<Shelf>) {
    /** Songs of the first shelf that has any; what "Play" and "Shuffle" on the artist page use. */
    val topTracks: List<Track>
        get() = shelves.firstNotNullOfOrNull { s ->
            s.items.filterIsInstance<BrowseItem.Song>().map { it.track }.takeIf { it.isNotEmpty() }
        } ?: emptyList()
}

data class PlaylistDetail(val playlist: PlaylistRef, val tracks: List<Track>, val continuation: String?)
data class TrackPage(val tracks: List<Track>, val continuation: String?)

/** Remote browse data. Implemented in :innertube. Failures are returned, never thrown. */
interface BrowseSource {
    suspend fun home(): Result<List<ShelfState>>
    suspend fun album(id: String): Result<AlbumDetail>
    suspend fun artist(id: String): Result<ArtistDetail>
    suspend fun playlist(id: String): Result<PlaylistDetail>
    suspend fun playlistMore(continuation: String): Result<TrackPage>
}
```

`Search.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlinx.coroutines.flow.Flow

data class SearchResults(
    val songs: List<Track>,
    val albums: List<AlbumRef>,
    val artists: List<ArtistRef>,
    val playlists: List<PlaylistRef>,
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

/** Remote search. Implemented in :innertube. */
interface SearchRepository {
    suspend fun suggestions(query: String): Result<List<String>>
    suspend fun search(query: String): Result<SearchResults>
}

/** Local, clearable search history (table `search_history`). Newest first. */
interface SearchHistoryRepository {
    fun recent(limit: Int): Flow<List<String>>
    suspend fun add(query: String)
    suspend fun remove(query: String)
    suspend fun clear()
}
```

`Library.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlinx.coroutines.flow.Flow

enum class LibrarySort { RECENTLY_ADDED, TITLE }

/** Slim row of the Library Songs list. */
data class LibrarySong(val track: Track, val liked: Boolean)

/** Local library (ADR-0003). Lists are Flows backed by SQL queries; [query] is a plain-text filter ("" = none). */
interface LibraryRepository {
    fun songs(query: String, sort: LibrarySort): Flow<List<LibrarySong>>
    fun albums(query: String, sort: LibrarySort): Flow<List<AlbumRef>>
    fun artists(query: String, sort: LibrarySort): Flow<List<ArtistRef>>
    fun isLiked(id: TrackId): Flow<Boolean>
    fun isInLibrary(id: TrackId): Flow<Boolean>
    fun isAlbumSaved(id: String): Flow<Boolean>
    fun isArtistFollowed(id: String): Flow<Boolean>
    /** Upserts the song; liking also sets inLibrary, unliking keeps it. */
    suspend fun setLiked(track: Track, liked: Boolean)
    /** Upserts the song; removing from the library also clears the like. */
    suspend fun setInLibrary(track: Track, inLibrary: Boolean)
    suspend fun setAlbumSaved(album: AlbumRef, saved: Boolean)
    suspend fun setArtistFollowed(artist: ArtistRef, followed: Boolean)
}

/** One playlist entry; [entryId] identifies the row (the same track may appear twice). */
data class PlaylistTrackRow(val entryId: Int, val track: Track)

/** User-created playlists (local only). */
interface PlaylistRepository {
    fun playlists(query: String, sort: LibrarySort): Flow<List<PlaylistRef>>
    fun playlist(id: String): Flow<PlaylistRef?>
    fun tracks(playlistId: String): Flow<List<PlaylistTrackRow>>
    suspend fun create(name: String): String
    suspend fun rename(id: String, name: String)
    suspend fun delete(id: String)
    suspend fun addTracks(id: String, tracks: List<Track>)
    suspend fun removeEntry(playlistId: String, entryId: Int)
    /** Rewrites positions in one transaction. [orderedEntryIds] must be exactly the playlist's current entry ids, else IllegalArgumentException and nothing changes. */
    suspend fun reorder(playlistId: String, orderedEntryIds: List<Int>)
}
```

`Home.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlinx.coroutines.flow.Flow

/** Local Home sections; they work fully offline. */
interface HomeLocalSource {
    fun quickPicks(limit: Int): Flow<List<Track>>
    fun recentlyPlayed(limit: Int): Flow<List<Track>>
}

data class CachedHome(val shelves: List<Shelf>, val fetchedAtEpochMs: Long)

/** Parsed Home shelves kept between runs (decision 3). Undecodable rows are dropped by the implementation. */
interface HomeShelfCache {
    fun observe(): Flow<CachedHome?>
    suspend fun replace(shelves: List<Shelf>, fetchedAtEpochMs: Long)
}

data class HomeRemote(val shelves: List<Shelf>, val fetchedAtEpochMs: Long, val outdated: Boolean)

/** Result of a successful refresh: every shelf of the fresh response, failed ones included. */
data class HomeRefresh(val shelves: List<ShelfState>)

interface HomeRepository {
    fun quickPicks(limit: Int): Flow<List<Track>>
    fun recentlyPlayed(limit: Int): Flow<List<Track>>
    /** Cached remote shelves; emits null when there is no usable cache. Re-emits after [refresh]. */
    fun remote(): Flow<HomeRemote?>
    /** True when the cache is missing or older than the revalidation age. */
    suspend fun needsRevalidation(): Boolean
    /** Fetches fresh shelves and replaces the cache. A failure leaves the cache untouched. */
    suspend fun refresh(): Result<HomeRefresh>
}
```

`Settings.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val historyThresholdMs: Long = DEFAULT_HISTORY_THRESHOLD_MS,
    val endlessRadio: Boolean = true,
    val skipSilence: Boolean = false,
    val normalizeLoudness: Boolean = true,
    val sleepTimerDefaultMinutes: Int = 15,
    val onboardingDone: Boolean = false,
) {
    fun normalized(): AppSettings = copy(
        historyThresholdMs = historyThresholdMs.coerceIn(MIN_HISTORY_THRESHOLD_MS, MAX_HISTORY_THRESHOLD_MS),
        sleepTimerDefaultMinutes = sleepTimerDefaultMinutes.coerceIn(1, 240),
    )

    companion object {
        const val MIN_HISTORY_THRESHOLD_MS = 10_000L
        const val MAX_HISTORY_THRESHOLD_MS = 120_000L
        const val DEFAULT_HISTORY_THRESHOLD_MS = 30_000L
    }
}

interface SettingsRepository {
    /** Emits the stored settings (defaults when nothing is stored), already normalized. */
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
```

`domain/src/testFixtures/.../Samples.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain.testing

import app.aria.domain.*

object Samples {
    fun track(n: Int, title: String = "Song $n") =
        Track(TrackId.youtube("track%06d".format(n)), title, listOf("Artist $n"), 180_000L, null)
    fun tracks(count: Int, from: Int = 1) = (from until from + count).map { track(it) }
    fun album(n: Int) = AlbumRef("MPREb_$n", "Album $n", listOf("Artist $n"), 2020, null)
    fun artist(n: Int) = ArtistRef("UC$n", "Artist $n", null)
    fun playlist(n: Int) = PlaylistRef("PL$n", "Playlist $n", "Someone", null, 10)
}
```

`Fakes.kt` (all in package `app.aria.domain.testing`; in-memory, deterministic, no coroutines dispatchers of their own):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain.testing

import app.aria.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class FakeNetworkMonitor(initial: Boolean = true) : NetworkMonitor {
    override val online = MutableStateFlow(initial)
}

class FakeBrowseSource : BrowseSource {
    var homeResult: Result<List<ShelfState>> = Result.success(emptyList())
    val albums = mutableMapOf<String, Result<AlbumDetail>>()
    val artists = mutableMapOf<String, Result<ArtistDetail>>()
    val playlists = mutableMapOf<String, Result<PlaylistDetail>>()
    val more = mutableMapOf<String, Result<TrackPage>>()
    var homeCalls = 0
    override suspend fun home(): Result<List<ShelfState>> { homeCalls++; return homeResult }
    override suspend fun album(id: String) = albums[id] ?: Result.failure(NoSuchElementException(id))
    override suspend fun artist(id: String) = artists[id] ?: Result.failure(NoSuchElementException(id))
    override suspend fun playlist(id: String) = playlists[id] ?: Result.failure(NoSuchElementException(id))
    override suspend fun playlistMore(continuation: String) = more[continuation] ?: Result.failure(NoSuchElementException(continuation))
}

class FakeSearchRepository : SearchRepository {
    var suggestionResult: Result<List<String>> = Result.success(emptyList())
    var searchResult: Result<SearchResults> = Result.success(SearchResults(emptyList(), emptyList(), emptyList(), emptyList()))
    val suggestionQueries = mutableListOf<String>()
    val searchQueries = mutableListOf<String>()
    override suspend fun suggestions(query: String): Result<List<String>> { suggestionQueries += query; return suggestionResult }
    override suspend fun search(query: String): Result<SearchResults> { searchQueries += query; return searchResult }
}

class FakeSearchHistoryRepository(initial: List<String> = emptyList()) : SearchHistoryRepository {
    val items = MutableStateFlow(initial)
    override fun recent(limit: Int): Flow<List<String>> = items.map { it.take(limit) }
    override suspend fun add(query: String) { items.value = listOf(query) + items.value.filter { it != query } }
    override suspend fun remove(query: String) { items.value = items.value.filter { it != query } }
    override suspend fun clear() { items.value = emptyList() }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial.normalized())
    override val settings: Flow<AppSettings> = state
    val current: AppSettings get() = state.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) { state.value = transform(state.value).normalized() }
}

class FakeHomeRepository : HomeRepository {
    val quick = MutableStateFlow<List<Track>>(emptyList())
    val recent = MutableStateFlow<List<Track>>(emptyList())
    val remoteState = MutableStateFlow<HomeRemote?>(null)
    var refreshResult: Result<HomeRefresh> = Result.success(HomeRefresh(emptyList()))
    var revalidate = true
    var refreshCalls = 0
    override fun quickPicks(limit: Int): Flow<List<Track>> = quick
    override fun recentlyPlayed(limit: Int): Flow<List<Track>> = recent
    override fun remote(): Flow<HomeRemote?> = remoteState
    override suspend fun needsRevalidation() = revalidate
    override suspend fun refresh(): Result<HomeRefresh> { refreshCalls++; return refreshResult }
}

class FakeLibraryRepository : LibraryRepository {
    private data class Entry(val track: Track, val liked: Boolean, val inLibrary: Boolean, val seq: Int)
    private val songsState = MutableStateFlow<Map<TrackId, Entry>>(emptyMap())
    private val albumsState = MutableStateFlow<List<AlbumRef>>(emptyList())
    private val artistsState = MutableStateFlow<List<ArtistRef>>(emptyList())
    private var seq = 0

    private fun match(text: String, q: String) = q.isBlank() || text.contains(q, ignoreCase = true)

    override fun songs(query: String, sort: LibrarySort): Flow<List<LibrarySong>> = songsState.map { m ->
        val rows = m.values.filter { it.inLibrary && (match(it.track.title, query) || it.track.artists.any { a -> match(a, query) }) }
        val sorted = if (sort == LibrarySort.TITLE) rows.sortedBy { it.track.title.lowercase() } else rows.sortedByDescending { it.seq }
        sorted.map { LibrarySong(it.track, it.liked) }
    }
    override fun albums(query: String, sort: LibrarySort): Flow<List<AlbumRef>> = albumsState.map { l ->
        l.filter { match(it.title, query) }.let { if (sort == LibrarySort.TITLE) it.sortedBy { a -> a.title.lowercase() } else it.reversed() }
    }
    override fun artists(query: String, sort: LibrarySort): Flow<List<ArtistRef>> = artistsState.map { l ->
        l.filter { match(it.name, query) }.let { if (sort == LibrarySort.TITLE) it.sortedBy { a -> a.name.lowercase() } else it.reversed() }
    }
    override fun isLiked(id: TrackId) = songsState.map { it[id]?.liked == true }
    override fun isInLibrary(id: TrackId) = songsState.map { it[id]?.inLibrary == true }
    override fun isAlbumSaved(id: String) = albumsState.map { l -> l.any { it.id == id } }
    override fun isArtistFollowed(id: String) = artistsState.map { l -> l.any { it.id == id } }

    override suspend fun setLiked(track: Track, liked: Boolean) {
        val old = songsState.value[track.id]
        val inLib = if (liked) true else old?.inLibrary == true
        songsState.value = songsState.value + (track.id to Entry(track, liked, inLib, old?.seq ?: ++seq))
    }
    override suspend fun setInLibrary(track: Track, inLibrary: Boolean) {
        val old = songsState.value[track.id]
        songsState.value = songsState.value + (track.id to Entry(track, if (inLibrary) old?.liked == true else false, inLibrary, old?.seq ?: ++seq))
    }
    override suspend fun setAlbumSaved(album: AlbumRef, saved: Boolean) {
        albumsState.value = albumsState.value.filter { it.id != album.id } + if (saved) listOf(album) else emptyList()
    }
    override suspend fun setArtistFollowed(artist: ArtistRef, followed: Boolean) {
        artistsState.value = artistsState.value.filter { it.id != artist.id } + if (followed) listOf(artist) else emptyList()
    }
}

class FakePlaylistRepository : PlaylistRepository {
    private class P(val ref: PlaylistRef, val rows: MutableList<PlaylistTrackRow> = mutableListOf())
    private val state = MutableStateFlow<List<P>>(emptyList())
    private val tick = MutableStateFlow(0)
    private var nextId = 1
    private var nextEntry = 1
    private fun bump() { tick.value++ }
    private fun refOf(p: P) = p.ref.copy(trackCount = p.rows.size)

    override fun playlists(query: String, sort: LibrarySort): Flow<List<PlaylistRef>> = tick.map {
        state.value.map(::refOf).filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
            .let { if (sort == LibrarySort.TITLE) it.sortedBy { r -> r.title.lowercase() } else it.reversed() }
    }
    override fun playlist(id: String): Flow<PlaylistRef?> = tick.map { state.value.firstOrNull { it.ref.id == id }?.let(::refOf) }
    override fun tracks(playlistId: String): Flow<List<PlaylistTrackRow>> = tick.map { state.value.firstOrNull { it.ref.id == playlistId }?.rows?.toList() ?: emptyList() }
    override suspend fun create(name: String): String {
        val id = "LP${nextId++}"
        state.value = state.value + P(PlaylistRef(id, name, null, null, 0)); bump(); return id
    }
    override suspend fun rename(id: String, name: String) {
        state.value = state.value.map { if (it.ref.id == id) P(it.ref.copy(title = name), it.rows) else it }; bump()
    }
    override suspend fun delete(id: String) { state.value = state.value.filter { it.ref.id != id }; bump() }
    override suspend fun addTracks(id: String, tracks: List<Track>) {
        state.value.firstOrNull { it.ref.id == id }?.rows?.addAll(tracks.map { PlaylistTrackRow(nextEntry++, it) }); bump()
    }
    override suspend fun removeEntry(playlistId: String, entryId: Int) {
        state.value.firstOrNull { it.ref.id == playlistId }?.rows?.removeAll { it.entryId == entryId }; bump()
    }
    override suspend fun reorder(playlistId: String, orderedEntryIds: List<Int>) {
        val p = state.value.first { it.ref.id == playlistId }
        require(orderedEntryIds.sorted() == p.rows.map { it.entryId }.sorted()) { "entry ids do not match" }
        val byId = p.rows.associateBy { it.entryId }
        p.rows.clear(); p.rows.addAll(orderedEntryIds.map { byId.getValue(it) }); bump()
    }
}
```

- [ ] **Step 4:** Push; `:domain:test` green, whole build green. **Commit** `feat(domain): browse, library, search, home and settings contracts with fakes`.

---

### Task 2: Engine setting `historyThresholdMs`

Slice 1, decision 5. Behaviour change in `:playback` only.

**Files:**
- Modify: `domain/src/main/kotlin/app/aria/domain/Playback.kt`, `playback/src/main/kotlin/app/aria/playback/DefaultPlaybackEngine.kt`
- Test: `playback/src/test/kotlin/app/aria/playback/EngineHistoryThresholdTest.kt`

**Interfaces:**
- Consumes: `AppSettings.MIN_HISTORY_THRESHOLD_MS`, `MAX_HISTORY_THRESHOLD_MS`, `DEFAULT_HISTORY_THRESHOLD_MS`; test kit `harness()`, `track(n)`.
- Produces: `PlaybackEngine.setHistoryThresholdMs(ms: Long)`; `PlaybackState.historyThresholdMs: Long` (default 30 000).

**Acceptance Criteria:**
- The engine records a play after the threshold, clamped to 10-120 s; default behaviour (30 s) unchanged; changing the threshold mid-track applies to the current track; a play is still recorded once per track.
- All existing playback tests pass unchanged.

- [ ] **Step 1: Failing test** (same style as `EngineHistoryResumeTest`):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import app.aria.domain.AppSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineHistoryThresholdTest {
    private fun playing(h: Harness): Harness {
        h.engine.playNow((1..2).map(::track)); h.settle()
        h.engine.onPlayWhenReadyChanged(true); h.engine.onBufferingChanged(false); h.settle()
        return h
    }

    @Test fun defaultIsThirtySeconds() = runTest {
        val h = playing(harness().attach())
        assertEquals(30_000L, h.state.historyThresholdMs)
        h.release()
    }

    @Test fun recordsAtTheConfiguredThreshold() = runTest {
        val h = playing(harness().attach())
        h.engine.setHistoryThresholdMs(45_000)
        h.advance(44_000); assertEquals(0, h.history.recorded.size)
        h.advance(1_500); assertEquals(1, h.history.recorded.size)
        h.advance(60_000); assertEquals(1, h.history.recorded.size)
        h.release()
    }

    @Test fun tooSmallIsClampedToTenSeconds() = runTest {
        val h = playing(harness().attach())
        h.engine.setHistoryThresholdMs(1)
        assertEquals(AppSettings.MIN_HISTORY_THRESHOLD_MS, h.state.historyThresholdMs)
        h.advance(9_000); assertEquals(0, h.history.recorded.size)
        h.advance(1_500); assertEquals(1, h.history.recorded.size)
        h.release()
    }

    @Test fun tooLargeIsClampedToTwoMinutes() = runTest {
        val h = playing(harness().attach())
        h.engine.setHistoryThresholdMs(10_000_000)
        assertEquals(AppSettings.MAX_HISTORY_THRESHOLD_MS, h.state.historyThresholdMs)
        h.advance(119_000); assertEquals(0, h.history.recorded.size)
        h.advance(1_500); assertEquals(1, h.history.recorded.size)
        h.release()
    }

    @Test fun loweringMidTrackAppliesToTheCurrentTrack() = runTest {
        val h = playing(harness().attach())
        h.advance(20_000); assertEquals(0, h.history.recorded.size)
        h.engine.setHistoryThresholdMs(15_000); h.advance(1_000)
        assertEquals(1, h.history.recorded.size)
        h.release()
    }
}
```

- [ ] **Step 2:** Push; CI fails (unresolved `setHistoryThresholdMs`). Expected: FAIL.
- [ ] **Step 3: Implement.** `Playback.kt`: add to `PlaybackState` (last constructor property) `val historyThresholdMs: Long = AppSettings.DEFAULT_HISTORY_THRESHOLD_MS,` and to `PlaybackEngine` `fun setHistoryThresholdMs(ms: Long)`. `DefaultPlaybackEngine.kt`: add field `private var historyThresholdMs = AppSettings.DEFAULT_HISTORY_THRESHOLD_MS`; in `publish()` pass `historyThresholdMs = historyThresholdMs`; in `onTick()` replace `listenedMs >= HISTORY_THRESHOLD_MS` by `listenedMs >= historyThresholdMs`; add

```kotlin
    override fun setHistoryThresholdMs(ms: Long) {
        historyThresholdMs = ms.coerceIn(AppSettings.MIN_HISTORY_THRESHOLD_MS, AppSettings.MAX_HISTORY_THRESHOLD_MS)
        publish()
    }
```

  and change the companion constant to `const val HISTORY_THRESHOLD_MS = AppSettings.DEFAULT_HISTORY_THRESHOLD_MS` (kept so nothing else breaks). `app.aria.domain.*` is already imported in the engine.
- [ ] **Step 4:** Push; all `:playback:test` and the build green. **Commit** `feat(playback): configurable history threshold (10-120 s)`.

---

### Task 3: Room schema v2, migration and the Home cache table

Slice 1, decision 3 (schema part). Adds `home_shelf_cache` and the migration; the DAOs of later tasks do not change the schema.

**Files:**
- Create: `data/src/main/kotlin/app/aria/data/entities/HomeShelfCacheEntity.kt`, `data/src/main/kotlin/app/aria/data/HomeShelfCacheDao.kt`, `data/src/main/kotlin/app/aria/data/AriaMigrations.kt`, `data/schemas/app.aria.data.AriaDatabase/2.json` (from CI)
- Modify: `data/src/main/kotlin/app/aria/data/AriaDatabase.kt`, `data/expected-schema.txt`, `data/src/test/kotlin/app/aria/data/SchemaContentTest.kt`, `.github/workflows/ci.yml` (print step), `app/src/main/kotlin/app/aria/music/di/AppModule.kt`
- Test: `data/src/test/kotlin/app/aria/data/MigrationV2Test.kt`

**Interfaces:**
- Produces: `HomeShelfCacheEntity(shelfKey: String, position: Int, payload: String, fetchedAtEpochMs: Long)`; `HomeShelfCacheDao.observe(): Flow<List<HomeShelfCacheEntity>>`, `replaceAll(rows)`, `delete(keys: List<String>)`; `AriaMigrations.MIGRATION_1_2`; `AriaDatabase.homeShelfCacheDao()`; database version 2.

**Acceptance Criteria:**
- A version 1 database containing a song migrates to 2 with the song intact and an empty `home_shelf_cache`; `runMigrationsAndValidate` passes (Room compares to `2.json`).
- `SchemaContentTest` expects 26 tables and 3 views, reading `2.json`; `expected-schema.txt` has `table:home_shelf_cache`.
- `AppModule` registers the migration; `scripts/check-no-destructive-migration.sh` still passes.
- CI's "Print schema" step prints whichever schema file changed (not only `1.json`).

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationV2Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AriaDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun v1ToV2KeepsDataAndAddsTheCacheTable() {
        helper.createDatabase("aria-mig", 1).apply {
            execSQL("INSERT INTO song (id, title, duration, liked, totalPlayTime) VALUES ('dQw4w9WgXcQ', 'Kept', 200, 0, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate("aria-mig", 2, true, AriaMigrations.MIGRATION_1_2)
        db.query("SELECT title FROM song WHERE id = 'dQw4w9WgXcQ'").use {
            assertTrue(it.moveToFirst()); assertEquals("Kept", it.getString(0))
        }
        db.query("SELECT COUNT(*) FROM home_shelf_cache").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.execSQL("INSERT INTO home_shelf_cache (shelfKey, position, payload, fetchedAtEpochMs) VALUES ('yt:0', 0, '{}', 1)")
        db.close()
    }

    @Test fun migrationIsNotDestructive() {
        helper.createDatabase("aria-mig2", 1).close()
        val db = helper.runMigrationsAndValidate("aria-mig2", 2, true, AriaMigrations.MIGRATION_1_2)
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'queue_item'").use { assertTrue(it.moveToFirst()) }
        assertFalse(db.query("SELECT name FROM sqlite_master WHERE name = 'nope'").use { it.moveToFirst() })
        db.close()
    }
}
```

  In `SchemaContentTest.kt` change the path to `schemas/app.aria.data.AriaDatabase/2.json`, rename `hasTwentyFiveTablesAndThreeViews` to `hasTwentySixTablesAndThreeViews` and assert 26 tables. In `data/expected-schema.txt` insert the line `table:home_shelf_cache` between `table:format` and `table:lyrics`.

- [ ] **Step 2:** Push; CI fails (unresolved `AriaMigrations`). Expected: FAIL.
- [ ] **Step 3: Implement.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One cached remote Home shelf: [payload] is the versioned JSON of a domain `Shelf` (see ShelfJsonCodec). */
@Entity(tableName = "home_shelf_cache")
data class HomeShelfCacheEntity(
    @PrimaryKey val shelfKey: String,
    val position: Int,
    val payload: String,
    val fetchedAtEpochMs: Long,
)
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.aria.data.entities.HomeShelfCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HomeShelfCacheDao {
    @Query("SELECT * FROM home_shelf_cache ORDER BY position") abstract fun observe(): Flow<List<HomeShelfCacheEntity>>
    @Query("DELETE FROM home_shelf_cache") protected abstract suspend fun clear()
    @Insert protected abstract suspend fun insert(rows: List<HomeShelfCacheEntity>)
    @Query("DELETE FROM home_shelf_cache WHERE shelfKey IN (:keys)") abstract suspend fun delete(keys: List<String>)

    @Transaction open suspend fun replaceAll(rows: List<HomeShelfCacheEntity>) { clear(); insert(rows) }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AriaMigrations {
    /** Adds the Home shelf cache (UI shell, decision 3). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `home_shelf_cache` (`shelfKey` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                    "`payload` TEXT NOT NULL, `fetchedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`shelfKey`))",
            )
        }
    }
}
```

  `AriaDatabase.kt`: import `HomeShelfCacheEntity`, add it to `entities`, `version = 2`, `abstract fun homeShelfCacheDao(): HomeShelfCacheDao`, and change the KDoc to "Schema version 2 ... 26 tables". `AppModule.database`: `Room.databaseBuilder(c, AriaDatabase::class.java, "aria.db").addMigrations(AriaMigrations.MIGRATION_1_2).build()` (import `app.aria.data.AriaMigrations`). `ci.yml` "Print schema as annotations when it changed": replace the `f=` line with `f="$(git status --porcelain -- data/schemas | awk '{print $2}' | head -1)"`.
- [ ] **Step 4: Get `2.json` from CI.** Push. Expect unit tests green but "Schema is committed and unchanged" red (the Room plugin wrote `2.json`). Download the `room-schema` artifact (`env -u GH_TOKEN gh run download <run-id> -n room-schema -D /tmp/schema`) or rebuild the file from the `schema-NNN` annotations (concatenate, `base64 -d | gunzip`). Copy `app.aria.data.AriaDatabase/2.json` into `data/schemas/app.aria.data.AriaDatabase/`, check it contains `home_shelf_cache` and `"version": 2`, commit.
- [ ] **Step 5:** Push; all jobs green (migration test now validates against `2.json`). **Commit** `feat(data): schema v2 with Home shelf cache and migration`.

---

### Task 4: `:data` repositories (library, playlists, local Home sections, search history)

Slice 1, decisions 2 and 9. SQL-side sort and filter, slim projections, no Paging.

**Files:**
- Create: `data/src/main/kotlin/app/aria/data/{TrackWriterDao,LibraryDao,PlaylistDao,HomeLocalDao,SearchHistoryDao,SqlRows,LibraryRepositoryImpl,PlaylistRepositoryImpl,HomeLocalSourceImpl,SearchHistoryRepositoryImpl}.kt`
- Modify: `data/src/main/kotlin/app/aria/data/{AriaDatabase,PlayEventDao,PlayEventRepositoryImpl}.kt`
- Test: `data/src/test/kotlin/app/aria/data/{LibraryRepositoryTest,PlaylistRepositoryTest,HomeLocalSourceTest,SearchHistoryRepositoryTest}.kt` (existing `PlayEventRepositoryTest` must still pass)

**Interfaces:**
- Consumes: `LibraryRepository`, `PlaylistRepository`, `HomeLocalSource`, `SearchHistoryRepository`, `LibrarySong`, `LibrarySort`, `PlaylistTrackRow`, `AlbumRef`, `ArtistRef`, `PlaylistRef`, `Track`, entities `SongEntity`, `ArtistEntity`, `SongArtistMap`, `AlbumEntity`, `AlbumArtistMap`, `PlaylistEntity`, `PlaylistSongMap`, `SearchHistory`, `Event`.
- Produces: `class LibraryRepositoryImpl(dao: LibraryDao, clock: () -> Long = System::currentTimeMillis) : LibraryRepository`; `class PlaylistRepositoryImpl(dao: PlaylistDao, clock: () -> Long = System::currentTimeMillis) : PlaylistRepository`; `class HomeLocalSourceImpl(dao: HomeLocalDao) : HomeLocalSource`; `class SearchHistoryRepositoryImpl(dao: SearchHistoryDao) : SearchHistoryRepository`; `AriaDatabase.libraryDao()`, `playlistDao()`, `homeLocalDao()`, `searchHistoryDao()`. `PlayEventRepositoryImpl.record` now also stores artists (names) so history rows show artists.

**Acceptance Criteria:**
- Like sets `inLibrary`; unlike keeps it; `setInLibrary(false)` clears the like; a play alone never appears in Library Songs but appears in `recentlyPlayed` and, after enough plays, `quickPicks`.
- Sorting (`RECENTLY_ADDED`, `TITLE`) and the text filter (title or artist, `%` and `_` treated literally) run in SQL; list results are slim (no embedded relations); 1 500 library songs query returns correctly.
- Playlists: create, rename, delete (entries cascade), add tracks (songs upserted), duplicates allowed, `reorder` rewrites positions in one transaction and rejects a mismatching id set without changing anything.
- Saved albums need `inLibrary != null`; followed artists need `bookmarkedAt != null`.
- Search history: newest first, re-adding moves a query to the top, capped at 50, clearable.

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aria.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {
    private lateinit var db: AriaDatabase
    private lateinit var repo: LibraryRepositoryImpl
    private var now = 1_000L
    private fun t(n: Int, title: String = "Song $n", artist: String = "Artist $n") =
        Track(TrackId.youtube("track%06d".format(n)), title, listOf(artist), 200_000L, "https://t/$n")

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AriaDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = LibraryRepositoryImpl(db.libraryDao()) { now++ }
    }
    @After fun close() = db.close()

    private suspend fun songs(q: String = "", s: LibrarySort = LibrarySort.RECENTLY_ADDED) = repo.songs(q, s).first()

    @Test fun likeAddsToLibraryAndUnlikeKeepsIt() = runBlocking {
        repo.setLiked(t(1), true)
        assertEquals(listOf(true), songs().map { it.liked })
        assertTrue(repo.isInLibrary(t(1).id).first())
        repo.setLiked(t(1), false)
        assertEquals(listOf(false), songs().map { it.liked })
    }

    @Test fun removingFromLibraryClearsTheLike() = runBlocking {
        repo.setLiked(t(1), true); repo.setInLibrary(t(1), false)
        assertTrue(songs().isEmpty()); assertFalse(repo.isLiked(t(1).id).first())
    }

    @Test fun addToLibraryDoesNotLike() = runBlocking {
        repo.setInLibrary(t(1), true)
        assertEquals(listOf(false), songs().map { it.liked })
    }

    @Test fun playsAloneAreNotInTheLibrary() = runBlocking {
        PlayEventRepositoryImpl(db.playEventDao()).record(t(1), 5_000L, 40_000L)
        assertTrue(songs().isEmpty())
    }

    @Test fun artistsRoundTripInOrder() = runBlocking {
        repo.setInLibrary(Track(TrackId.youtube("track000001"), "Duet", listOf("B", "A"), 1_000L, null), true)
        assertEquals(listOf("B", "A"), songs().single().track.artists)
    }

    @Test fun sortAndFilterRunInSql() = runBlocking {
        repo.setInLibrary(t(1, "Banana", "Zed"), true); repo.setInLibrary(t(2, "apple", "Yan"), true); repo.setInLibrary(t(3, "Cherry", "Xi"), true)
        assertEquals(listOf("Cherry", "apple", "Banana"), songs().map { it.track.title })
        assertEquals(listOf("apple", "Banana", "Cherry"), songs(s = LibrarySort.TITLE).map { it.track.title })
        assertEquals(listOf("apple"), songs("PPL").map { it.track.title })
        assertEquals(listOf("Banana"), songs("zed").map { it.track.title })
    }

    @Test fun filterTreatsPercentAndUnderscoreLiterally() = runBlocking {
        repo.setInLibrary(t(1, "100% Pure"), true); repo.setInLibrary(t(2, "Other"), true)
        assertEquals(listOf("100% Pure"), songs("%").map { it.track.title })
        assertTrue(songs("_").isEmpty())
    }

    @Test fun largeLibraryQueries() = runBlocking {
        for (i in 1..1_500) repo.setInLibrary(t(i), true)
        assertEquals(1_500, songs().size)
        assertEquals("Song 1", songs(s = LibrarySort.TITLE).first().track.title)
    }

    @Test fun albumsNeedInLibraryAndArtistsNeedABookmark() = runBlocking {
        val album = AlbumRef("MPREb_1", "Alb", listOf("Art"), 2020, "https://a")
        repo.setAlbumSaved(album, true)
        assertEquals(listOf(album.copy(thumbnailUrl = "https://a")), repo.albums("", LibrarySort.TITLE).first())
        assertTrue(repo.isAlbumSaved("MPREb_1").first())
        repo.setAlbumSaved(album, false); assertTrue(repo.albums("", LibrarySort.TITLE).first().isEmpty())
        val artist = ArtistRef("UC1", "Singer", null)
        repo.setArtistFollowed(artist, true); assertEquals(listOf(artist), repo.artists("", LibrarySort.TITLE).first())
        repo.setArtistFollowed(artist, false); assertTrue(repo.artists("", LibrarySort.TITLE).first().isEmpty())
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aria.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryTest {
    private lateinit var db: AriaDatabase
    private lateinit var repo: PlaylistRepositoryImpl
    private fun t(n: Int) = Track(TrackId.youtube("track%06d".format(n)), "Song $n", listOf("A"), 1_000L, "https://t/$n")

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AriaDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = PlaylistRepositoryImpl(db.playlistDao())
    }
    @After fun close() = db.close()

    private suspend fun titles(id: String) = repo.tracks(id).first().map { it.track.title }

    @Test fun createRenameListAndCount() = runBlocking {
        val id = repo.create("Mine"); repo.addTracks(id, listOf(t(1), t(2)))
        repo.rename(id, "Renamed")
        val ref = repo.playlists("", LibrarySort.TITLE).first().single()
        assertEquals("Renamed", ref.title); assertEquals(2, ref.trackCount); assertEquals("https://t/1", ref.thumbnailUrl)
        assertEquals("Renamed", repo.playlist(id).first()?.title)
    }

    @Test fun duplicatesAreAllowedAndOrderIsInsertionOrder() = runBlocking {
        val id = repo.create("P"); repo.addTracks(id, listOf(t(1), t(2))); repo.addTracks(id, listOf(t(1)))
        assertEquals(listOf("Song 1", "Song 2", "Song 1"), titles(id))
    }

    @Test fun reorderRewritesPositions() = runBlocking {
        val id = repo.create("P"); repo.addTracks(id, listOf(t(1), t(2), t(3)))
        val ids = repo.tracks(id).first().map { it.entryId }
        repo.reorder(id, listOf(ids[2], ids[0], ids[1]))
        assertEquals(listOf("Song 3", "Song 1", "Song 2"), titles(id))
        repo.addTracks(id, listOf(t(4)))
        assertEquals("Song 4", titles(id).last())
    }

    @Test fun reorderWithWrongIdsIsRejectedAndChangesNothing() = runBlocking {
        val id = repo.create("P"); repo.addTracks(id, listOf(t(1), t(2)))
        val ids = repo.tracks(id).first().map { it.entryId }
        try { repo.reorder(id, listOf(ids[1], 9_999)); fail("expected IllegalArgumentException") } catch (_: IllegalArgumentException) {}
        try { repo.reorder(id, listOf(ids[1])); fail("expected IllegalArgumentException") } catch (_: IllegalArgumentException) {}
        assertEquals(listOf("Song 1", "Song 2"), titles(id))
    }

    @Test fun removeEntryAndDeleteCascade() = runBlocking {
        val id = repo.create("P"); repo.addTracks(id, listOf(t(1), t(2)))
        repo.removeEntry(id, repo.tracks(id).first().first().entryId)
        assertEquals(listOf("Song 2"), titles(id))
        repo.delete(id)
        assertTrue(repo.playlists("", LibrarySort.TITLE).first().isEmpty()); assertTrue(repo.tracks(id).first().isEmpty())
    }

    @Test fun addingTracksDoesNotPutThemInTheLibrary() = runBlocking {
        val id = repo.create("P"); repo.addTracks(id, listOf(t(1)))
        assertTrue(LibraryRepositoryImpl(db.libraryDao()).songs("", LibrarySort.TITLE).first().isEmpty())
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aria.domain.Track
import app.aria.domain.TrackId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeLocalSourceTest {
    private lateinit var db: AriaDatabase
    private lateinit var plays: PlayEventRepositoryImpl
    private lateinit var home: HomeLocalSourceImpl
    private fun t(n: Int) = Track(TrackId.youtube("track%06d".format(n)), "Song $n", listOf("Artist $n"), 1_000L, null)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AriaDatabase::class.java)
            .allowMainThreadQueries().build()
        plays = PlayEventRepositoryImpl(db.playEventDao()); home = HomeLocalSourceImpl(db.homeLocalDao())
    }
    @After fun close() = db.close()

    @Test fun emptyHistoryGivesEmptySections() = runBlocking {
        assertTrue(home.quickPicks(10).first().isEmpty()); assertTrue(home.recentlyPlayed(10).first().isEmpty())
    }

    @Test fun recentlyPlayedIsNewestFirstWithArtistsAndDistinct() = runBlocking {
        plays.record(t(1), 1_000, 40_000); plays.record(t(2), 2_000, 40_000); plays.record(t(1), 3_000, 40_000)
        val r = home.recentlyPlayed(10).first()
        assertEquals(listOf("Song 1", "Song 2"), r.map { it.title }); assertEquals(listOf("Artist 1"), r.first().artists)
        assertEquals(1, home.recentlyPlayed(1).first().size)
    }

    @Test fun quickPicksRankByPlayCount() = runBlocking {
        plays.record(t(1), 1_000, 1); plays.record(t(2), 2_000, 1); plays.record(t(2), 3_000, 1)
        assertEquals(listOf("Song 2", "Song 1"), home.quickPicks(10).first().map { it.title })
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchHistoryRepositoryTest {
    private lateinit var db: AriaDatabase
    private lateinit var repo: SearchHistoryRepositoryImpl

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AriaDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = SearchHistoryRepositoryImpl(db.searchHistoryDao())
    }
    @After fun close() = db.close()

    @Test fun newestFirstAndReAddMovesToTop() = runBlocking {
        repo.add("a"); repo.add("b"); repo.add("a")
        assertEquals(listOf("a", "b"), repo.recent(10).first())
    }
    @Test fun removeAndClear() = runBlocking {
        repo.add("a"); repo.add("b"); repo.remove("a")
        assertEquals(listOf("b"), repo.recent(10).first())
        repo.clear(); assertTrue(repo.recent(10).first().isEmpty())
    }
    @Test fun capsAtFifty() = runBlocking {
        for (i in 1..60) repo.add("q$i")
        val r = repo.recent(100).first()
        assertEquals(50, r.size); assertEquals("q60", r.first()); assertEquals("q11", r.last())
    }
    @Test fun blankQueriesAreIgnored() = runBlocking { repo.add("   "); assertTrue(repo.recent(10).first().isEmpty()) }
}
```

- [ ] **Step 2:** Push; CI fails (`libraryDao` etc. missing). Expected: FAIL.
- [ ] **Step 3: Implement.** `SqlRows.kt` (shared columns, row projections and mappers):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.data.entities.ArtistEntity
import app.aria.data.entities.SongEntity
import app.aria.domain.AlbumRef
import app.aria.domain.ArtistRef
import app.aria.domain.LibrarySong
import app.aria.domain.PlaylistRef
import app.aria.domain.Track
import app.aria.domain.TrackId

/** Unit separator between joined artist names; names never contain it. */
internal const val NAME_SEP = "\u001F"

/** Slim song columns shared by every list query; artists are joined in position order. */
internal const val SONG_ROW_COLUMNS =
    "s.id AS id, s.title AS title, s.duration AS duration, s.thumbnailUrl AS thumbnailUrl, s.liked AS liked, " +
        "(SELECT group_concat(n, char(31)) FROM (SELECT a.name AS n FROM song_artist_map m " +
        "JOIN artist a ON a.id = m.artistId WHERE m.songId = s.id ORDER BY m.position)) AS artistNames"

data class SongRow(
    val id: String, val title: String, val duration: Int, val thumbnailUrl: String?, val liked: Boolean, val artistNames: String?,
)
data class AlbumRow(val id: String, val title: String, val year: Int?, val thumbnailUrl: String?, val artistNames: String?)
data class ArtistRow(val id: String, val name: String, val thumbnailUrl: String?)
data class PlaylistRow(val id: String, val name: String, val thumbnailUrl: String?, val trackCount: Int)
data class PlaylistEntryRow(
    val entryId: Int, val id: String, val title: String, val duration: Int, val thumbnailUrl: String?, val liked: Boolean, val artistNames: String?,
)

internal fun String?.splitNames(): List<String> = this?.split(NAME_SEP)?.filter { it.isNotBlank() }.orEmpty()

internal fun SongRow.toTrack(): Track? = runCatching {
    Track(TrackId.parse(id), title, artistNames.splitNames(), duration.takeIf { it >= 0 }?.let { it * 1000L }, thumbnailUrl)
}.getOrNull()

internal fun SongRow.toLibrarySong(): LibrarySong? = toTrack()?.let { LibrarySong(it, liked) }
internal fun AlbumRow.toRef() = AlbumRef(id, title, artistNames.splitNames(), year, thumbnailUrl)
internal fun ArtistRow.toRef() = ArtistRef(id, name, thumbnailUrl)
internal fun PlaylistRow.toRef() = PlaylistRef(id, name, null, thumbnailUrl, trackCount)

/** A track as the rows it needs: the song and its artists (artists known only by name get a name-derived local id). */
class TrackWrite(val song: SongEntity, val artists: List<ArtistEntity>)

internal fun artistIdForName(name: String) = "LN:" + name.trim().lowercase()

internal fun Track.toWrite() = TrackWrite(
    SongEntity(id = id.raw, title = title, duration = durationMs?.let { (it / 1000).toInt() } ?: -1, thumbnailUrl = thumbnailUrl),
    artists.filter { it.isNotBlank() }.map { ArtistEntity(id = artistIdForName(it), name = it.trim()) },
)

/** Escapes LIKE wildcards; queries use ESCAPE '\'. */
internal fun String.escapeLike(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
```

`TrackWriterDao.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import app.aria.data.entities.ArtistEntity
import app.aria.data.entities.SongArtistMap
import app.aria.data.entities.SongEntity

/** Base for DAOs that upsert songs (decision 2: songs are upserted on first like, add, playlist-add or play). Existing rows are never overwritten. */
abstract class TrackWriterDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertSong(song: SongEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertArtist(artist: ArtistEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertSongArtist(map: SongArtistMap): Long

    @Transaction
    open suspend fun ensure(write: TrackWrite) {
        insertSong(write.song)
        write.artists.forEachIndexed { i, a -> insertArtist(a); insertSongArtist(SongArtistMap(write.song.id, a.id, i)) }
    }
}
```

`LibraryDao.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.aria.data.entities.AlbumArtistMap
import app.aria.data.entities.AlbumEntity
import app.aria.data.entities.ArtistEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
abstract class LibraryDao : TrackWriterDao() {
    @Query(
        "SELECT $SONG_ROW_COLUMNS FROM song s WHERE s.inLibrary IS NOT NULL AND (:q = '' OR s.title LIKE '%' || :q || '%' ESCAPE '\\' " +
            "OR EXISTS (SELECT 1 FROM song_artist_map m JOIN artist a ON a.id = m.artistId WHERE m.songId = s.id AND a.name LIKE '%' || :q || '%' ESCAPE '\\')) " +
            "ORDER BY CASE WHEN :byTitle = 1 THEN LOWER(s.title) END ASC, s.inLibrary DESC",
    )
    abstract fun songs(q: String, byTitle: Int): Flow<List<SongRow>>

    @Query(
        "SELECT al.id AS id, al.title AS title, al.year AS year, al.thumbnailUrl AS thumbnailUrl, " +
            "(SELECT group_concat(n, char(31)) FROM (SELECT a.name AS n FROM album_artist_map m JOIN artist a ON a.id = m.artistId " +
            "WHERE m.albumId = al.id ORDER BY m.`order`)) AS artistNames FROM album al " +
            "WHERE al.inLibrary IS NOT NULL AND (:q = '' OR al.title LIKE '%' || :q || '%' ESCAPE '\\') " +
            "ORDER BY CASE WHEN :byTitle = 1 THEN LOWER(al.title) END ASC, al.inLibrary DESC",
    )
    abstract fun albums(q: String, byTitle: Int): Flow<List<AlbumRow>>

    @Query(
        "SELECT a.id AS id, a.name AS name, a.thumbnailUrl AS thumbnailUrl FROM artist a " +
            "WHERE a.bookmarkedAt IS NOT NULL AND (:q = '' OR a.name LIKE '%' || :q || '%' ESCAPE '\\') " +
            "ORDER BY CASE WHEN :byTitle = 1 THEN LOWER(a.name) END ASC, a.bookmarkedAt DESC",
    )
    abstract fun artists(q: String, byTitle: Int): Flow<List<ArtistRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM song WHERE id = :id AND liked = 1)") abstract fun isLiked(id: String): Flow<Boolean>
    @Query("SELECT EXISTS(SELECT 1 FROM song WHERE id = :id AND inLibrary IS NOT NULL)") abstract fun isInLibrary(id: String): Flow<Boolean>
    @Query("SELECT EXISTS(SELECT 1 FROM album WHERE id = :id AND inLibrary IS NOT NULL)") abstract fun isAlbumSaved(id: String): Flow<Boolean>
    @Query("SELECT EXISTS(SELECT 1 FROM artist WHERE id = :id AND bookmarkedAt IS NOT NULL)") abstract fun isArtistFollowed(id: String): Flow<Boolean>

    @Query("UPDATE song SET liked = 1, likedDate = :now, inLibrary = COALESCE(inLibrary, :now) WHERE id = :id")
    protected abstract suspend fun like(id: String, now: LocalDateTime)
    @Query("UPDATE song SET liked = 0, likedDate = NULL WHERE id = :id") protected abstract suspend fun unlike(id: String)
    @Query("UPDATE song SET inLibrary = COALESCE(inLibrary, :now) WHERE id = :id") protected abstract suspend fun addToLibrary(id: String, now: LocalDateTime)
    @Query("UPDATE song SET inLibrary = NULL, liked = 0, likedDate = NULL WHERE id = :id") protected abstract suspend fun removeFromLibrary(id: String)

    @Transaction open suspend fun setLiked(write: TrackWrite, liked: Boolean, now: LocalDateTime) {
        ensure(write); if (liked) like(write.song.id, now) else unlike(write.song.id)
    }
    @Transaction open suspend fun setInLibrary(write: TrackWrite, inLibrary: Boolean, now: LocalDateTime) {
        ensure(write); if (inLibrary) addToLibrary(write.song.id, now) else removeFromLibrary(write.song.id)
    }

    @Upsert protected abstract suspend fun upsertAlbum(album: AlbumEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertArtistIgnore(artist: ArtistEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertAlbumArtist(map: AlbumArtistMap): Long
    @Query("UPDATE album SET inLibrary = NULL WHERE id = :id") protected abstract suspend fun unsaveAlbum(id: String)

    @Transaction open suspend fun saveAlbum(album: AlbumEntity, artists: List<ArtistEntity>) {
        upsertAlbum(album)
        artists.forEachIndexed { i, a -> insertArtistIgnore(a); insertAlbumArtist(AlbumArtistMap(album.id, a.id, i)) }
    }
    @Transaction open suspend fun removeAlbum(id: String) = unsaveAlbum(id)

    @Upsert abstract suspend fun upsertArtist(artist: ArtistEntity)
    @Query("UPDATE artist SET bookmarkedAt = NULL WHERE id = :id") abstract suspend fun unfollowArtist(id: String)
}
```

  (Kotlin string templates `$SONG_ROW_COLUMNS` inside an annotation argument are allowed because the constant is `const val`. If CI rejects them, use `"..." + SONG_ROW_COLUMNS + "..."`.)

`LibraryRepositoryImpl.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.data.entities.AlbumEntity
import app.aria.data.entities.ArtistEntity
import app.aria.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class LibraryRepositoryImpl(
    private val dao: LibraryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : LibraryRepository {
    private fun now(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(clock()), ZoneOffset.UTC)
    private fun LibrarySort.flag() = if (this == LibrarySort.TITLE) 1 else 0

    override fun songs(query: String, sort: LibrarySort): Flow<List<LibrarySong>> =
        dao.songs(query.escapeLike(), sort.flag()).map { rows -> rows.mapNotNull { it.toLibrarySong() } }
    override fun albums(query: String, sort: LibrarySort): Flow<List<AlbumRef>> =
        dao.albums(query.escapeLike(), sort.flag()).map { rows -> rows.map { it.toRef() } }
    override fun artists(query: String, sort: LibrarySort): Flow<List<ArtistRef>> =
        dao.artists(query.escapeLike(), sort.flag()).map { rows -> rows.map { it.toRef() } }
    override fun isLiked(id: TrackId) = dao.isLiked(id.raw)
    override fun isInLibrary(id: TrackId) = dao.isInLibrary(id.raw)
    override fun isAlbumSaved(id: String) = dao.isAlbumSaved(id)
    override fun isArtistFollowed(id: String) = dao.isArtistFollowed(id)

    override suspend fun setLiked(track: Track, liked: Boolean) = dao.setLiked(track.toWrite(), liked, now())
    override suspend fun setInLibrary(track: Track, inLibrary: Boolean) = dao.setInLibrary(track.toWrite(), inLibrary, now())

    override suspend fun setAlbumSaved(album: AlbumRef, saved: Boolean) {
        if (!saved) { dao.removeAlbum(album.id); return }
        val entity = AlbumEntity(
            id = album.id, title = album.title, year = album.year, thumbnailUrl = album.thumbnailUrl,
            songCount = 0, duration = 0, inLibrary = now(),
        )
        dao.saveAlbum(entity, album.artists.filter { it.isNotBlank() }.map { ArtistEntity(id = artistIdForName(it), name = it.trim()) })
    }

    override suspend fun setArtistFollowed(artist: ArtistRef, followed: Boolean) {
        if (followed) dao.upsertArtist(ArtistEntity(id = artist.id, name = artist.name, thumbnailUrl = artist.thumbnailUrl, bookmarkedAt = now()))
        else dao.unfollowArtist(artist.id)
    }
}
```

`PlaylistDao.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.aria.data.entities.PlaylistEntity
import app.aria.data.entities.PlaylistSongMap
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

private const val PLAYLIST_COLUMNS =
    "p.id AS id, p.name AS name, COALESCE(p.thumbnailUrl, (SELECT s.thumbnailUrl FROM playlist_song_map m JOIN song s ON s.id = m.songId " +
        "WHERE m.playlistId = p.id AND s.thumbnailUrl IS NOT NULL ORDER BY m.position LIMIT 1)) AS thumbnailUrl, " +
        "(SELECT COUNT(*) FROM playlist_song_map m WHERE m.playlistId = p.id) AS trackCount"

@Dao
abstract class PlaylistDao : TrackWriterDao() {
    @Query(
        "SELECT $PLAYLIST_COLUMNS FROM playlist p WHERE p.isLocal = 1 AND (:q = '' OR p.name LIKE '%' || :q || '%' ESCAPE '\\') " +
            "ORDER BY CASE WHEN :byTitle = 1 THEN LOWER(p.name) END ASC, p.createdAt DESC",
    )
    abstract fun playlists(q: String, byTitle: Int): Flow<List<PlaylistRow>>

    @Query("SELECT $PLAYLIST_COLUMNS FROM playlist p WHERE p.id = :id AND p.isLocal = 1") abstract fun playlist(id: String): Flow<PlaylistRow?>

    @Query("SELECT pm.id AS entryId, $SONG_ROW_COLUMNS FROM playlist_song_map pm JOIN song s ON s.id = pm.songId WHERE pm.playlistId = :id ORDER BY pm.position")
    abstract fun entries(id: String): Flow<List<PlaylistEntryRow>>

    @Insert abstract suspend fun insertPlaylist(playlist: PlaylistEntity)
    @Query("UPDATE playlist SET name = :name, lastUpdateTime = :now WHERE id = :id AND isLocal = 1") abstract suspend fun rename(id: String, name: String, now: LocalDateTime)
    @Query("DELETE FROM playlist WHERE id = :id AND isLocal = 1") abstract suspend fun delete(id: String)
    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_song_map WHERE playlistId = :id") protected abstract suspend fun maxPosition(id: String): Int
    @Insert protected abstract suspend fun insertEntry(entry: PlaylistSongMap): Long
    @Query("DELETE FROM playlist_song_map WHERE id = :entryId AND playlistId = :playlistId") abstract suspend fun deleteEntry(playlistId: String, entryId: Int)
    @Query("SELECT id FROM playlist_song_map WHERE playlistId = :id ORDER BY position") protected abstract suspend fun entryIds(id: String): List<Int>
    @Query("UPDATE playlist_song_map SET position = :position WHERE id = :entryId AND playlistId = :playlistId")
    protected abstract suspend fun setPosition(playlistId: String, entryId: Int, position: Int)

    @Transaction
    open suspend fun addTracks(playlistId: String, writes: List<TrackWrite>) {
        var pos = maxPosition(playlistId) + 1
        for (w in writes) { ensure(w); insertEntry(PlaylistSongMap(playlistId = playlistId, songId = w.song.id, position = pos++)) }
    }

    /** Throws before writing anything when [ordered] is not exactly the current entry ids; the whole rewrite is one transaction. */
    @Transaction
    open suspend fun reorder(playlistId: String, ordered: List<Int>) {
        val current = entryIds(playlistId)
        require(ordered.size == current.size && ordered.sorted() == current.sorted()) { "entry ids do not match the playlist" }
        ordered.forEachIndexed { i, id -> setPosition(playlistId, id, i) }
    }
}
```

`PlaylistRepositoryImpl.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.data.entities.PlaylistEntity
import app.aria.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class PlaylistRepositoryImpl(
    private val dao: PlaylistDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : PlaylistRepository {
    private fun now(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(clock()), ZoneOffset.UTC)

    override fun playlists(query: String, sort: LibrarySort): Flow<List<PlaylistRef>> =
        dao.playlists(query.escapeLike(), if (sort == LibrarySort.TITLE) 1 else 0).map { rows -> rows.map { it.toRef() } }
    override fun playlist(id: String): Flow<PlaylistRef?> = dao.playlist(id).map { it?.toRef() }
    override fun tracks(playlistId: String): Flow<List<PlaylistTrackRow>> = dao.entries(playlistId).map { rows ->
        rows.mapNotNull { r ->
            SongRow(r.id, r.title, r.duration, r.thumbnailUrl, r.liked, r.artistNames).toTrack()?.let { PlaylistTrackRow(r.entryId, it) }
        }
    }

    override suspend fun create(name: String): String {
        val entity = PlaylistEntity(name = name.trim(), createdAt = now(), lastUpdateTime = now(), isEditable = true, isLocal = true)
        dao.insertPlaylist(entity); return entity.id
    }
    override suspend fun rename(id: String, name: String) = dao.rename(id, name.trim(), now())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun addTracks(id: String, tracks: List<Track>) = dao.addTracks(id, tracks.map { it.toWrite() })
    override suspend fun removeEntry(playlistId: String, entryId: Int) = dao.deleteEntry(playlistId, entryId)
    override suspend fun reorder(playlistId: String, orderedEntryIds: List<Int>) = dao.reorder(playlistId, orderedEntryIds)
}
```

`HomeLocalDao.kt` and `HomeLocalSourceImpl.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HomeLocalDao {
    @Query(
        "SELECT $SONG_ROW_COLUMNS FROM song s JOIN (SELECT songId, COUNT(*) AS plays, MAX(timestamp) AS lastAt FROM event GROUP BY songId) e " +
            "ON e.songId = s.id WHERE s.hideFromQuickPicks = 0 ORDER BY e.plays DESC, e.lastAt DESC LIMIT :limit",
    )
    abstract fun quickPicks(limit: Int): Flow<List<SongRow>>

    @Query(
        "SELECT $SONG_ROW_COLUMNS FROM song s JOIN (SELECT songId, MAX(timestamp) AS lastAt FROM event GROUP BY songId) e " +
            "ON e.songId = s.id ORDER BY e.lastAt DESC LIMIT :limit",
    )
    abstract fun recentlyPlayed(limit: Int): Flow<List<SongRow>>
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.domain.HomeLocalSource
import app.aria.domain.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HomeLocalSourceImpl(private val dao: HomeLocalDao) : HomeLocalSource {
    override fun quickPicks(limit: Int): Flow<List<Track>> = dao.quickPicks(limit).map { r -> r.mapNotNull { it.toTrack() } }
    override fun recentlyPlayed(limit: Int): Flow<List<Track>> = dao.recentlyPlayed(limit).map { r -> r.mapNotNull { it.toTrack() } }
}
```

`SearchHistoryDao.kt` and `SearchHistoryRepositoryImpl.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.aria.data.entities.SearchHistory
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SearchHistoryDao {
    @Query("SELECT `query` FROM search_history ORDER BY id DESC LIMIT :limit") abstract fun recent(limit: Int): Flow<List<String>>
    @Query("DELETE FROM search_history WHERE `query` = :q") abstract suspend fun delete(q: String)
    @Query("DELETE FROM search_history") abstract suspend fun clear()
    @Insert protected abstract suspend fun insert(row: SearchHistory)
    @Query("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY id DESC LIMIT 50)") protected abstract suspend fun trim()

    @Transaction open suspend fun add(q: String) { delete(q); insert(SearchHistory(query = q)); trim() }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.domain.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow

class SearchHistoryRepositoryImpl(private val dao: SearchHistoryDao) : SearchHistoryRepository {
    override fun recent(limit: Int): Flow<List<String>> = dao.recent(limit)
    override suspend fun add(query: String) { val q = query.trim(); if (q.isNotEmpty()) dao.add(q) }
    override suspend fun remove(query: String) = dao.delete(query)
    override suspend fun clear() = dao.clear()
}
```

  `PlayEventDao.kt`: replace `record` and add the two inserts:

```kotlin
    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertArtist(artist: ArtistEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertSongArtist(map: SongArtistMap): Long

    @Transaction
    open suspend fun record(write: TrackWrite, event: Event) {
        insertSong(write.song)
        write.artists.forEachIndexed { i, a -> insertArtist(a); insertSongArtist(SongArtistMap(write.song.id, a.id, i)) }
        insertEvent(event)
    }
```

  (add imports for `ArtistEntity`, `SongArtistMap`; remove the old two-argument `record`.) `PlayEventRepositoryImpl.record` becomes `dao.record(track.toWrite(), Event(songId = track.id.raw, timestamp = at, playTime = playTimeMs))` and drops the local `SongEntity` construction and its import. `AriaDatabase`: add `abstract fun libraryDao(): LibraryDao`, `playlistDao()`, `homeLocalDao()`, `searchHistoryDao()`.
- [ ] **Step 4:** Push; `:data:test` green (the schema is unchanged: DAOs only; the existing `PlayEventRepositoryTest` still passes). If Room rejects the DAO base class (`TrackWriterDao` not annotated), annotate nothing else but move the three `@Insert` methods into each DAO (duplicate them) and report it. **Commit** `feat(data): library, playlist, home-local and search-history repositories`.

---

### Task 5: DataStore `SettingsRepository`

Slice 1, decision 5 (storage part). Keeps `datastore/` out of Android backup like every other private store.

**Files:**
- Create: `data/src/main/kotlin/app/aria/data/DataStoreSettingsRepository.kt`
- Modify: `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, `scripts/check_backup_rules.py`, `docs/data-flow.md`
- Test: `data/src/test/kotlin/app/aria/data/DataStoreSettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: `AppSettings`, `SettingsRepository`; `androidx.datastore:datastore-preferences` (Task 0).
- Produces: `class DataStoreSettingsRepository(store: DataStore<Preferences>) : SettingsRepository`; `fun createSettingsDataStore(file: File, scope: CoroutineScope): DataStore<Preferences>` (replaces a corrupt file by defaults).

**Acceptance Criteria:**
- Defaults when nothing is stored; `update` persists across a new DataStore on the same file; out-of-range stored values come back normalized (10-120 s); a corrupt file yields defaults instead of a crash.
- `check_backup_rules.py` requires `("file", "datastore/")` and both XML files exclude it; `test_check_backup_rules.py` still passes (it removes each exclusion in turn and expects failure).

- [ ] **Step 1: Failing test:**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.domain.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun open(file: java.io.File, scope: CoroutineScope) = DataStoreSettingsRepository(createSettingsDataStore(file, scope))

    @Test fun defaultsWhenNothingIsStored() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        assertEquals(AppSettings(), open(tmp.newFile("a.preferences_pb").also { it.delete() }, scope).settings.first())
        scope.cancel()
    }

    @Test fun updatesPersistAcrossInstances() = runBlocking {
        val file = tmp.newFile("b.preferences_pb").also { it.delete() }
        val s1 = CoroutineScope(Job() + Dispatchers.IO)
        open(file, s1).update { it.copy(historyThresholdMs = 45_000, endlessRadio = false, skipSilence = true, normalizeLoudness = false, sleepTimerDefaultMinutes = 30, onboardingDone = true) }
        s1.cancel(); s1.coroutineContext[Job]!!.join()
        val s2 = CoroutineScope(Job() + Dispatchers.IO)
        assertEquals(
            AppSettings(45_000, endlessRadio = false, skipSilence = true, normalizeLoudness = false, sleepTimerDefaultMinutes = 30, onboardingDone = true),
            open(file, s2).settings.first(),
        )
        s2.cancel()
    }

    @Test fun updatesAreClamped() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        val repo = open(tmp.newFile("c.preferences_pb").also { it.delete() }, scope)
        repo.update { it.copy(historyThresholdMs = 1) }
        assertEquals(10_000L, repo.settings.first().historyThresholdMs)
        repo.update { it.copy(historyThresholdMs = 99_999_999) }
        assertEquals(120_000L, repo.settings.first().historyThresholdMs)
        scope.cancel()
    }

    @Test fun corruptFileGivesDefaults() = runBlocking {
        val file = tmp.newFile("d.preferences_pb").also { it.writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        assertEquals(AppSettings(), open(file, scope).settings.first())
        scope.cancel()
    }
}
```

- [ ] **Step 2:** Push; CI fails (unresolved). Expected: FAIL.
- [ ] **Step 3: Implement.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import app.aria.domain.AppSettings
import app.aria.domain.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException

/** A corrupt settings file is replaced by defaults; settings are never worth a crash. */
fun createSettingsDataStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { file },
    )

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private object Keys {
        val historyThresholdMs = longPreferencesKey("history_threshold_ms")
        val endlessRadio = booleanPreferencesKey("endless_radio")
        val skipSilence = booleanPreferencesKey("skip_silence")
        val normalizeLoudness = booleanPreferencesKey("normalize_loudness")
        val sleepDefaultMinutes = intPreferencesKey("sleep_default_minutes")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    override val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val next = transform(p.toSettings()).normalized()
            p[Keys.historyThresholdMs] = next.historyThresholdMs
            p[Keys.endlessRadio] = next.endlessRadio
            p[Keys.skipSilence] = next.skipSilence
            p[Keys.normalizeLoudness] = next.normalizeLoudness
            p[Keys.sleepDefaultMinutes] = next.sleepTimerDefaultMinutes
            p[Keys.onboardingDone] = next.onboardingDone
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            historyThresholdMs = this[Keys.historyThresholdMs] ?: d.historyThresholdMs,
            endlessRadio = this[Keys.endlessRadio] ?: d.endlessRadio,
            skipSilence = this[Keys.skipSilence] ?: d.skipSilence,
            normalizeLoudness = this[Keys.normalizeLoudness] ?: d.normalizeLoudness,
            sleepTimerDefaultMinutes = this[Keys.sleepDefaultMinutes] ?: d.sleepTimerDefaultMinutes,
            onboardingDone = this[Keys.onboardingDone] ?: d.onboardingDone,
        ).normalized()
    }
}
```

  Backup: add `<exclude domain="file" path="datastore/" />` to `backup_rules.xml` (`<full-backup-content>`) and to **both** `<cloud-backup>` and `<device-transfer>` in `data_extraction_rules.xml`; in `scripts/check_backup_rules.py` add to `REQUIRED`: `("file", "datastore/"),             # app settings (DataStore)`. `docs/data-flow.md`: in the "Android backup and device transfer" paragraph add "the settings store (`datastore/`)" to the list of excluded items.
- [ ] **Step 4:** Push; `:data:test`, `pytest scripts` (backup rules self-test) and the build green. **Commit** `feat(data): DataStore settings repository; exclude datastore from backup`.

---

### Task 6: `:innertube` browse adapters, synthetic fixtures, capture script, stale-fixture test, release gate

Slice 1, decision 1. Phase A is adapters over the already-ported `YouTube.home/album/artist/playlist/searchSummary/searchSuggestions`; no new network code. Synthetic fixtures (Kotlin-built page objects) test mapping logic and null-safety only; the sandbox cannot reach YouTube Music.

**Files:**
- Create: `innertube/src/main/kotlin/app/aria/innertube/adapter/{BrowseMapping,YouTubeBrowseSource,YouTubeSearchRepository}.kt`, `innertube/src/test/kotlin/app/aria/innertube/adapter/{BrowseMappingTest,YouTubeBrowseSourceTest,YouTubeSearchRepositoryTest}.kt`, `innertube/src/test/kotlin/app/aria/innertube/fixtures/{FixtureCaptureTest,FixtureFreshness,FixtureFreshnessTest,LiveFixtureReplayTest}.kt`, `scripts/capture-innertube-fixtures`
- Modify: `innertube/build.gradle.kts`, `docs/release-gates.md`, `docs/manual-device-checklist.md`, `docs/providers/youtube-music.md`, `docs/data-flow.md`, `PRIVACY.md`

**Interfaces:**
- Consumes: `YouTube.*`, page classes (see Task 0 table), `SongItem.toTrack()` (existing), `BrowseSource`, `SearchRepository` and the domain models.
- Produces:
  - `class YouTubeBrowseSource(home: suspend () -> Result<HomePage> = { YouTube.home() }, album: suspend (String) -> Result<AlbumPage> = { YouTube.album(it) }, artist: suspend (String) -> Result<ArtistPage> = { YouTube.artist(it) }, playlist: suspend (String) -> Result<PlaylistPage> = { YouTube.playlist(it) }, playlistMore: suspend (String) -> Result<PlaylistContinuationPage> = { YouTube.playlistContinuation(it) }) : BrowseSource`
  - `class YouTubeSearchRepository(searchSummary: suspend (String) -> Result<SearchSummaryPage> = { YouTube.searchSummary(it) }, suggestions: suspend (String) -> Result<SearchSuggestions> = { YouTube.searchSuggestions(it) }) : SearchRepository`
  - internal: `SongItem.toTrackOrNull()`, `YTItem.toBrowseItem()`, `HomePage.toShelfStates(map)`, `AlbumPage.toDetail()`, `ArtistPage.toDetail()`, `PlaylistPage.toDetail()`, `List<YTItem>.toSearchResults()`, `object FixtureFreshness`.

**Acceptance Criteria:**
- A song with an invalid video ID is dropped, not thrown; podcasts and episodes are not mapped; a shelf whose items all fail to map becomes `ShelfState.Failed(title)` while its neighbours stay `Ready`; a shelf with only unmappable (null) items is dropped; empty pages map to empty lists without exceptions.
- `YouTubeBrowseSource` returns failures from the lambdas as failures (never throws); a failed album does not affect anything else.
- Search results are grouped by item type, not by the localized section title.
- `scripts/capture-innertube-fixtures` (maintainer-run, needs network) runs `FixtureCaptureTest`, which skips unless `-PcaptureFixtures=true`; it writes sanitized summaries (query strings stripped, no cookies or visitor data) plus `manifest.json` into `innertube/src/test/resources/fixtures/`. `FixtureFreshnessTest` fails when a manifest exists and is older than 60 days, and passes (with a visible message) when none exists yet. `LiveFixtureReplayTest` checks invariants of whatever fixtures exist.
- `docs/release-gates.md` has the unchecked gate "Parsers verified against live responses" and the manual checklist has matching items.

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.BrowseItem
import app.aria.domain.ShelfState
import app.aria.innertube.models.*
import app.aria.innertube.pages.*
import org.junit.Assert.*
import org.junit.Test

class BrowseMappingTest {
    private fun song(id: String, title: String = id) = SongItem(id = id, title = title, artists = listOf(Artist("A", "UC1")), thumbnail = "https://i.ytimg.com/x.jpg")
    private fun album(n: Int) = AlbumItem(browseId = "MPREb_$n", playlistId = "OLAK$n", title = "Alb $n", artists = listOf(Artist("Ar", null)), year = 2021, thumbnail = "https://lh3.googleusercontent.com/a")
    private fun artist(n: Int) = ArtistItem(id = "UC$n", title = "Art $n", thumbnail = null, shuffleEndpoint = null, radioEndpoint = null)
    private fun playlist(id: String, count: String?, podcast: Boolean = false) = PlaylistItem(
        id = id, title = "PL", author = Artist("Me", null), songCountText = count, thumbnail = null,
        playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null, isPodcast = podcast,
    )

    @Test fun invalidVideoIdIsDroppedNotThrown() {
        assertNull(song("short").toTrackOrNull())
        assertEquals("dQw4w9WgXcQ", song("dQw4w9WgXcQ").toTrackOrNull()?.id?.raw)
    }

    @Test fun podcastsAndEpisodesAreNotMapped() {
        assertNull(playlist("PLx", "3 songs", podcast = true).toBrowseItem())
        assertNull(song("dQw4w9WgXcQ").copy(isEpisode = true).toBrowseItem())
        assertNull(PodcastItem("PC", "P", null, null, null, null, null).toBrowseItem())
    }

    @Test fun playlistCountIsParsedFromDisplayText() {
        assertEquals(12, playlist("PL1", "12 songs").toRef().trackCount)
        assertEquals(1234, playlist("PL1", "1,234 songs").toRef().trackCount)
        assertNull(playlist("PL1", null).toRef().trackCount)
        assertNull(playlist("PL1", "many").toRef().trackCount)
    }

    @Test fun homeShelvesFailIndependently() {
        val page = HomePage(
            chips = null, continuation = null,
            sections = listOf(
                HomePage.Section("Good", "Label", null, null, listOf(song("dQw4w9WgXcQ"), album(1), artist(1), song("bad"))),
                HomePage.Section("Bad", null, null, null, listOf(album(2), album(3))),
                HomePage.Section("Podcasts only", null, null, null, listOf(PodcastItem("PC", "P", null, null, null, null, null))),
                HomePage.Section("Empty", null, null, null, emptyList()),
            ),
        )
        val states = page.toShelfStates { item -> if ((item as? AlbumItem)?.browseId in setOf("MPREb_2", "MPREb_3")) error("boom") else item.toBrowseItem() }
        assertEquals(2, states.size)
        val good = (states[0] as ShelfState.Ready).shelf
        assertEquals("Good", good.title); assertEquals("Label", good.subtitle)
        assertEquals(listOf("song", "album", "artist"), good.items.map { it.key.substringBefore(':') })
        assertEquals(ShelfState.Failed("Bad"), states[1])
    }

    @Test fun albumPageMapsAndDropsBadTracks() {
        val d = AlbumPage(album(1), listOf(song("dQw4w9WgXcQ"), song("nope")), listOf(album(2))).toDetail()
        assertEquals("MPREb_1", d.album.id); assertEquals(listOf("Ar"), d.album.artists); assertEquals(1, d.tracks.size)
        assertEquals(listOf("MPREb_2"), d.otherVersions.map { it.id })
    }

    @Test fun artistPageDropsEmptySectionsAndKeepsOrder() {
        val d = ArtistPage(
            artist(1), listOf(ArtistSection("Songs", listOf(song("dQw4w9WgXcQ")), null), ArtistSection("Nothing", emptyList(), null), ArtistSection("Albums", listOf(album(1)), null)),
            "desc", "1M subscribers",
        ).toDetail()
        assertEquals(listOf("Songs", "Albums"), d.shelves.map { it.title }); assertEquals("desc", d.description)
        assertEquals(1, d.topTracks.size)
    }

    @Test fun playlistPageUsesTheSongsContinuation() {
        val d = PlaylistPage(playlist("PL1", "2 songs"), listOf(song("dQw4w9WgXcQ")), songsContinuation = "tok", continuation = "other").toDetail()
        assertEquals("tok", d.continuation); assertEquals("Me", d.playlist.subtitle)
        assertEquals("other", PlaylistPage(playlist("PL1", null), emptyList(), null, "other").toDetail().continuation)
    }

    @Test fun searchGroupsByTypeNotByTitle() {
        val r = listOf(song("dQw4w9WgXcQ"), song("dQw4w9WgXcQ"), album(1), artist(1), playlist("PL1", null), song("bad")).toSearchResults()
        assertEquals(1, r.songs.size); assertEquals(1, r.albums.size); assertEquals(1, r.artists.size); assertEquals(1, r.playlists.size)
    }

    @Test fun emptyPagesMapToEmptyLists() {
        assertTrue(HomePage(null, emptyList()).toShelfStates().isEmpty())
        assertTrue(emptyList<YTItem>().toSearchResults().isEmpty)
        assertTrue(AlbumPage(album(1), emptyList(), emptyList()).toDetail().tracks.isEmpty())
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.innertube.models.*
import app.aria.innertube.pages.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class YouTubeBrowseSourceTest {
    private val artist = ArtistItem(id = "UC1", title = "N", thumbnail = null, shuffleEndpoint = null, radioEndpoint = null)

    @Test fun failuresAreReturnedNotThrown() = runBlocking {
        val src = YouTubeBrowseSource(home = { error("net") }, album = { Result.failure(java.io.IOException("x")) })
        assertTrue(src.home().isFailure); assertTrue(src.album("a").isFailure)
    }

    @Test fun artistAndMoreMapThroughTheLambdas() = runBlocking {
        var asked: String? = null
        val src = YouTubeBrowseSource(
            artist = { asked = it; Result.success(ArtistPage(artist, emptyList(), null, null)) },
            playlistMore = { Result.success(PlaylistContinuationPage(emptyList(), "next")) },
        )
        assertEquals("UC1", src.artist("UC1").getOrThrow().artist.id); assertEquals("UC1", asked)
        assertEquals("next", src.playlistMore("c").getOrThrow().continuation)
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.innertube.models.*
import app.aria.innertube.pages.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class YouTubeSearchRepositoryTest {
    @Test fun searchMapsAllSummariesIntoOneResult() = runBlocking {
        val song = SongItem(id = "dQw4w9WgXcQ", title = "S", artists = emptyList(), thumbnail = "t")
        val repo = YouTubeSearchRepository(searchSummary = { Result.success(SearchSummaryPage(listOf(SearchSummary("Songs", listOf(song)), SearchSummary("Top result", listOf(song))))) })
        assertEquals(1, repo.search("q").getOrThrow().songs.size)
    }
    @Test fun suggestionsReturnQueriesOnly() = runBlocking {
        val repo = YouTubeSearchRepository(suggestions = { Result.success(SearchSuggestions(listOf("a", "b"), emptyList())) })
        assertEquals(listOf("a", "b"), repo.suggestions("x").getOrThrow())
    }
    @Test fun failuresAreReturned() = runBlocking {
        assertTrue(YouTubeSearchRepository(searchSummary = { Result.failure(RuntimeException()) }).search("q").isFailure)
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.fixtures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FixtureFreshnessTest {
    @Test fun youngFixturesPass() = assertNull(FixtureFreshness.check(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 20)))
    @Test fun oldFixturesFailLoudly() {
        val msg = FixtureFreshness.check(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 8))
        assertNotNull(msg); assertEquals(true, msg!!.contains("scripts/capture-innertube-fixtures"))
    }
    @Test fun theBoundaryIsSixtyDays() {
        assertNull(FixtureFreshness.check(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 2)))
        assertNotNull(FixtureFreshness.check(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 3)))
    }
    /** Whatever is committed must be fresh. With no manifest yet (no maintainer capture) this passes; the release gate stays open. */
    @Test fun committedManifestIsFresh() {
        val manifest = java.io.File(FixtureFreshness.DIR, "manifest.json")
        if (!manifest.exists()) { println("No live fixtures captured yet; release gate 'parsers verified against live responses' is open."); return }
        val date = Regex("\"capturedAt\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"").find(manifest.readText())?.groupValues?.get(1)
        assertNotNull("manifest.json has no capturedAt", date)
        assertNull(FixtureFreshness.check(LocalDate.parse(date), LocalDate.now()))
    }
}
```

- [ ] **Step 2:** Push; CI fails (unresolved). Expected: FAIL.
- [ ] **Step 3: Implement.** `BrowseMapping.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.*
import app.aria.innertube.models.*
import app.aria.innertube.pages.*

/** `toTrack()` throws on an invalid video ID; page data is untrusted, so browse mapping drops such rows instead. */
internal fun SongItem.toTrackOrNull(): Track? = runCatching { toTrack() }.getOrNull()

internal fun String?.parseCount(): Int? =
    this?.let { Regex("\\d[\\d,.]*").find(it)?.value?.filter(Char::isDigit)?.toIntOrNull() }

internal fun AlbumItem.toRef() = AlbumRef(browseId, title, artists.orEmpty().map { it.name }.filter { it.isNotBlank() }, year, thumbnail.takeIf { it.isNotBlank() })
internal fun ArtistItem.toRef() = ArtistRef(id, title, thumbnail)
internal fun PlaylistItem.toRef() = PlaylistRef(id, title, author?.name, thumbnail, songCountText.parseCount())

internal fun YTItem.toBrowseItem(): BrowseItem? = when (this) {
    is SongItem -> if (isEpisode) null else toTrackOrNull()?.let { BrowseItem.Song(it) }
    is AlbumItem -> BrowseItem.Album(toRef())
    is ArtistItem -> BrowseItem.Artist(toRef())
    is PlaylistItem -> if (isPodcast) null else BrowseItem.Playlist(toRef())
    is PodcastItem, is EpisodeItem -> null
}

/** Each section maps on its own: one that throws on every item is Failed, the others are unaffected. */
internal fun HomePage.toShelfStates(map: (YTItem) -> BrowseItem? = YTItem::toBrowseItem): List<ShelfState> =
    sections.mapNotNull { s ->
        val results = s.items.map { runCatching { map(it) } }
        val items = results.mapNotNull { it.getOrNull() }
        when {
            items.isNotEmpty() -> ShelfState.Ready(Shelf(s.title, s.label, items))
            results.any { it.isFailure } -> ShelfState.Failed(s.title)
            else -> null
        }
    }

internal fun AlbumPage.toDetail() = AlbumDetail(album.toRef(), songs.mapNotNull { it.toTrackOrNull() }, otherVersions.map { it.toRef() })

internal fun ArtistPage.toDetail() = ArtistDetail(
    artist = artist.toRef(), description = description, subscriberText = subscriberCountText,
    shelves = sections.mapNotNull { sec ->
        val items = sec.items.mapNotNull { runCatching { it.toBrowseItem() }.getOrNull() }
        if (items.isEmpty()) null else Shelf(sec.title, null, items)
    },
)

internal fun PlaylistPage.toDetail() = PlaylistDetail(playlist.toRef(), songs.mapNotNull { it.toTrackOrNull() }, songsContinuation ?: continuation)

internal fun List<YTItem>.toSearchResults() = SearchResults(
    songs = filterIsInstance<SongItem>().filterNot { it.isEpisode }.mapNotNull { it.toTrackOrNull() }.distinctBy { it.id },
    albums = filterIsInstance<AlbumItem>().map { it.toRef() }.distinctBy { it.id },
    artists = filterIsInstance<ArtistItem>().map { it.toRef() }.distinctBy { it.id },
    playlists = filterIsInstance<PlaylistItem>().filterNot { it.isPodcast }.map { it.toRef() }.distinctBy { it.id },
)
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.*
import app.aria.innertube.YouTube
import app.aria.innertube.pages.*
import kotlin.coroutines.cancellation.CancellationException

class YouTubeBrowseSource(
    private val home: suspend () -> Result<HomePage> = { YouTube.home() },
    private val album: suspend (String) -> Result<AlbumPage> = { YouTube.album(it) },
    private val artist: suspend (String) -> Result<ArtistPage> = { YouTube.artist(it) },
    private val playlist: suspend (String) -> Result<PlaylistPage> = { YouTube.playlist(it) },
    private val playlistMore: suspend (String) -> Result<PlaylistContinuationPage> = { YouTube.playlistContinuation(it) },
) : BrowseSource {
    private suspend inline fun <T, R> call(block: suspend () -> Result<T>, map: (T) -> R): Result<R> = try {
        block().mapCatching(map)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    override suspend fun home() = call(home) { it.toShelfStates() }
    override suspend fun album(id: String) = call({ album(id) }) { it.toDetail() }
    override suspend fun artist(id: String) = call({ artist(id) }) { it.toDetail() }
    override suspend fun playlist(id: String) = call({ playlist(id) }) { it.toDetail() }
    override suspend fun playlistMore(continuation: String) =
        call({ playlistMore(continuation) }) { page -> TrackPage(page.songs.mapNotNull { it.toTrackOrNull() }, page.continuation) }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.adapter

import app.aria.domain.SearchRepository
import app.aria.domain.SearchResults
import app.aria.innertube.YouTube
import app.aria.innertube.models.SearchSuggestions
import app.aria.innertube.pages.SearchSummaryPage
import kotlin.coroutines.cancellation.CancellationException

class YouTubeSearchRepository(
    private val searchSummary: suspend (String) -> Result<SearchSummaryPage> = { YouTube.searchSummary(it) },
    private val suggestions: suspend (String) -> Result<SearchSuggestions> = { YouTube.searchSuggestions(it) },
) : SearchRepository {
    override suspend fun suggestions(query: String): Result<List<String>> = try {
        suggestions.invoke(query).map { it.queries }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    override suspend fun search(query: String): Result<SearchResults> = try {
        searchSummary.invoke(query).mapCatching { page -> page.summaries.flatMap { it.items }.toSearchResults() }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
}
```

`FixtureFreshness.kt` (test sources):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.fixtures

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object FixtureFreshness {
    const val DIR = "src/test/resources/fixtures"
    const val MAX_AGE_DAYS = 60L
    fun check(capturedAt: LocalDate, today: LocalDate): String? {
        val age = ChronoUnit.DAYS.between(capturedAt, today)
        return if (age > MAX_AGE_DAYS) "Live fixtures are $age days old (limit $MAX_AGE_DAYS). Run scripts/capture-innertube-fixtures on a maintainer machine, review the diff and commit." else null
    }
}
```

`FixtureCaptureTest.kt` (skips unless `-PcaptureFixtures=true`; runs the real adapters against YouTube Music and writes sanitized summaries):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.fixtures

import app.aria.domain.*
import app.aria.innertube.adapter.YouTubeBrowseSource
import app.aria.innertube.adapter.YouTubeSearchRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Maintainer-run only (scripts/capture-innertube-fixtures). Writes sanitized summaries of what the adapters return for real responses. */
class FixtureCaptureTest {
    private val pretty = Json { prettyPrint = true }
    private fun clean(url: String?) = url?.substringBefore('?')

    private fun item(i: BrowseItem): JsonObject = buildJsonObject {
        when (i) {
            is BrowseItem.Song -> { put("type", "song"); put("id", i.track.id.raw); put("title", i.track.title); put("thumb", clean(i.track.thumbnailUrl)) }
            is BrowseItem.Album -> { put("type", "album"); put("id", i.album.id); put("title", i.album.title); put("thumb", clean(i.album.thumbnailUrl)) }
            is BrowseItem.Artist -> { put("type", "artist"); put("id", i.artist.id); put("title", i.artist.name); put("thumb", clean(i.artist.thumbnailUrl)) }
            is BrowseItem.Playlist -> { put("type", "playlist"); put("id", i.playlist.id); put("title", i.playlist.title); put("thumb", clean(i.playlist.thumbnailUrl)) }
        }
    }
    private fun tracks(list: List<Track>) = JsonArray(list.map { item(BrowseItem.Song(it)) })
    private fun write(dir: File, name: String, obj: JsonObject) { File(dir, "$name.json").writeText(pretty.encodeToString(JsonObject.serializer(), obj)) }

    @Test fun capture() = runBlocking {
        assumeTrue(System.getProperty("aria.capture") == "true")
        val dir = File(FixtureFreshness.DIR).also { it.mkdirs() }
        val browse = YouTubeBrowseSource(); val search = YouTubeSearchRepository()
        val files = mutableListOf<String>()

        val home = browse.home().getOrThrow()
        write(dir, "home", buildJsonObject {
            put("shelves", JsonArray(home.map { s ->
                when (s) {
                    is ShelfState.Ready -> buildJsonObject { put("title", s.shelf.title); put("items", JsonArray(s.shelf.items.map(::item))) }
                    is ShelfState.Failed -> buildJsonObject { put("title", s.title); put("failed", true) }
                }
            }))
        }); files += "home.json"

        val results = search.search("daft punk").getOrThrow()
        write(dir, "search", buildJsonObject {
            put("songs", tracks(results.songs)); put("albums", JsonArray(results.albums.map { item(BrowseItem.Album(it)) }))
            put("artists", JsonArray(results.artists.map { item(BrowseItem.Artist(it)) })); put("playlists", JsonArray(results.playlists.map { item(BrowseItem.Playlist(it)) }))
        }); files += "search.json"
        write(dir, "suggestions", buildJsonObject { put("queries", JsonArray(search.suggestions("daft").getOrThrow().map(::JsonPrimitive))) }); files += "suggestions.json"

        results.albums.firstOrNull()?.let { a ->
            val d = browse.album(a.id).getOrThrow()
            write(dir, "album", buildJsonObject { put("title", d.album.title); put("tracks", tracks(d.tracks)) }); files += "album.json"
        }
        results.artists.firstOrNull()?.let { a ->
            val d = browse.artist(a.id).getOrThrow()
            write(dir, "artist", buildJsonObject { put("name", d.artist.name); put("shelves", JsonArray(d.shelves.map { s -> buildJsonObject { put("title", s.title); put("items", JsonArray(s.items.map(::item))) } })) }); files += "artist.json"
        }
        results.playlists.firstOrNull()?.let { p ->
            val d = browse.playlist(p.id).getOrThrow()
            write(dir, "playlist", buildJsonObject { put("title", d.playlist.title); put("tracks", tracks(d.tracks)); put("hasMore", d.continuation != null) }); files += "playlist.json"
        }
        File(dir, "manifest.json").writeText(pretty.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("capturedAt", LocalDate.now().toString()); put("files", JsonArray(files.map(::JsonPrimitive)))
        }))
    }
}
```

`LiveFixtureReplayTest.kt` (invariants of captured summaries; no-op when none):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.innertube.fixtures

import kotlinx.serialization.json.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveFixtureReplayTest {
    private val id = Regex("^[A-Za-z0-9_-]{11}$")
    private fun walk(e: JsonElement, f: (JsonObject) -> Unit) {
        when (e) { is JsonObject -> { f(e); e.values.forEach { walk(it, f) } }; is JsonArray -> e.forEach { walk(it, f) }; else -> {} }
    }

    @Test fun capturedSummariesLookSane() {
        val dir = File(FixtureFreshness.DIR)
        val files = dir.listFiles { f -> f.name.endsWith(".json") && f.name != "manifest.json" }.orEmpty()
        for (file in files) {
            walk(Json.parseToJsonElement(file.readText())) { o ->
                if (o["type"]?.jsonPrimitive?.content == "song") assertTrue("${file.name}: bad song id", id.matches(o["id"]!!.jsonPrimitive.content))
                o["thumb"]?.takeIf { it !is JsonNull }?.let { assertTrue("${file.name}: non-https thumb", it.jsonPrimitive.content.startsWith("https://")) }
            }
        }
        if (File(dir, "home.json").exists()) assertTrue("home has no shelves", Json.parseToJsonElement(File(dir, "home.json").readText()).jsonObject["shelves"]!!.jsonArray.isNotEmpty())
    }
}
```

  `innertube/build.gradle.kts`: in the existing `tasks.withType<Test>().configureEach` add `systemProperty("aria.capture", providers.gradleProperty("captureFixtures").orNull ?: "false")`; add `testImplementation(libs.kotlinx.serialization.json)`. `scripts/capture-innertube-fixtures` (executable, `chmod +x`):

```bash
#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Maintainer-run: fetches real YouTube Music responses through the adapters and saves sanitized summaries
# into innertube/src/test/resources/fixtures. Needs network access to music.youtube.com (CI and the sandbox cannot).
# Review the git diff before committing; FixtureFreshnessTest fails 60 days after capture.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew :innertube:testDebugUnitTest -PcaptureFixtures=true --tests '*FixtureCaptureTest' --console=plain
echo "Captured. Review: git diff innertube/src/test/resources/fixtures"
```

  Docs: `docs/release-gates.md` under "Before the first public build" add `- [ ] Parsers verified against live responses: a maintainer ran scripts/capture-innertube-fixtures, reviewed and committed innertube/src/test/resources/fixtures (manifest younger than 60 days) and the manual checklist's "Browse parsers" section passed on a device. Synthetic fixtures only prove mapping logic and null-safety.` `docs/manual-device-checklist.md`: add section "## Browse parsers (live)" with items: Home shows YouTube Music shelves and at least one tile of each kind opens; an Album, an Artist and a Playlist page open from search and play; search suggestions appear while typing; airplane mode shows the offline state and the cached Home still shows. `docs/providers/youtube-music.md`: add a bullet "Browse: `BrowseSource` and `SearchRepository` call `YouTube.home/album/artist/playlist/searchSummary/searchSuggestions`: home feed, browse IDs of album/artist/playlist pages and the text typed in search (suggestions, debounced). Same endpoint family as search; the library and account endpoints (`YouTube.library`, login) are not used (ADR-0003)." `docs/data-flow.md`: add a row "YouTube Music, browse (same provider) | Browse IDs, the home feed request, search text as typed (suggestions) | When Home, an Album/Artist/Playlist page or Search is open | No (core feature)". `PRIVACY.md`: add one sentence that the text typed in the search box is sent to YouTube Music for suggestions as you type, and that Home and detail pages request your region's feed.
- [ ] **Step 4:** Push; `:innertube:test` and the rest green (capture test skipped; freshness test prints the open-gate message). **Commit** `feat(innertube): browse and search adapters, fixture capture script and freshness test`.

---

### Task 7: Home repository (stale-while-revalidate) and the Home shelf cache

Slice 1, decision 3. Pure logic in `:domain`, storage in `:data`.

**Files:**
- Modify: `domain/src/main/kotlin/app/aria/domain/Home.kt` (add `DefaultHomeRepository`), `domain/src/testFixtures/kotlin/app/aria/domain/testing/Fakes.kt` (add `FakeHomeShelfCache`, `FakeHomeLocalSource`)
- Create: `data/src/main/kotlin/app/aria/data/{ShelfJsonCodec,HomeShelfCacheImpl}.kt`
- Test: `domain/src/test/kotlin/app/aria/domain/DefaultHomeRepositoryTest.kt`, `data/src/test/kotlin/app/aria/data/{ShelfJsonCodecTest,HomeShelfCacheImplTest}.kt`

**Interfaces:**
- Consumes: `HomeLocalSource`, `BrowseSource`, `HomeShelfCache`, `HomeRepository`, `HomeRemote`, `HomeRefresh`, `CachedHome`, `HomeShelfCacheDao`, `HomeShelfCacheEntity`.
- Produces: `class DefaultHomeRepository(local: HomeLocalSource, browse: BrowseSource, cache: HomeShelfCache, clock: () -> Long = System::currentTimeMillis) : HomeRepository` with `REVALIDATE_AFTER_MS = 15 * 60_000L`, `OUTDATED_AFTER_MS = 7L * 24 * 3600_000`; `object ShelfJsonCodec { fun encode(shelf: Shelf): String; fun decode(json: String): Shelf? }`; `class HomeShelfCacheImpl(dao: HomeShelfCacheDao) : HomeShelfCache`.

**Acceptance Criteria:**
- `refresh()` stores only `Ready` shelves, returns every shelf state (failed ones included), and on a failed fetch or zero usable shelves returns failure and leaves the cache untouched.
- `remote()` emits null with no cache; `outdated` is true only when the cache is older than 7 days; `needsRevalidation()` is true when there is no cache or it is older than 15 minutes.
- The codec round-trips every item kind; garbage, an unknown version, an unknown item type and an invalid song ID decode to null; when any cached row is undecodable the cache drops all rows and reports null (so Home refetches); nothing else (search, detail pages) is cached.

- [ ] **Step 1: Failing tests.** Fakes first (append to `Fakes.kt`):

```kotlin
class FakeHomeShelfCache : HomeShelfCache {
    val state = MutableStateFlow<CachedHome?>(null)
    var replaceCalls = 0
    override fun observe(): Flow<CachedHome?> = state
    override suspend fun replace(shelves: List<Shelf>, fetchedAtEpochMs: Long) { replaceCalls++; state.value = CachedHome(shelves, fetchedAtEpochMs) }
}

class FakeHomeLocalSource : HomeLocalSource {
    val quick = MutableStateFlow<List<Track>>(emptyList()); val recent = MutableStateFlow<List<Track>>(emptyList())
    override fun quickPicks(limit: Int): Flow<List<Track>> = quick
    override fun recentlyPlayed(limit: Int): Flow<List<Track>> = recent
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain

import app.aria.domain.testing.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DefaultHomeRepositoryTest {
    private val browse = FakeBrowseSource(); private val cache = FakeHomeShelfCache(); private var now = 10_000_000L
    private val repo = DefaultHomeRepository(FakeHomeLocalSource(), browse, cache) { now }
    private fun shelf(t: String) = Shelf(t, null, listOf(BrowseItem.Song(Samples.track(1))))

    @Test fun refreshStoresReadyShelvesAndReportsFailedOnes() = runBlocking {
        browse.homeResult = Result.success(listOf(ShelfState.Ready(shelf("A")), ShelfState.Failed("B")))
        val r = repo.refresh().getOrThrow()
        assertEquals(2, r.shelves.size)
        assertEquals(listOf("A"), cache.state.value!!.shelves.map { it.title }); assertEquals(now, cache.state.value!!.fetchedAtEpochMs)
    }

    @Test fun failedFetchKeepsTheCache() = runBlocking {
        cache.state.value = CachedHome(listOf(shelf("Old")), 1L)
        browse.homeResult = Result.failure(java.io.IOException("offline"))
        assertTrue(repo.refresh().isFailure)
        assertEquals(listOf("Old"), cache.state.value!!.shelves.map { it.title }); assertEquals(0, cache.replaceCalls)
    }

    @Test fun nothingUsableKeepsTheCache() = runBlocking {
        cache.state.value = CachedHome(listOf(shelf("Old")), 1L)
        browse.homeResult = Result.success(listOf(ShelfState.Failed("X")))
        assertTrue(repo.refresh().isFailure); assertEquals(0, cache.replaceCalls)
    }

    @Test fun remoteIsNullWithoutCacheAndOutdatedAfterSevenDays() = runBlocking {
        assertNull(repo.remote().first())
        cache.state.value = CachedHome(listOf(shelf("A")), now - 6L * 24 * 3600_000)
        assertFalse(repo.remote().first()!!.outdated)
        cache.state.value = CachedHome(listOf(shelf("A")), now - 8L * 24 * 3600_000)
        assertTrue(repo.remote().first()!!.outdated)
    }

    @Test fun revalidationAgeIsFifteenMinutes() = runBlocking {
        assertTrue(repo.needsRevalidation())
        cache.state.value = CachedHome(listOf(shelf("A")), now - 14 * 60_000L); assertFalse(repo.needsRevalidation())
        cache.state.value = CachedHome(listOf(shelf("A")), now - 16 * 60_000L); assertTrue(repo.needsRevalidation())
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShelfJsonCodecTest {
    private val shelf = Shelf(
        "Mixed", "Label",
        listOf(
            BrowseItem.Song(Track(TrackId.youtube("dQw4w9WgXcQ"), "S", listOf("A", "B"), 213_000L, "https://i.ytimg.com/x")),
            BrowseItem.Song(Track(TrackId.youtube("kJQP7kiw5Fk"), "NoDur", emptyList(), null, null)),
            BrowseItem.Album(AlbumRef("MPREb_1", "Alb", listOf("Ar"), 2020, "https://a")),
            BrowseItem.Album(AlbumRef("MPREb_2", "Alb2", emptyList(), null, null)),
            BrowseItem.Artist(ArtistRef("UC1", "N", null)),
            BrowseItem.Playlist(PlaylistRef("PL1", "P", "Me", null, 12)),
            BrowseItem.Playlist(PlaylistRef("PL2", "P2", null, "https://p", null)),
        ),
    )

    @Test fun roundTrip() = assertEquals(shelf, ShelfJsonCodec.decode(ShelfJsonCodec.encode(shelf)))
    @Test fun nullSubtitleRoundTrips() = assertEquals(shelf.copy(subtitle = null), ShelfJsonCodec.decode(ShelfJsonCodec.encode(shelf.copy(subtitle = null))))
    @Test fun garbageIsNull() { assertNull(ShelfJsonCodec.decode("not json")); assertNull(ShelfJsonCodec.decode("{}")) }
    @Test fun unknownVersionIsNull() = assertNull(ShelfJsonCodec.decode(ShelfJsonCodec.encode(shelf).replace("\"v\":1", "\"v\":99")))
    @Test fun unknownItemTypeIsNull() = assertNull(ShelfJsonCodec.decode(ShelfJsonCodec.encode(shelf).replace("\"t\":\"artist\"", "\"t\":\"podcast\"")))
    @Test fun invalidSongIdIsNull() = assertNull(ShelfJsonCodec.decode(ShelfJsonCodec.encode(shelf).replace("dQw4w9WgXcQ", "short")))
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aria.data.entities.HomeShelfCacheEntity
import app.aria.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeShelfCacheImplTest {
    private lateinit var db: AriaDatabase
    private lateinit var cache: HomeShelfCacheImpl
    private fun shelf(t: String) = Shelf(t, null, listOf(BrowseItem.Artist(ArtistRef("UC1", t, null))))

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AriaDatabase::class.java).allowMainThreadQueries().build()
        cache = HomeShelfCacheImpl(db.homeShelfCacheDao())
    }
    @After fun close() = db.close()

    @Test fun emptyIsNull() = runBlocking { assertNull(cache.observe().first()) }

    @Test fun replaceKeepsOrderAndTime() = runBlocking {
        cache.replace(listOf(shelf("A"), shelf("B")), 500L)
        val c = cache.observe().first()!!
        assertEquals(listOf("A", "B"), c.shelves.map { it.title }); assertEquals(500L, c.fetchedAtEpochMs)
        cache.replace(listOf(shelf("C")), 900L)
        assertEquals(listOf("C"), cache.observe().first()!!.shelves.map { it.title })
    }

    @Test fun undecodableRowDropsTheWholeCacheSoHomeRefetches() = runBlocking {
        cache.replace(listOf(shelf("A")), 500L)
        db.homeShelfCacheDao().replaceAll(db.homeShelfCacheDao().observe().first() + HomeShelfCacheEntity("yt:09", 9, "garbage", 500L))
        assertNull(cache.observe().first())
        assertTrue(db.homeShelfCacheDao().observe().first().isEmpty())
    }
}
```

- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.** Append to `Home.kt`:

```kotlin
class DefaultHomeRepository(
    private val local: HomeLocalSource,
    private val browse: BrowseSource,
    private val cache: HomeShelfCache,
    private val clock: () -> Long = System::currentTimeMillis,
) : HomeRepository {
    override fun quickPicks(limit: Int) = local.quickPicks(limit)
    override fun recentlyPlayed(limit: Int) = local.recentlyPlayed(limit)

    override fun remote(): Flow<HomeRemote?> = cache.observe().map { c ->
        c?.takeIf { it.shelves.isNotEmpty() }?.let {
            HomeRemote(it.shelves, it.fetchedAtEpochMs, outdated = clock() - it.fetchedAtEpochMs > OUTDATED_AFTER_MS)
        }
    }

    override suspend fun needsRevalidation(): Boolean {
        val c = cache.observe().first() ?: return true
        return clock() - c.fetchedAtEpochMs > REVALIDATE_AFTER_MS
    }

    override suspend fun refresh(): Result<HomeRefresh> {
        val states = browse.home().getOrElse { return Result.failure(it) }
        val ready = states.filterIsInstance<ShelfState.Ready>().map { it.shelf }
        if (ready.isEmpty()) return Result.failure(IllegalStateException("Home returned no usable shelves"))
        cache.replace(ready, clock())
        return Result.success(HomeRefresh(states))
    }

    companion object {
        const val REVALIDATE_AFTER_MS = 15 * 60_000L
        const val OUTDATED_AFTER_MS = 7L * 24 * 3600_000
    }
}
```

  (add `import kotlinx.coroutines.flow.first` and `import kotlinx.coroutines.flow.map` to `Home.kt`.)

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned JSON of one domain Shelf. decode never throws: anything unexpected is null. */
object ShelfJsonCodec {
    private const val VERSION = 1

    fun encode(shelf: Shelf): String = JSONObject()
        .put("v", VERSION).put("title", shelf.title).put("subtitle", shelf.subtitle ?: JSONObject.NULL)
        .put("items", JSONArray().also { a -> shelf.items.forEach { a.put(encodeItem(it)) } })
        .toString()

    fun decode(json: String): Shelf? = try {
        val o = JSONObject(json)
        if (o.getInt("v") != VERSION) null
        else {
            val items = o.getJSONArray("items")
            Shelf(o.getString("title"), o.str("subtitle"), (0 until items.length()).map { decodeItem(items.getJSONObject(it)) ?: return null })
        }
    } catch (e: Exception) { null }

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)
    private fun JSONObject.int(k: String): Int? = if (isNull(k)) null else getInt(k)
    private fun JSONObject.strings(k: String): List<String> = getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } }
    private fun nul(v: Any?): Any = v ?: JSONObject.NULL

    private fun encodeItem(i: BrowseItem): JSONObject = when (i) {
        is BrowseItem.Song -> JSONObject().put("t", "song").put("id", i.track.id.raw).put("title", i.track.title)
            .put("artists", JSONArray(i.track.artists)).put("dur", nul(i.track.durationMs)).put("thumb", nul(i.track.thumbnailUrl))
        is BrowseItem.Album -> JSONObject().put("t", "album").put("id", i.album.id).put("title", i.album.title)
            .put("artists", JSONArray(i.album.artists)).put("year", nul(i.album.year)).put("thumb", nul(i.album.thumbnailUrl))
        is BrowseItem.Artist -> JSONObject().put("t", "artist").put("id", i.artist.id).put("title", i.artist.name).put("thumb", nul(i.artist.thumbnailUrl))
        is BrowseItem.Playlist -> JSONObject().put("t", "playlist").put("id", i.playlist.id).put("title", i.playlist.title)
            .put("sub", nul(i.playlist.subtitle)).put("thumb", nul(i.playlist.thumbnailUrl)).put("count", nul(i.playlist.trackCount))
    }

    private fun decodeItem(o: JSONObject): BrowseItem? = when (o.getString("t")) {
        "song" -> BrowseItem.Song(Track(TrackId.parse(o.getString("id")), o.getString("title"), o.strings("artists"), if (o.isNull("dur")) null else o.getLong("dur"), o.str("thumb")))
        "album" -> BrowseItem.Album(AlbumRef(o.getString("id"), o.getString("title"), o.strings("artists"), o.int("year"), o.str("thumb")))
        "artist" -> BrowseItem.Artist(ArtistRef(o.getString("id"), o.getString("title"), o.str("thumb")))
        "playlist" -> BrowseItem.Playlist(PlaylistRef(o.getString("id"), o.getString("title"), o.str("sub"), o.str("thumb"), o.int("count")))
        else -> null
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.data.entities.HomeShelfCacheEntity
import app.aria.domain.CachedHome
import app.aria.domain.HomeShelfCache
import app.aria.domain.Shelf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HomeShelfCacheImpl(private val dao: HomeShelfCacheDao) : HomeShelfCache {
    override fun observe(): Flow<CachedHome?> = dao.observe().map { rows ->
        if (rows.isEmpty()) return@map null
        val decoded = rows.map { it to ShelfJsonCodec.decode(it.payload) }
        if (decoded.any { it.second == null }) {
            // Decision 3: an undecodable row means the cache is from an incompatible build or damaged; drop it all and refetch.
            dao.delete(rows.map { it.shelfKey })
            return@map null
        }
        CachedHome(decoded.map { it.second!! }, rows.minOf { it.fetchedAtEpochMs })
    }

    override suspend fun replace(shelves: List<Shelf>, fetchedAtEpochMs: Long) {
        dao.replaceAll(shelves.mapIndexed { i, s -> HomeShelfCacheEntity("yt:%02d".format(i), i, ShelfJsonCodec.encode(s), fetchedAtEpochMs) })
    }
}
```
- [ ] **Step 4:** Push; `:domain:test`, `:data:test` green. **Commit** `feat: Home repository with stale-while-revalidate cache`.

---

### Task 8: `PlayerController`, `PlayerUiState`, messages and the app test setup

Slice 1, decisions 4 and 9 (window). The only playback entry point for screens.

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/player/{PlayerUiState,PlayerController,ServiceLauncher}.kt`, `app/src/main/kotlin/app/aria/music/di/{Qualifiers,UiModule}.kt`, `domain/src/testFixtures/kotlin/app/aria/domain/testing/FakePlaybackEngine.kt`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/kotlin/app/aria/music/player/{TestEngine,PlayerControllerTest}.kt`

**Interfaces:**
- Consumes: `PlaybackEngine` (incl. `setHistoryThresholdMs`), `PlaybackState`, `PlaybackEvent`, `QueueItem`, `RepeatMode`, `SleepTimer`, `DefaultPlaybackEngine.QUEUE_CAP`, `AriaPlaybackService.start(context)`.
- Produces:

```kotlin
enum class PlayerStatus { IDLE, BUFFERING, PLAYING, PAUSED, WAITING_FOR_NETWORK, ERROR }
data class PlayerUiState(
    val queue: List<QueueItem> = emptyList(), val currentIndex: Int = -1, val status: PlayerStatus = PlayerStatus.IDLE,
    val durationMs: Long = 0L, val shuffle: Boolean = false, val repeat: RepeatMode = RepeatMode.OFF, val sleepTimer: SleepTimer? = null,
) { val current: QueueItem?; val hasQueue: Boolean; val isPlaying: Boolean /* PLAYING or BUFFERING */ }
enum class SkipReason { UNAVAILABLE, NETWORK, OTHER }
sealed interface PlayerMessage {
    data class TrackSkipped(val title: String, val reason: SkipReason) : PlayerMessage
    data object TooManyFailures : PlayerMessage
    data class QueueWindowed(val queued: Int, val total: Int) : PlayerMessage
    data class QueueFull(val dropped: Int) : PlayerMessage
}
fun interface ServiceLauncher { fun start() }
@Singleton class PlayerController @Inject constructor(engine: PlaybackEngine, launcher: ServiceLauncher, @ApplicationScope scope: CoroutineScope) {
    val ui: StateFlow<PlayerUiState>; val positionMs: StateFlow<Long>; val messages: Flow<PlayerMessage>
    fun play(tracks: List<Track>, startIndex: Int = 0); fun playNext(track: Track); fun addToQueue(tracks: List<Track>)
    fun skipTo(index: Int); fun removeAt(index: Int); fun move(from: Int, to: Int); fun clearQueue()
    fun togglePlayPause(); fun next(); fun previous(); fun seekTo(positionMs: Long)
    fun setShuffle(on: Boolean); fun cycleRepeat(); fun setSleepTimer(minutes: Int?); fun setSleepAtEndOfTrack(on: Boolean)
}
@Qualifier annotation class ApplicationScope
class FakePlaybackEngine : PlaybackEngine  // testFixtures
```

**Acceptance Criteria:**
- `ui` is a slow state: it does not re-emit when only `positionMs` changes; `positionMs` is a separate flow.
- A command with no running service starts the service and sets the queue: with a fake engine and with the real `DefaultPlaybackEngine` having no output attached, `play(tracks)` calls the launcher once and the queue equals the tracks.
- Playing from a list longer than 500 queues a window of at most 500 starting at the tapped item and emits `QueueWindowed(queued, total)`; a list of at most 500 queues everything with no message; `addToQueue` over the remaining room emits `QueueFull(dropped)`.
- Engine events become one-shot messages through a controller-owned channel; reasons map `"unavailable"` to `UNAVAILABLE`, `"network error"` to `NETWORK`, anything else to `OTHER`; `Stopped` maps to `TooManyFailures` (the engine does not expose a "region" reason; see risks).

- [ ] **Step 1: Test support and failing tests.** `app/build.gradle.kts`: add (after the existing `implementation` lines)

```kotlin
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(project(":domain")))
    androidTestImplementation(testFixtures(project(":domain")))
```

  and inside `android { ... }`: `testOptions { unitTests.isIncludeAndroidResources = true }`. `FakePlaybackEngine.kt` (testFixtures):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.domain.testing

import app.aria.domain.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class FakePlaybackEngine : PlaybackEngine {
    val mutableState = MutableStateFlow(PlaybackState())
    val mutableEvents = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
    override val state: StateFlow<PlaybackState> = mutableState
    override val events: SharedFlow<PlaybackEvent> = mutableEvents
    val calls = mutableListOf<String>()
    val playNowCalls = mutableListOf<Pair<List<Track>, Int>>()
    val addedToEnd = mutableListOf<List<Track>>()
    var historyThresholdMs: Long? = null
    private var nextId = 1L

    private fun items(tracks: List<Track>) = tracks.map { QueueItem(nextId++, it) }
    override fun playNow(tracks: List<Track>, startIndex: Int) {
        calls += "playNow"; playNowCalls += tracks to startIndex
        mutableState.value = mutableState.value.copy(queue = items(tracks), currentIndex = startIndex, status = PlaybackStatus.Buffering)
    }
    override fun playNext(track: Track) { calls += "playNext" }
    override fun addToEnd(tracks: List<Track>) { calls += "addToEnd"; addedToEnd.add(tracks) }
    override fun removeAt(index: Int) { calls += "removeAt:$index" }
    override fun move(from: Int, to: Int) { calls += "move:$from:$to" }
    override fun clear() { calls += "clear" }
    override fun skipTo(index: Int) { calls += "skipTo:$index" }
    override fun play() { calls += "play" }
    override fun pause() { calls += "pause" }
    override fun next() { calls += "next" }
    override fun previous() { calls += "previous" }
    override fun seekTo(positionMs: Long) { calls += "seekTo:$positionMs" }
    override fun setShuffle(on: Boolean) { calls += "setShuffle:$on" }
    override fun setRepeat(mode: RepeatMode) { calls += "setRepeat:$mode" }
    override fun setSleepTimer(durationMs: Long?) { calls += "setSleepTimer:$durationMs" }
    override fun setSleepAtEndOfTrack(on: Boolean) { calls += "setSleepAtEndOfTrack:$on" }
    override fun setSkipSilence(on: Boolean) { calls += "setSkipSilence:$on" }
    override fun setNormalizeLoudness(on: Boolean) { calls += "setNormalizeLoudness:$on" }
    override fun setEndlessRadio(on: Boolean) { calls += "setEndlessRadio:$on" }
    override fun setHistoryThresholdMs(ms: Long) { historyThresholdMs = ms; calls += "setHistoryThresholdMs:$ms" }
}
```

  `TestEngine.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.player

import app.aria.domain.*
import app.aria.domain.testing.FakeNetworkMonitor
import app.aria.playback.DefaultPlaybackEngine
import kotlinx.coroutines.CoroutineDispatcher

/** A real engine with inert collaborators and no output attached (no service running). */
fun testEngine(dispatcher: CoroutineDispatcher) = DefaultPlaybackEngine(
    streams = object : StreamSource { override suspend fun resolve(id: TrackId) = Result.failure<ResolvedStream>(IllegalStateException("test")) },
    store = object : QueueStore {
        override suspend fun saveStructure(queue: SavedQueue) {}
        override suspend fun savePosition(position: SavedPosition) {}
        override suspend fun load(): RestoredQueue? = null
    },
    radio = object : RadioSource { override suspend fun related(seed: Track, limit: Int) = Result.success(emptyList<Track>()) },
    history = object : PlayEventRepository {
        override suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long) {}
        override suspend fun playCount(id: TrackId) = 0
    },
    network = FakeNetworkMonitor(),
    dispatcher = dispatcher,
    workDispatcher = dispatcher,
)
```

```kotlin
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
}
```

- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.di

import javax.inject.Qualifier

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.player

fun interface ServiceLauncher { fun start() }
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.di

import android.content.Context
import app.aria.music.player.ServiceLauncher
import app.aria.playback.AriaPlaybackService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UiModule {
    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides @Singleton
    fun serviceLauncher(@ApplicationContext c: Context): ServiceLauncher = ServiceLauncher { AriaPlaybackService.start(c) }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.player

import app.aria.domain.*

enum class PlayerStatus { IDLE, BUFFERING, PLAYING, PAUSED, WAITING_FOR_NETWORK, ERROR }

/** Slow state: everything except the playback position (see PlayerController.positionMs). */
data class PlayerUiState(
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val status: PlayerStatus = PlayerStatus.IDLE,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val sleepTimer: SleepTimer? = null,
) {
    val current: QueueItem? get() = queue.getOrNull(currentIndex)
    val hasQueue: Boolean get() = queue.isNotEmpty()
    val isPlaying: Boolean get() = status == PlayerStatus.PLAYING || status == PlayerStatus.BUFFERING
}

enum class SkipReason { UNAVAILABLE, NETWORK, OTHER }

sealed interface PlayerMessage {
    data class TrackSkipped(val title: String, val reason: SkipReason) : PlayerMessage
    data object TooManyFailures : PlayerMessage
    data class QueueWindowed(val queued: Int, val total: Int) : PlayerMessage
    data class QueueFull(val dropped: Int) : PlayerMessage
}

internal fun PlaybackState.toUi() = PlayerUiState(
    queue = queue, currentIndex = currentIndex,
    status = when (status) {
        PlaybackStatus.Idle -> PlayerStatus.IDLE
        PlaybackStatus.Buffering -> PlayerStatus.BUFFERING
        PlaybackStatus.Playing -> PlayerStatus.PLAYING
        PlaybackStatus.Paused -> PlayerStatus.PAUSED
        PlaybackStatus.WaitingForNetwork -> PlayerStatus.WAITING_FOR_NETWORK
        is PlaybackStatus.Error -> PlayerStatus.ERROR
    },
    durationMs = durationMs, shuffle = shuffle, repeat = repeat, sleepTimer = sleepTimer,
)

internal fun PlaybackEvent.toMessage(): PlayerMessage = when (this) {
    is PlaybackEvent.Skipped -> PlayerMessage.TrackSkipped(
        track.title,
        when (reason) { "unavailable" -> SkipReason.UNAVAILABLE; "network error" -> SkipReason.NETWORK; else -> SkipReason.OTHER },
    )
    is PlaybackEvent.Stopped -> PlayerMessage.TooManyFailures
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.player

import app.aria.domain.*
import app.aria.music.di.ApplicationScope
import app.aria.playback.DefaultPlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** The only playback entry point for screens. Wraps the engine; starts the playback service before a command that needs it. */
@Singleton
class PlayerController @Inject constructor(
    private val engine: PlaybackEngine,
    private val launcher: ServiceLauncher,
    @ApplicationScope scope: CoroutineScope,
) {
    val ui: StateFlow<PlayerUiState> =
        engine.state.map { it.toUi() }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, PlayerUiState())

    /** Collected only by the seek bar and the mini-player progress line. */
    val positionMs: StateFlow<Long> =
        engine.state.map { it.positionMs }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, 0L)

    private val channel = Channel<PlayerMessage>(Channel.BUFFERED)
    val messages: Flow<PlayerMessage> = channel.receiveAsFlow()

    init { scope.launch { engine.events.collect { channel.trySend(it.toMessage()) } } }

    fun play(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val tapped = startIndex.coerceIn(0, tracks.lastIndex)
        launcher.start()
        if (tracks.size > QUEUE_CAP) {
            val window = tracks.subList(tapped, minOf(tracks.size, tapped + QUEUE_CAP))
            engine.playNow(window, 0)
            channel.trySend(PlayerMessage.QueueWindowed(window.size, tracks.size))
        } else {
            engine.playNow(tracks, tapped)
        }
    }

    fun playNext(track: Track) { launcher.start(); engine.playNext(track) }

    fun addToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (!ui.value.hasQueue) { play(tracks, 0); return }
        val room = (QUEUE_CAP - ui.value.queue.size).coerceAtLeast(0)
        if (tracks.size > room) channel.trySend(PlayerMessage.QueueFull(tracks.size - room))
        launcher.start(); engine.addToEnd(tracks)
    }

    fun skipTo(index: Int) = engine.skipTo(index)
    fun removeAt(index: Int) = engine.removeAt(index)
    fun move(from: Int, to: Int) = engine.move(from, to)
    fun clearQueue() = engine.clear()
    fun togglePlayPause() { if (ui.value.isPlaying) engine.pause() else engine.play() }
    fun next() = engine.next()
    fun previous() = engine.previous()
    fun seekTo(positionMs: Long) = engine.seekTo(positionMs)
    fun setShuffle(on: Boolean) = engine.setShuffle(on)
    fun cycleRepeat() = engine.setRepeat(when (ui.value.repeat) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF })
    fun setSleepTimer(minutes: Int?) = engine.setSleepTimer(minutes?.let { it * 60_000L })
    fun setSleepAtEndOfTrack(on: Boolean) = engine.setSleepAtEndOfTrack(on)

    private companion object { const val QUEUE_CAP = DefaultPlaybackEngine.QUEUE_CAP }
}
```

  (`togglePlayPause` reads `ui.value`, which tests refresh by `runCurrent()`; the fake emits `Playing` then `Paused` as in the test.)
- [ ] **Step 4:** Push; `:app:testFossDebugUnitTest` and the whole build green (Hilt validates `PlayerController` because `UiModule` provides its dependencies). **Commit** `feat(app): PlayerController with slow state, position flow and message channel`.

---

### Task 9: App wiring: DI for data and browse, settings applied at startup

Slice 1 completes here (data layer plus `PlayerController` usable and wired; the legacy screen still works). Decision 5 (application side).

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/di/{DataModule,BrowseModule}.kt`, `app/src/main/kotlin/app/aria/music/player/EngineSettingsApplier.kt`
- Modify: `app/src/main/kotlin/app/aria/music/AriaApp.kt`, `app/src/androidTest/kotlin/app/aria/music/HiltWiringTest.kt`
- Test: `app/src/test/kotlin/app/aria/music/player/EngineSettingsApplierTest.kt`

**Interfaces:**
- Consumes: all Task 1 contracts, `*Impl` classes from `:data`, `YouTubeBrowseSource`, `YouTubeSearchRepository`, `createSettingsDataStore`, `DefaultHomeRepository`, `PlaybackEngine`.
- Produces: Hilt bindings for `LibraryRepository`, `PlaylistRepository`, `HomeLocalSource`, `SearchHistoryRepository`, `HomeShelfCache`, `HomeRepository`, `SettingsRepository`, `BrowseSource`, `SearchRepository`; `@Singleton class EngineSettingsApplier @Inject constructor(engine: PlaybackEngine, settings: SettingsRepository, @ApplicationScope scope: CoroutineScope) { fun start(): Job }`.

**Acceptance Criteria:**
- Stored settings reach the engine with no Settings screen anywhere in the app: the applier test builds only a repository, the applier and the real engine, and sees `endlessRadio`, `skipSilence`, `normalizeLoudness` and `historyThresholdMs` applied; later changes follow.
- `AriaApp.onCreate` starts the applier; no screen code applies settings.
- `HiltWiringTest` resolves the new bindings and `PlayerController` on API 26 and 34.
- Only files in `app/.../di/` import `app.aria.data.*` or `app.aria.innertube.*` for the new wiring.

- [ ] **Step 1: Failing test:**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.player

import app.aria.domain.AppSettings
import app.aria.domain.testing.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EngineSettingsApplierTest {
    @Test fun storedValuesReachTheEngineWithoutAnyScreen() = runTest {
        val engine = testEngine(StandardTestDispatcher(testScheduler))
        val repo = FakeSettingsRepository(AppSettings(historyThresholdMs = 45_000, endlessRadio = false, skipSilence = true, normalizeLoudness = false))
        EngineSettingsApplier(engine, repo, backgroundScope).start(); runCurrent()
        val s = engine.state.value
        assertEquals(45_000L, s.historyThresholdMs); assertFalse(s.endlessRadio); assertTrue(s.skipSilence); assertFalse(s.normalizeLoudness)
        engine.release()
    }

    @Test fun laterChangesFollow() = runTest {
        val engine = testEngine(StandardTestDispatcher(testScheduler)); val repo = FakeSettingsRepository()
        EngineSettingsApplier(engine, repo, backgroundScope).start(); runCurrent()
        assertEquals(30_000L, engine.state.value.historyThresholdMs); assertTrue(engine.state.value.endlessRadio)
        repo.update { it.copy(historyThresholdMs = 90_000, endlessRadio = false) }; runCurrent()
        assertEquals(90_000L, engine.state.value.historyThresholdMs); assertFalse(engine.state.value.endlessRadio)
        engine.release()
    }

    @Test fun outOfRangeStoredValuesAreClampedByTheEngine() = runTest {
        val engine = testEngine(StandardTestDispatcher(testScheduler))
        EngineSettingsApplier(engine, FakeSettingsRepository(), backgroundScope).apply(AppSettings(historyThresholdMs = 5)); runCurrent()
        assertEquals(10_000L, engine.state.value.historyThresholdMs)
        engine.release()
    }
}
```

- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.player

import app.aria.domain.AppSettings
import app.aria.domain.PlaybackEngine
import app.aria.domain.SettingsRepository
import app.aria.music.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Applies stored settings to the engine. Started by the Application so no screen has to exist for settings to take effect. */
@Singleton
class EngineSettingsApplier @Inject constructor(
    private val engine: PlaybackEngine,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start(): Job = scope.launch { settings.settings.collect { apply(it) } }

    internal fun apply(s: AppSettings) {
        engine.setEndlessRadio(s.endlessRadio)
        engine.setSkipSilence(s.skipSilence)
        engine.setNormalizeLoudness(s.normalizeLoudness)
        engine.setHistoryThresholdMs(s.historyThresholdMs)
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.di

import android.content.Context
import app.aria.data.*
import app.aria.domain.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton fun library(db: AriaDatabase): LibraryRepository = LibraryRepositoryImpl(db.libraryDao())
    @Provides @Singleton fun playlists(db: AriaDatabase): PlaylistRepository = PlaylistRepositoryImpl(db.playlistDao())
    @Provides @Singleton fun homeLocal(db: AriaDatabase): HomeLocalSource = HomeLocalSourceImpl(db.homeLocalDao())
    @Provides @Singleton fun searchHistory(db: AriaDatabase): SearchHistoryRepository = SearchHistoryRepositoryImpl(db.searchHistoryDao())
    @Provides @Singleton fun homeCache(db: AriaDatabase): HomeShelfCache = HomeShelfCacheImpl(db.homeShelfCacheDao())

    @Provides @Singleton
    fun home(local: HomeLocalSource, browse: BrowseSource, cache: HomeShelfCache): HomeRepository = DefaultHomeRepository(local, browse, cache)

    @Provides @Singleton
    fun settings(@ApplicationContext c: Context): SettingsRepository = DataStoreSettingsRepository(
        createSettingsDataStore(File(c.filesDir, "datastore/settings.preferences_pb"), CoroutineScope(SupervisorJob() + Dispatchers.IO)),
    )
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.di

import app.aria.domain.BrowseSource
import app.aria.domain.SearchRepository
import app.aria.innertube.adapter.YouTubeBrowseSource
import app.aria.innertube.adapter.YouTubeSearchRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BrowseModule {
    @Provides @Singleton fun browse(): BrowseSource = YouTubeBrowseSource()
    @Provides @Singleton fun searchRepository(): SearchRepository = YouTubeSearchRepository()
}
```

  `AriaApp.kt`: add `@Inject lateinit var settingsApplier: app.aria.music.player.EngineSettingsApplier` and, in `onCreate` after `engine.serviceStarter = ...`, `settingsApplier.start()`. `HiltWiringTest.kt`: add `@Inject lateinit var library: LibraryRepository; playlists: PlaylistRepository; home: HomeRepository; settings: SettingsRepository; browse: BrowseSource; searchRepository: SearchRepository; controller: app.aria.music.player.PlayerController` and assert all non-null in `graphResolves` (imports from `app.aria.domain.*`).
- [ ] **Step 4:** Push; all jobs green incl. `playback-service` (API 26/34: the real Hilt graph, DB with migration and DataStore open on the emulator). **Commit** `feat(app): wire data layer, browse adapters and startup settings`. **Slice 1 complete.**

---

### Task 10: UI guard scripts (string literals, screen imports)

Slice 2, decision 8 and the module rules. Lands before the first screen.

**Files:**
- Create: `scripts/check_ui_strings.py`, `scripts/check-ui-strings.sh`, `scripts/test_check_ui_strings.py`, `scripts/ui-strings-baseline.txt`, `scripts/check-screen-imports.sh`, `scripts/test_check_screen_imports.py`
- Modify: `.github/workflows/ci.yml` (Static checks step)

**Interfaces:**
- Produces: `scripts/check-ui-strings.sh [repo-root]`, `scripts/check-screen-imports.sh [repo-root]` (exit 1 with `FAIL: ...` lines on violations).

**Acceptance Criteria:**
- The strings check fails on a string literal containing a letter passed to `Text(`, `Text(text =`, or a parameter named `contentDescription`, `stateDescription`, `label`, `title`, `text`, `placeholder`, `hint`, `supportingText`, `message`, `description`, or to `showSnackbar(`, in any `.kt` under `app/src/main`; templates-only literals (`"$count"`, `"${x}"`) and letterless literals (`"•"`) pass; a trailing `// ui-string-ok: <reason>` allows one line; files elsewhere (`androidTest`, `test`) are ignored.
- `scripts/ui-strings-baseline.txt` lists repo-relative files exempt while they are legacy (only `app/src/main/kotlin/app/aria/music/PlayerScreen.kt`, deleted in Task 16). The check fails when a listed file does not exist or has no violation any more ("stale baseline entry"), so the list can only shrink.
- The imports check fails when a file under `app/src/main/kotlin` outside `app/aria/music/di/` imports `app.aria.data.` or `app.aria.innertube.`, and when a file under `app/aria/music/ui/` imports `app.aria.playback.` or `app.aria.domain.PlaybackEngine` (screens use `PlayerController`).
- Both run in CI "Static checks" and are self-tested by pytest samples; the real repo passes.

- [ ] **Step 1: Failing tests.**

```python
# SPDX-License-Identifier: GPL-3.0-only
import pathlib, subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts" / "check-ui-strings.sh"
KT = "app/src/main/kotlin/app/aria/music/ui/Screen.kt"


def put(root, rel, text):
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)


def run(root):
    p = subprocess.run(["bash", str(SCRIPT), str(root)], capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr


def flagged(tmp_path, body):
    put(tmp_path, KT, body)
    return run(tmp_path)


def test_literals_in_text_are_flagged(tmp_path):
    for body in ['Text("Hello")', 'Text(text = "Hello")', 'Text(\n    "Hello",\n    maxLines = 1)', 'Text("""Hello""")']:
        code, out = flagged(tmp_path, body + "\n")
        assert code == 1 and "Screen.kt" in out, (body, out)


def test_parameters_are_flagged(tmp_path):
    for body in ['Icon(x, contentDescription = "Play")', 'label = { }, title = "Queue"', 'placeholder = "Search"',
                 'stateDescription = "On"', 'scope.launch { host.showSnackbar("Saved") }', 'contentDescription = if (a) "A" else null']:
        code, out = flagged(tmp_path, body + "\n")
        assert code == 1, (body, out)


def test_resources_nulls_and_templates_pass(tmp_path):
    body = 'Text(stringResource(R.string.x))\nIcon(x, contentDescription = null)\nText("$count")\nText("${a.b}")\nText("•")\nText(title)\n'
    code, out = flagged(tmp_path, body)
    assert code == 0, out


def test_letters_next_to_templates_are_flagged(tmp_path):
    code, out = flagged(tmp_path, 'Text("${a} songs")\n')
    assert code == 1, out


def test_escape_comment_allows_one_line(tmp_path):
    code, out = flagged(tmp_path, 'Text("Debug") // ui-string-ok: developer-only label\n')
    assert code == 0, out


def test_other_source_sets_are_ignored(tmp_path):
    put(tmp_path, "app/src/androidTest/kotlin/X.kt", 'Text("hello")\n')
    put(tmp_path, "app/src/test/kotlin/X.kt", 'Text("hello")\n')
    code, out = run(tmp_path)
    assert code == 0, out


def test_baseline_exempts_a_file_that_still_violates(tmp_path):
    put(tmp_path, "app/src/main/kotlin/Legacy.kt", 'Text("Old")\n')
    put(tmp_path, "scripts/ui-strings-baseline.txt", "app/src/main/kotlin/Legacy.kt\n")
    code, out = run(tmp_path)
    assert code == 0, out


def test_stale_baseline_entries_fail(tmp_path):
    put(tmp_path, "app/src/main/kotlin/Clean.kt", 'Text(stringResource(R.string.x))\n')
    put(tmp_path, "scripts/ui-strings-baseline.txt", "app/src/main/kotlin/Clean.kt\napp/src/main/kotlin/Missing.kt\n")
    code, out = run(tmp_path)
    assert code == 1 and "stale baseline" in out and "Clean.kt" in out and "Missing.kt" in out, out


def test_real_repo_passes():
    code, out = run(ROOT)
    assert code == 0, out
```

```python
# SPDX-License-Identifier: GPL-3.0-only
import pathlib, subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts" / "check-screen-imports.sh"
BASE = "app/src/main/kotlin/app/aria/music"


def put(root, rel, text):
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)


def run(root):
    p = subprocess.run(["bash", str(SCRIPT), str(root)], capture_output=True, text=True)
    return p.returncode, p.stdout + p.stderr


def test_di_may_import_data_and_innertube(tmp_path):
    put(tmp_path, f"{BASE}/di/M.kt", "import app.aria.data.AriaDatabase\nimport app.aria.innertube.YouTube\n")
    code, out = run(tmp_path)
    assert code == 0, out


def test_screens_may_not_import_data_or_innertube(tmp_path):
    for imp in ["import app.aria.data.LibraryDao", "import app.aria.innertube.models.SongItem"]:
        put(tmp_path, f"{BASE}/ui/home/HomeScreen.kt", imp + "\n")
        code, out = run(tmp_path)
        assert code == 1 and "HomeScreen.kt" in out, (imp, out)


def test_root_package_files_may_not_import_them_either(tmp_path):
    put(tmp_path, f"{BASE}/Foo.kt", "import app.aria.data.AriaDatabase\n")
    code, out = run(tmp_path)
    assert code == 1 and "Foo.kt" in out, out


def test_ui_may_not_touch_the_engine_directly(tmp_path):
    for imp in ["import app.aria.playback.DefaultPlaybackEngine", "import app.aria.domain.PlaybackEngine"]:
        put(tmp_path, f"{BASE}/ui/nowplaying/X.kt", imp + "\n")
        code, out = run(tmp_path)
        assert code == 1 and "X.kt" in out, (imp, out)


def test_domain_models_and_player_controller_are_fine_in_ui(tmp_path):
    put(tmp_path, f"{BASE}/ui/nowplaying/X.kt", "import app.aria.domain.Track\nimport app.aria.domain.PlaybackState\nimport app.aria.music.player.PlayerController\n")
    code, out = run(tmp_path)
    assert code == 0, out


def test_real_repo_passes():
    code, out = run(ROOT)
    assert code == 0, out
```

- [ ] **Step 2:** Push; the pytest step fails (scripts missing). Expected: FAIL.
- [ ] **Step 3: Implement.** `scripts/check-ui-strings.sh`:

```bash
#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Every user-visible string lives in strings.xml (UI shell decision 8). Self-tested by scripts/test_check_ui_strings.py.
# Usage: check-ui-strings.sh [repo-root]
exec python3 "$(dirname "$0")/check_ui_strings.py" "${1:-.}"
```

```python
#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fails on user-visible string literals in app/src/main Kotlin: Text("..."), contentDescription = "..." and similar.

A literal counts when, after removing $name and ${...} templates, it still contains a letter. A line ending in
`// ui-string-ok: <reason>` is allowed. Files listed in scripts/ui-strings-baseline.txt are exempt while they are
legacy; a baseline entry that is missing or no longer violates is itself a failure, so the list only shrinks.
"""
import pathlib
import re
import sys

START = re.compile(
    r'\bText\(\s*(?:text\s*=\s*)?(?=")'
    r'|\b(?:contentDescription|stateDescription|label|title|text|placeholder|hint|supportingText|message|description)\s*=\s*(?:if\s*\([^)]*\)\s*)?(?=")'
    r'|\bshowSnackbar\(\s*(?:message\s*=\s*)?(?=")'
)
TEMPLATE = re.compile(r'\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*')


def literal_at(text: str, i: int) -> str:
    """Body of the string literal starting at text[i] == '"' (raw or normal)."""
    if text.startswith('"""', i):
        j = text.find('"""', i + 3)
        return text[i + 3:j if j >= 0 else len(text)]
    j = i + 1
    while j < len(text) and text[j] != '"':
        j += 2 if text[j] == "\\" else 1
    return text[i + 1:j]


def violations(path: pathlib.Path) -> list[int]:
    text = path.read_text()
    lines = text.split("\n")
    out = []
    for m in START.finditer(text):
        body = TEMPLATE.sub("", literal_at(text, m.end()))
        if not re.search(r"[A-Za-z]", body):
            continue
        line_no = text.count("\n", 0, m.start()) + 1
        if "ui-string-ok:" in lines[line_no - 1]:
            continue
        out.append(line_no)
    return out


def main(root: pathlib.Path) -> int:
    baseline_file = root / "scripts" / "ui-strings-baseline.txt"
    baseline = {l.strip() for l in baseline_file.read_text().splitlines() if l.strip() and not l.startswith("#")} if baseline_file.exists() else set()
    fail = False
    seen_violating: set[str] = set()
    for f in sorted((root / "app" / "src" / "main").rglob("*.kt")):
        rel = f.relative_to(root).as_posix()
        v = violations(f)
        if v:
            seen_violating.add(rel)
            if rel not in baseline:
                fail = True
                for n in v:
                    print(f"FAIL: string literal in UI code, use strings.xml: {rel}:{n}")
    for entry in sorted(baseline - seen_violating):
        fail = True
        print(f"FAIL: stale baseline entry (file missing or clean, remove it from scripts/ui-strings-baseline.txt): {entry}")
    return 1 if fail else 0


if __name__ == "__main__":
    sys.exit(main(pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")))
```

  `scripts/ui-strings-baseline.txt`:

```text
# Legacy files exempt from check-ui-strings.sh. Entries may only be removed. Task 16 deletes the last one.
app/src/main/kotlin/app/aria/music/PlayerScreen.kt
```

  `scripts/check-screen-imports.sh`:

```bash
#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Screens depend on :domain interfaces only. :data and :innertube implementations are wired in app/.../di/ alone;
# UI code reaches playback through PlayerController, never the engine. Self-tested by scripts/test_check_screen_imports.py.
set -uo pipefail
root="${1:-.}"
base="$root/app/src/main/kotlin"
[ -d "$base" ] || exit 0
fail=0
while IFS= read -r line; do
  case "$line" in */app/aria/music/di/*) continue;; esac
  echo "FAIL: only the di package may import :data or :innertube: ${line#"$root"/}"; fail=1
done < <(grep -rnE '^import app\.aria\.(data|innertube)\.' "$base" --include=*.kt 2>/dev/null)
if [ -d "$base/app/aria/music/ui" ]; then
  while IFS= read -r line; do
    echo "FAIL: UI code must use PlayerController, not the engine: ${line#"$root"/}"; fail=1
  done < <(grep -rnE '^import app\.aria\.(playback\.|domain\.PlaybackEngine$)' "$base/app/aria/music/ui" --include=*.kt 2>/dev/null)
fi
exit $fail
```

  (`chmod +x` both.) `ci.yml` "Static checks": add after `scripts/check-webview-safety.sh`: `scripts/check-ui-strings.sh` and `scripts/check-screen-imports.sh`.
- [ ] **Step 4:** Push; `verify` green (the real repo passes because `PlayerScreen.kt` is the only literal-bearing file and is baselined; `AriaApp`, `MainActivity` and the `di` files follow the import rule). **Commit** `ci: UI string and screen-import guards`.

---

### Task 11: Image pipeline (Coil 3) with URL policy, size cap and capped disk cache

Slice 2, decision 6.

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/image/{ImageUrlPolicy,ImageSizeCap,AriaImageLoader,AriaImage}.kt`, `docs/providers/google-image-cdn.md`
- Modify: `app/src/main/kotlin/app/aria/music/di/UiModule.kt`, `docs/data-flow.md`, `PRIVACY.md`
- Test: `app/src/test/kotlin/app/aria/music/image/{ImageUrlPolicyTest,ImageSizeCapTest,AriaImageLoaderTest}.kt`

**Interfaces:**
- Consumes: `app.aria.playback.StreamUrlPolicy.isAllowed(url, allowLocal)`; Coil 3 (Task 0 signatures).
- Produces:

```kotlin
object ImageUrlPolicy { fun isAllowed(url: String): Boolean }
object ImageSizeCap { const val MAX_DECODE_PX = 1_024; fun cap(widthPx: Int?, heightPx: Int?): Pair<Int, Int> }
object AriaImageLoader { const val DISK_CACHE_BYTES = 100L * 1024 * 1024; fun diskCacheDir(cacheDir: File): File; fun create(context: Context): ImageLoader }
val LocalAriaImageLoader: ProvidableCompositionLocal<ImageLoader?>
@Composable fun AriaImage(url: String?, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop)
```

**Acceptance Criteria:**
- One place (`ImageUrlPolicy`) decides which image URLs load: https only (via `StreamUrlPolicy`), no custom port, no user info, host on the allowlist (`i.ytimg.com` plus `*.googleusercontent.com`, `*.ggpht.com`, `*.ytimg.com`, matched on a dot boundary). The Coil interceptor rejects anything else with an `ErrorResult` before any network call, and `AriaImage` shows a neutral placeholder for such URLs, for null, and when no loader is provided (secure by default: no loader, no requests).
- Disk cache 100 MB in `cacheDir/image_cache` (the cache directory is never included in Android backup); decoded size capped at 1 024 px per side; memory cache bounded.
- `docs/data-flow.md`, `PRIVACY.md` and a provider record state that image requests reveal the device IP address to Google's image CDN.

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageUrlPolicyTest {
    @Test fun allowsYouTubeImageHosts() {
        for (u in listOf("https://i.ytimg.com/vi/x/hq.jpg", "https://lh3.googleusercontent.com/abc=w544", "https://yt3.ggpht.com/a", "https://yt3.googleusercontent.com/b", "https://i9.ytimg.com/c")) assertTrue(ImageUrlPolicy.isAllowed(u), u)
    }
    @Test fun rejectsEverythingElse() {
        for (u in listOf(
            "http://i.ytimg.com/x", "file:///sdcard/x.jpg", "content://media/external/1", "data:image/png;base64,AAAA", "https://evil.example/x.png",
            "https://evilytimg.com/x", "https://ytimg.com.evil.example/x", "https://i.ytimg.com@evil.example/x", "https://i.ytimg.com:8443/x",
            "https://user:pw@i.ytimg.com/x", "", "not a url", "https:///x",
        )) assertFalse(ImageUrlPolicy.isAllowed(u), u)
    }
    @Test fun hostMatchIsCaseInsensitive() = assertTrue(ImageUrlPolicy.isAllowed("https://I.YTIMG.COM/x"))
}

class ImageSizeCapTest {
    @Test fun capsEachSideAndFillsUnknowns() {
        assertEquals(300 to 200, ImageSizeCap.cap(300, 200))
        assertEquals(1_024 to 1_024, ImageSizeCap.cap(5_000, 9_000))
        assertEquals(1_024 to 1_024, ImageSizeCap.cap(null, null))
        assertEquals(500 to 1_024, ImageSizeCap.cap(500, null))
        assertEquals(1 to 1, ImageSizeCap.cap(0, -3))
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AriaImageLoaderTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun diskCacheIsCappedAndLivesInTheCacheDir() {
        assertEquals(100L * 1024 * 1024, AriaImageLoader.DISK_CACHE_BYTES)
        assertEquals(File("/c/image_cache"), AriaImageLoader.diskCacheDir(File("/c")))
    }

    @Test fun rejectedUrlsNeverReachTheNetwork() = runBlocking {
        val loader = AriaImageLoader.create(ctx)
        for (u in listOf("http://i.ytimg.com/x.jpg", "https://evil.example/x.png", "file:///etc/passwd")) {
            val result = loader.execute(ImageRequest.Builder(ctx).data(u).build())
            assertTrue(u, result is ErrorResult)
            assertTrue(u, (result as ErrorResult).throwable is ImageRejectedException)
        }
        loader.shutdown()
    }
}
```

- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

import app.aria.playback.StreamUrlPolicy
import java.net.URI

/** The one place that decides which remote images may load (UI shell decision 6). */
object ImageUrlPolicy {
    private val exactHosts = setOf("i.ytimg.com")
    private val suffixes = listOf(".ytimg.com", ".googleusercontent.com", ".ggpht.com")

    fun isAllowed(url: String): Boolean {
        if (!StreamUrlPolicy.isAllowed(url, allowLocal = false)) return false
        val uri = try { URI(url) } catch (_: Exception) { return false }
        if (uri.userInfo != null || (uri.port != -1 && uri.port != 443)) return false
        val host = uri.host?.lowercase() ?: return false
        return host in exactHosts || suffixes.any { host.endsWith(it) && host.length > it.length }
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

object ImageSizeCap {
    const val MAX_DECODE_PX = 1_024
    /** Unknown or unbounded sides become the cap; known sides are limited to it and at least 1. */
    fun cap(widthPx: Int?, heightPx: Int?): Pair<Int, Int> =
        (widthPx ?: MAX_DECODE_PX).coerceIn(1, MAX_DECODE_PX) to (heightPx ?: MAX_DECODE_PX).coerceIn(1, MAX_DECODE_PX)
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

import android.content.Context
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.size.Dimension
import coil3.size.Size
import okio.Path.Companion.toOkioPath
import java.io.File

class ImageRejectedException(message: String) : Exception(message)

/** Rejects URLs that fail [ImageUrlPolicy] before any network call and caps the decode size. */
internal class SafeImageInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data !is String || !ImageUrlPolicy.isAllowed(data)) {
            return ErrorResult(image = null, request = chain.request, throwable = ImageRejectedException("Image URL not allowed"))
        }
        val (w, h) = ImageSizeCap.cap((chain.size.width as? Dimension.Pixels)?.px, (chain.size.height as? Dimension.Pixels)?.px)
        return chain.withSize(Size(w, h)).proceed()
    }
}

object AriaImageLoader {
    const val DISK_CACHE_BYTES = 100L * 1024 * 1024
    /** Under cacheDir, which Android never backs up. */
    fun diskCacheDir(cacheDir: File) = File(cacheDir, "image_cache")

    fun create(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(SafeImageInterceptor()); add(OkHttpNetworkFetcherFactory()) }
        .diskCache { DiskCache.Builder().directory(diskCacheDir(context.cacheDir).toOkioPath()).maxSizeBytes(DISK_CACHE_BYTES).build() }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
        .build()
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage

/** No loader provided means no image request is ever made (tests, previews). MainActivity provides the real one. */
val LocalAriaImageLoader: ProvidableCompositionLocal<ImageLoader?> = staticCompositionLocalOf { null }

/** Artwork is decorative in lists (the title is read next to it); pass a description only where the image carries meaning. */
@Composable
fun AriaImage(url: String?, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val loader = LocalAriaImageLoader.current
    if (url == null || loader == null || !ImageUrlPolicy.isAllowed(url)) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    AsyncImage(model = url, contentDescription = contentDescription, imageLoader = loader, modifier = modifier, contentScale = contentScale)
}
```

  `UiModule.kt`: add `@Provides @Singleton fun imageLoader(@ApplicationContext c: Context): coil3.ImageLoader = app.aria.music.image.AriaImageLoader.create(c)`. Docs: create `docs/providers/google-image-cdn.md` (SPDX comment, Tier 2, endpoints `i.ytimg.com`, `*.googleusercontent.com`, `*.ggpht.com`, owner Google, default on, data sent: the image URL and the device IP address plus a standard HTTP user agent; when: a screen shows artwork; allowlist in `ImageUrlPolicy`; cache 100 MB in `cache/image_cache`, never backed up; kill switch: none beyond the YouTube kill switch); `docs/data-flow.md` add row "Google image CDN (artwork) | The image URL; the request reveals the device IP address to Google | Whenever a screen shows artwork | No (core feature)"; `PRIVACY.md` add a sentence that artwork is loaded directly from Google's image servers, which sees your IP address, and cached on the device (100 MB cap, not backed up).
- [ ] **Step 3b (stress-test S4):** in debug builds only (`BuildConfig.DEBUG`), when `ImageUrlPolicy` rejects a URL the interceptor logs the **host only** (never the URL, path or query) at debug level through Timber, so a missing CDN host shows up in the first device run; release builds log nothing. Add a test that a rejected URL produces the placeholder (error result, no network call) and that the log call receives only the host.
- [ ] **Step 4:** Push; `:app` unit tests and the build green (`checkLicense` passes for Coil/okio). **Commit** `feat(app): Coil image pipeline with URL policy and caps`.

---

### Task 12: Shell: theme, icons, navigation, bottom bar, shared states, message host, notification rationale, `app-ui` CI job

Slice 2. The app switches to the new shell; the legacy screen stays as the Search tab until Task 16.

**Files:**
- Create: `app/src/main/res/values/strings.xml`, `plurals.xml`; `app/src/main/kotlin/app/aria/music/ui/{Formatters,AriaTheme}.kt`, `ui/common/{AriaIcons,States}.kt`, `ui/shell/{Routes,AriaShell,AriaNavHost,PlayerMessages,NotificationPermission}.kt`; `app/src/test/kotlin/app/aria/music/ui/{FormattersTest}.kt`, `ui/shell/NotificationPermissionTest.kt`; `app/src/androidTest/kotlin/app/aria/music/ui/{TestSupport,ShellTest,StatesTest}.kt`
- Modify: `app/build.gradle.kts`, `app/src/main/kotlin/app/aria/music/MainActivity.kt`, `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `PlayerController`, `PlayerMessage`, `LocalAriaImageLoader`, `GlassSurface`, legacy `PlayerScreen(search, engine, startService)`.
- Produces:

```kotlin
object Formatters { fun duration(ms: Long, locale: Locale = Locale.getDefault()): String; fun count(n: Int, locale: Locale = Locale.getDefault()): String }
@Composable fun AriaTheme(dynamic: Boolean = true, content: @Composable () -> Unit)
@Serializable data object HomeRoute; @Serializable data object SearchRoute; @Serializable data object LibraryRoute
@Serializable data object NowPlayingRoute; @Serializable data object QueueRoute; @Serializable data object SettingsRoute; @Serializable data object OnboardingRoute
@Serializable data class AlbumRoute(val id: String); @Serializable data class ArtistRoute(val id: String); @Serializable data class PlaylistRoute(val id: String, val local: Boolean)
enum class TopLevel(val route: Any, @StringRes val label: Int, val icon: ImageVector) { HOME, SEARCH, LIBRARY }
@Composable fun AriaShellContent(selected: TopLevel?, onSelect: (TopLevel) -> Unit, snackbarHost: SnackbarHostState, miniPlayer: @Composable () -> Unit, content: @Composable (PaddingValues) -> Unit)
@Composable fun AriaRoot(legacySearch: @Composable () -> Unit)    // NavHost + shell + message host; later tasks register screens in AriaNavHost.kt
fun PlayerMessage.text(resources: Resources): String
object AriaIcons { val Home, Search, Favorite, FavoriteBorder, MoreVert, Close, ArrowBack, PlayArrow, Pause, SkipNext, SkipPrevious, Shuffle, Repeat, RepeatOne, QueueMusic, LibraryMusic, Add, Delete, Check, ArrowUp, ArrowDown, ExpandMore: ImageVector }
@Composable fun LoadingState(modifier); fun EmptyState(@StringRes title: Int, @StringRes body: Int? = null, modifier); fun ErrorState(onRetry: () -> Unit, modifier); fun OfflineState(onRetry: () -> Unit, modifier)
```

**Acceptance Criteria:**
- Bottom bar with Home, Search, Library (labels from resources, items at least 48dp); the mini player slot sits above the bar; Now Playing, Queue, Settings, Onboarding and detail routes hide the bar.
- Player messages show as non-blocking snackbars with the reason (unavailable, offline, other, too many failures, queue windowed, queue full) from resources and plurals.
- A notification-permission rationale dialog appears at the first moment a queue exists on API 33+ when the permission is missing; "Allow" requests it, "Not now" dismisses it for the process; the pure decision function is unit-tested.
- `Formatters` are locale-aware (German grouping, hours when needed).
- The `app-ui` CI job runs `app.aria.music.ui` Compose tests on API 26 and 34; `playback-service` keeps running the rest; debug builds enable the `en-XA` pseudolocale; `AriaTestTheme(pseudo, fontScale)` lets any test render under `en-XA` and font scale 2.0.
- The app starts into the shell with Home and Library placeholders (string resources) and the legacy screen as Search; `./gradlew build` lint passes.

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {
    @Test fun durations() {
        assertEquals("0:00", Formatters.duration(0)); assertEquals("0:07", Formatters.duration(7_400)); assertEquals("3:07", Formatters.duration(187_000))
        assertEquals("1:02:03", Formatters.duration(3_723_000)); assertEquals("0:00", Formatters.duration(-5))
    }
    @Test fun countsFollowTheLocale() {
        assertEquals("1,234", Formatters.count(1_234, Locale.US)); assertEquals("1.234", Formatters.count(1_234, Locale.GERMANY))
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationPermissionTest {
    @Test fun explainsOnlyWhenNeededOnceOnApi33Plus() {
        assertEquals(true, NotificationPermissionPolicy.shouldExplain(sdk = 34, granted = false, alreadyAsked = false, hasQueue = true))
        assertEquals(false, NotificationPermissionPolicy.shouldExplain(33, true, false, true))
        assertEquals(false, NotificationPermissionPolicy.shouldExplain(34, false, true, true))
        assertEquals(false, NotificationPermissionPolicy.shouldExplain(34, false, false, false))
        assertEquals(false, NotificationPermissionPolicy.shouldExplain(32, false, false, true))
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import java.util.Locale

/** Test wrapper: optional en-XA pseudolocale (long accented strings) and font scale, on the app theme without dynamic color. */
@Composable
fun AriaTestTheme(pseudo: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val cfg = remember(pseudo, fontScale) {
        Configuration(base.resources.configuration).apply { if (pseudo) setLocale(Locale.forLanguageTag("en-XA")); this.fontScale = fontScale }
    }
    val ctx = remember(cfg) { base.createConfigurationContext(cfg) }
    CompositionLocalProvider(
        LocalContext provides ctx, LocalConfiguration provides cfg, LocalDensity provides Density(ctx.resources.displayMetrics.density, fontScale),
    ) { AriaTheme(dynamic = false, content = content) }
}
```

  (`TestSupport.kt` in `app/src/androidTest/kotlin/app/aria/music/ui/`, package `app.aria.music.ui`.)

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.aria.music.ui.shell.AriaShellContent
import app.aria.music.ui.shell.TopLevel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShellTest {
    @get:Rule val rule = createComposeRule()

    private fun show(selected: TopLevel? = TopLevel.HOME, pseudo: Boolean = false, fontScale: Float = 1f, onSelect: (TopLevel) -> Unit = {}) = rule.setContent {
        AriaTestTheme(pseudo, fontScale) {
            AriaShellContent(selected, onSelect, SnackbarHostState(), miniPlayer = { Text("MINI", modifier = androidx.compose.ui.Modifier.testTag("mini")) }) { Text("BODY") }
        }
    }

    @Test fun barShowsThreeTabsAndReportsSelection() {
        val picked = mutableListOf<TopLevel>(); show(onSelect = { picked += it })
        for (t in TopLevel.entries) rule.onNodeWithTag("tab:${t.name}").assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        rule.onNodeWithTag("tab:SEARCH").performClick(); rule.onNodeWithTag("tab:LIBRARY").performClick()
        assertEquals(listOf(TopLevel.SEARCH, TopLevel.LIBRARY), picked)
        rule.onNodeWithTag("tab:HOME").assertIsSelected()
    }

    @Test fun miniPlayerSitsAboveTheBar() {
        show()
        val mini = rule.onNodeWithTag("mini").getUnclippedBoundsInRoot(); val tab = rule.onNodeWithTag("tab:HOME").getUnclippedBoundsInRoot()
        assert(mini.bottom <= tab.top) { "mini player $mini must be above tabs $tab" }
    }

    @Test fun barIsHiddenOnFullScreenRoutes() { show(selected = null); rule.onNodeWithTag("tab:HOME").assertDoesNotExist(); rule.onNodeWithText("BODY").assertIsDisplayed() }

    @Test fun survivesPseudolocaleAndBigFonts() {
        show(pseudo = true, fontScale = 2f)
        for (t in TopLevel.entries) rule.onNodeWithTag("tab:${t.name}").assertIsDisplayed()
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.aria.music.R
import app.aria.music.ui.common.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StatesTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun errorAndOfflineOfferRetry() {
        var retries = 0
        rule.setContent { AriaTestTheme { ErrorState(onRetry = { retries++ }) } }
        rule.onNodeWithText(str(R.string.error_title)).assertIsDisplayed(); rule.onNodeWithText(str(R.string.retry)).performClick()
        assertEquals(1, retries)
    }
    @Test fun offlineAndEmptyAndLoadingRender() {
        rule.setContent { AriaTestTheme(pseudo = true) { OfflineState(onRetry = {}) } }
        rule.onNodeWithTag(TAG_OFFLINE).assertIsDisplayed()
    }
    @Test fun loadingHasADescription() {
        rule.setContent { AriaTestTheme { LoadingState() } }
        rule.onNodeWithContentDescription(str(R.string.loading)).assertIsDisplayed()
    }
}
```

- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.** `app/build.gradle.kts`: in `buildTypes { debug { ... } }` add `isPseudoLocalesEnabled = true`; dependencies `androidTestImplementation(libs.compose.ui.test.junit4)` and `debugImplementation(libs.compose.ui.test.manifest)`. Resources, `values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- SPDX-License-Identifier: GPL-3.0-only -->
<resources>
    <string name="nav_home">Home</string>
    <string name="nav_search">Search</string>
    <string name="nav_library">Library</string>
    <string name="home_placeholder">Home is coming soon.</string>
    <string name="library_placeholder">Library is coming soon.</string>
    <string name="loading">Loading</string>
    <string name="error_title">Something went wrong</string>
    <string name="error_body">That did not load. Try again.</string>
    <string name="retry">Retry</string>
    <string name="offline_title">You are offline</string>
    <string name="offline_body">Check your connection and try again.</string>
    <string name="artist_separator">, </string>
    <string name="msg_skipped_unavailable">Skipped %1$s: not available</string>
    <string name="msg_skipped_network">Skipped %1$s: network error</string>
    <string name="msg_skipped_other">Skipped %1$s: could not play</string>
    <string name="msg_too_many_failures">Stopped after several tracks failed in a row</string>
    <string name="msg_queue_windowed">Queued %1$s of %2$s songs, starting from the one you tapped</string>
    <string name="notif_title">Show playback controls?</string>
    <string name="notif_body">Android needs your permission to show the player in the notification shade and on the lock screen. Music plays either way.</string>
    <string name="notif_allow">Allow</string>
    <string name="notif_not_now">Not now</string>
</resources>
```

  `plurals.xml`: `<plurals name="msg_queue_full"><item quantity="one">%1$d song did not fit in the queue (limit 500)</item><item quantity="other">%1$d songs did not fit in the queue (limit 500)</item></plurals>`. `Formatters.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import java.text.NumberFormat
import java.util.Locale

object Formatters {
    fun duration(ms: Long, locale: Locale = Locale.getDefault()): String {
        val total = (ms / 1000).coerceAtLeast(0); val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) String.format(locale, "%d:%02d:%02d", h, m, s) else String.format(locale, "%d:%02d", m, s)
    }
    fun count(n: Int, locale: Locale = Locale.getDefault()): String = NumberFormat.getIntegerInstance(locale).format(n)
}
```

  `AriaTheme.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun AriaTheme(dynamic: Boolean = true, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        val c = LocalContext.current; if (dark) dynamicDarkColorScheme(c) else dynamicLightColorScheme(c)
    } else if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme, content = content)
}
```

  `ui/common/AriaIcons.kt` (own vectors: no icon library dependency; path data from the Material icon set):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object AriaIcons {
    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black)).build()

    val Home = icon("Home", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
    val Search = icon("Search", "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z")
    val Favorite = icon("Favorite", "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z")
    val FavoriteBorder = icon("FavoriteBorder", "M16.5,3c-1.74,0 -3.41,0.81 -4.5,2.09C10.91,3.81 9.24,3 7.5,3 4.42,3 2,5.42 2,8.5c0,3.78 3.4,6.86 8.55,11.54L12,21.35l1.45,-1.32C18.6,15.36 22,12.28 22,8.5 22,5.42 19.58,3 16.5,3zM12.1,18.55l-0.1,0.1 -0.1,-0.1C7.14,14.24 4,11.39 4,8.5 4,6.5 5.5,5 7.5,5c1.54,0 3.04,0.99 3.57,2.36h1.87C13.46,5.99 14.96,5 16.5,5c2,0 3.5,1.5 3.5,3.5 0,2.89 -3.14,5.74 -7.9,10.05z")
    val MoreVert = icon("MoreVert", "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z")
    val Close = icon("Close", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    val ArrowBack = icon("ArrowBack", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    val PlayArrow = icon("PlayArrow", "M8,5v14l11,-7z")
    val Pause = icon("Pause", "M6,19h4V5H6v14zm8,-14v14h4V5h-4z")
    val SkipNext = icon("SkipNext", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")
    val SkipPrevious = icon("SkipPrevious", "M6,6h2v12H6zm3.5,6l8.5,6V6z")
    val Shuffle = icon("Shuffle", "M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5V4h-5.5zM14.83,13.41l-1.41,1.41 3.13,3.13L14.5,20H20v-5.5l-2.04,2.04 -3.13,-3.13z")
    val Repeat = icon("Repeat", "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z")
    val RepeatOne = icon("RepeatOne", "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4zM13,15V9h-1l-2,1v1h1.5v4H13z")
    val QueueMusic = icon("QueueMusic", "M15,6H3v2h12V6zM15,10H3v2h12v-2zM3,16h8v-2H3v2zM17,6v8.18c-0.31,-0.11 -0.65,-0.18 -1,-0.18 -1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3V8h3V6h-5z")
    val LibraryMusic = icon("LibraryMusic", "M20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM18,6h-3v5.5c0,1.38 -1.12,2.5 -2.5,2.5S10,12.88 10,11.5s1.12,-2.5 2.5,-2.5c0.57,0 1.08,0.19 1.5,0.51V5h4v2zM4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6z")
    val Add = icon("Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val Delete = icon("Delete", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
    val Check = icon("Check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
    val ArrowUp = icon("ArrowUp", "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8 -8,8z")
    val ArrowDown = icon("ArrowDown", "M20,12l-1.41,-1.41L13,16.17V4h-2v12.17l-5.58,-5.59L4,12l8,8 8,-8z")
    val ExpandMore = icon("ExpandMore", "M7.41,8.59L12,13.17l4.59,-4.58L18,10l-6,6 -6,-6 1.41,-1.41z")
}
```

  `ui/common/States.kt`: `const val TAG_OFFLINE = "state:offline"`, `TAG_ERROR = "state:error"`, `TAG_EMPTY = "state:empty"`, `TAG_LOADING = "state:loading"`; composables: `LoadingState` = `Box(fillMaxSize, center) { CircularProgressIndicator(Modifier.semantics { contentDescription = stringResource(R.string.loading) }) }` with the tag; `EmptyState(title, body, modifier)` = centered `Column { Text(stringResource(title), style = titleMedium); body?.let { Text(stringResource(it)) } }`; `ErrorState(onRetry)` = column with `Text(stringResource(R.string.error_title))`, body, `Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }`; `OfflineState(onRetry)` same with offline strings. Every composable `Modifier.fillMaxSize().padding(24.dp)` and the tag via `testTag`.

  `ui/shell/Routes.kt`: the routes from Interfaces (with `import kotlinx.serialization.Serializable`) and

```kotlin
enum class TopLevel(val route: Any, @StringRes val label: Int, val icon: ImageVector) {
    HOME(HomeRoute, R.string.nav_home, AriaIcons.Home),
    SEARCH(SearchRoute, R.string.nav_search, AriaIcons.Search),
    LIBRARY(LibraryRoute, R.string.nav_library, AriaIcons.LibraryMusic),
}
```

  `AriaShell.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

@Composable
fun AriaShellContent(
    selected: TopLevel?, onSelect: (TopLevel) -> Unit, snackbarHost: SnackbarHostState,
    miniPlayer: @Composable () -> Unit, content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            Column {
                miniPlayer()
                if (selected != null) NavigationBar {
                    TopLevel.entries.forEach { t ->
                        NavigationBarItem(
                            selected = t == selected, onClick = { onSelect(t) },
                            icon = { Icon(t.icon, contentDescription = null) }, label = { Text(stringResource(t.label)) },
                            modifier = Modifier.testTag("tab:${t.name}").defaultMinSize(minHeight = 48.dp),
                        )
                    }
                }
            }
        },
        content = content,
    )
}
```

  (imports: `androidx.compose.foundation.layout.*`, `androidx.compose.material3.*`, `androidx.compose.runtime.Composable`, `androidx.compose.ui.Modifier`, `androidx.compose.ui.platform.testTag`, `androidx.compose.ui.res.stringResource`, `androidx.compose.ui.unit.dp`, `app.aria.music.R`, `app.aria.music.ui.common.AriaIcons`.) `PlayerMessages.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

import android.content.res.Resources
import app.aria.music.R
import app.aria.music.player.PlayerMessage
import app.aria.music.player.SkipReason
import app.aria.music.ui.Formatters

fun PlayerMessage.text(r: Resources): String = when (this) {
    is PlayerMessage.TrackSkipped -> r.getString(
        when (reason) { SkipReason.UNAVAILABLE -> R.string.msg_skipped_unavailable; SkipReason.NETWORK -> R.string.msg_skipped_network; SkipReason.OTHER -> R.string.msg_skipped_other }, title,
    )
    PlayerMessage.TooManyFailures -> r.getString(R.string.msg_too_many_failures)
    is PlayerMessage.QueueWindowed -> r.getString(R.string.msg_queue_windowed, Formatters.count(queued), Formatters.count(total))
    is PlayerMessage.QueueFull -> r.getQuantityString(R.plurals.msg_queue_full, dropped, dropped)
}
```

  `NotificationPermission.kt`: `object NotificationPermissionPolicy { fun shouldExplain(sdk: Int, granted: Boolean, alreadyAsked: Boolean, hasQueue: Boolean) = sdk >= 33 && !granted && !alreadyAsked && hasQueue }` and

```kotlin
@Composable
fun NotificationPermissionGate(hasQueue: Boolean) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val granted = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    if (NotificationPermissionPolicy.shouldExplain(Build.VERSION.SDK_INT, granted, asked, hasQueue)) {
        AlertDialog(
            onDismissRequest = { asked = true },
            title = { Text(stringResource(R.string.notif_title)) }, text = { Text(stringResource(R.string.notif_body)) },
            confirmButton = { TextButton(onClick = { asked = true; launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.notif_allow)) } },
            dismissButton = { TextButton(onClick = { asked = true }) { Text(stringResource(R.string.notif_not_now)) } },
        )
    }
}
```

  `AriaNavHost.kt` and the root:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

@Composable
fun AriaRoot(legacySearch: @Composable () -> Unit, controller: PlayerController = hiltViewModel<ShellViewModel>().controller) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    val ui by controller.ui.collectAsStateWithLifecycle()
    LaunchedEffect(controller) { controller.messages.collect { snackbar.showSnackbar(it.text(resources)) } }
    NotificationPermissionGate(hasQueue = ui.hasQueue)
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val selected = TopLevel.entries.firstOrNull { t -> dest?.hierarchy?.any { it.hasRoute(t.route::class) } == true }
    AriaShellContent(
        selected = selected,
        onSelect = { t -> nav.navigate(t.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
        snackbarHost = snackbar,
        miniPlayer = { /* Task 13 */ },
    ) { padding -> AriaNavHost(nav, legacySearch, Modifier.padding(padding)) }
}

@HiltViewModel class ShellViewModel @Inject constructor(val controller: PlayerController) : ViewModel()

@Composable
fun AriaNavHost(nav: NavHostController, legacySearch: @Composable () -> Unit, modifier: Modifier) {
    NavHost(nav, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> { Placeholder(R.string.home_placeholder) }
        composable<SearchRoute> { legacySearch() }
        composable<LibraryRoute> { Placeholder(R.string.library_placeholder) }
    }
}

@Composable private fun Placeholder(@StringRes text: Int) = EmptyState(title = text)
```

  (The `/* Task 13 */` comment is the only forward reference; Task 13 replaces it and a grep in Task 24 checks it is gone.) `MainActivity.kt`:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var search: SearchSource
    @Inject lateinit var engine: PlaybackEngine
    @Inject lateinit var imageLoader: ImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AriaTheme {
                CompositionLocalProvider(LocalAriaImageLoader provides imageLoader) {
                    AriaRoot(legacySearch = { PlayerScreen(search, engine) { AriaPlaybackService.start(this@MainActivity) } })
                }
            }
        }
    }
}
```

  (`MainActivity` is outside `ui/` so the import rule allows `PlaybackEngine`; the legacy screen is deleted in Task 16.) CI: add job `app-ui` to `ci.yml` (copy `glass-ui`, same pinned actions and KVM step; `script:` is):

```yaml
          script: scripts/ci-gradle.sh :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=app.aria.music.ui
```

  and change the `playback-service` script to `scripts/ci-gradle.sh :playback:connectedDebugAndroidTest :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notPackage=app.aria.music.ui`. If `package=` does not include sub-packages on the runner in use, list the sub-packages comma-separated.
- [ ] **Step 4:** Push; iterate until `verify`, `app-ui` (API 26, 34) and `playback-service` are green. **Commit** `feat(app): navigation shell, theme, shared states and message host`.

---

### Task 13: Mini player

Slice 2. Persistent, above the bar, expands to Now Playing.

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/ui/miniplayer/{MiniPlayer,MiniPlayerViewModel}.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/kotlin/app/aria/music/ui/shell/AriaNavHost.kt` (AriaRoot slot)
- Test: `app/src/test/kotlin/app/aria/music/ui/miniplayer/MiniPlayerViewModelTest.kt`, `app/src/androidTest/kotlin/app/aria/music/ui/miniplayer/MiniPlayerTest.kt`

**Interfaces:**
- Consumes: `PlayerController` (`ui`, `positionMs`, `togglePlayPause`, `next`), `AriaImage`, `GlassSurface`, `Formatters`, `AriaIcons`.
- Produces:

```kotlin
sealed interface MiniPlayerUiState {
    data object Hidden : MiniPlayerUiState
    data class Showing(val title: String, val artists: List<String>, val thumbnailUrl: String?, val isPlaying: Boolean, val status: PlayerStatus, val durationMs: Long) : MiniPlayerUiState
}
@HiltViewModel class MiniPlayerViewModel @Inject constructor(controller: PlayerController) : ViewModel() { val state: StateFlow<MiniPlayerUiState>; val positionMs: StateFlow<Long>; fun toggle(); fun next() }
const val TAG_MINI_PLAYER = "miniplayer"; TAG_MINI_PLAY = "miniplayer:play"; TAG_MINI_NEXT = "miniplayer:next"
@Composable fun MiniPlayerContent(state: MiniPlayerUiState.Showing, positionMs: () -> Long, onToggle: () -> Unit, onNext: () -> Unit, onExpand: () -> Unit, modifier: Modifier = Modifier)
@Composable fun MiniPlayerHost(onExpand: () -> Unit, viewModel: MiniPlayerViewModel = hiltViewModel())
```

**Acceptance Criteria:**
- Hidden without a queue; otherwise shows artwork, title, artists, a thin progress line, play/pause and next. Only the progress line reads the position flow.
- The whole row is one 48dp+ target announced as "Open now playing: <title>"; play/pause has the description "Play" or "Pause"; an error status shows the string "Playback problem" as a state description; the surface is `GlassSurface` (fallback on API 26-30).
- ViewModel tests and Compose tests pass; the Compose tests also render under `en-XA` and font scale 2.0.

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.miniplayer

import app.aria.domain.*
import app.aria.domain.testing.FakePlaybackEngine
import app.aria.domain.testing.Samples
import app.aria.music.player.PlayerController
import app.aria.music.player.ServiceLauncher
import app.aria.music.player.PlayerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MiniPlayerViewModelTest {
    @BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private fun TestScope.vm(engine: FakePlaybackEngine): MiniPlayerViewModel {
        val vm = MiniPlayerViewModel(PlayerController(engine, ServiceLauncher { }, backgroundScope))
        backgroundScope.launch { vm.state.collect { } }; return vm
    }

    @Test fun hiddenWithoutAQueue() = runTest { assertEquals(MiniPlayerUiState.Hidden, vm(FakePlaybackEngine()).state.value) }

    @Test fun showsTheCurrentTrack() = runTest {
        val engine = FakePlaybackEngine(); val vm = vm(engine)
        engine.mutableState.value = PlaybackState(queue = listOf(QueueItem(1, Samples.track(1))), currentIndex = 0, status = PlaybackStatus.Playing, durationMs = 180_000)
        runCurrent()
        val s = vm.state.value as MiniPlayerUiState.Showing
        assertEquals("Song 1", s.title); assertEquals(listOf("Artist 1"), s.artists); assertTrue(s.isPlaying); assertEquals(PlayerStatus.PLAYING, s.status); assertEquals(180_000L, s.durationMs)
    }

    @Test fun toggleAndNextDelegate() = runTest {
        val engine = FakePlaybackEngine(); val vm = vm(engine)
        engine.mutableState.value = PlaybackState(queue = listOf(QueueItem(1, Samples.track(1))), currentIndex = 0, status = PlaybackStatus.Paused); runCurrent()
        vm.toggle(); vm.next()
        assertEquals(listOf("play", "next"), engine.calls)
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.miniplayer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.aria.music.R
import app.aria.music.player.PlayerStatus
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MiniPlayerTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private fun state(playing: Boolean = false, status: PlayerStatus = if (playing) PlayerStatus.PLAYING else PlayerStatus.PAUSED) =
        MiniPlayerUiState.Showing("Title", listOf("A", "B"), null, playing, status, 200_000)

    private fun show(s: MiniPlayerUiState.Showing, pseudo: Boolean = false, fontScale: Float = 1f, toggle: () -> Unit = {}, next: () -> Unit = {}, expand: () -> Unit = {}) =
        rule.setContent { AriaTestTheme(pseudo, fontScale) { MiniPlayerContent(s, { 50_000L }, toggle, next, expand) } }

    @Test fun showsTitleArtistsAndDescribesPlayPause() {
        show(state(playing = false)); rule.onNodeWithText("Title").assertIsDisplayed(); rule.onNodeWithText("A, B").assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.action_play)).assertIsDisplayed()
    }
    @Test fun playingShowsPause() { show(state(playing = true)); rule.onNodeWithContentDescription(str(R.string.action_pause)).assertIsDisplayed() }
    @Test fun controlsAreAtLeast48dpAndInvoke() {
        var t = 0; var n = 0; var e = 0; show(state(), toggle = { t++ }, next = { n++ }, expand = { e++ })
        rule.onNodeWithTag(TAG_MINI_PLAY).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag(TAG_MINI_NEXT).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithContentDescription(str(R.string.mini_open_now_playing, "Title")).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf(1, 1, 1), listOf(t, n, e))
    }
    @Test fun errorStatusIsAnnounced() { show(state(status = PlayerStatus.ERROR)); rule.onNodeWithTag(TAG_MINI_PLAYER).assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.StateDescription)) }
    @Test fun survivesPseudolocaleAndBigFonts() { show(state(), pseudo = true, fontScale = 2f); rule.onNodeWithTag(TAG_MINI_PLAY).assertIsDisplayed(); rule.onNodeWithTag(TAG_MINI_NEXT).assertIsDisplayed() }
}
```

- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.** Strings: `action_play` "Play", `action_pause` "Pause", `action_next` "Next", `action_previous` "Previous", `mini_open_now_playing` "Open now playing: %1$s", `state_playback_problem` "Playback problem", `state_waiting_for_network` "Waiting for network", `state_buffering` "Buffering". ViewModel:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.miniplayer

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(private val controller: PlayerController) : ViewModel() {
    val state: StateFlow<MiniPlayerUiState> = controller.ui.map { ui ->
        val item = ui.current
        if (item == null) MiniPlayerUiState.Hidden
        else MiniPlayerUiState.Showing(item.track.title, item.track.artists, item.track.thumbnailUrl, ui.isPlaying, ui.status, ui.durationMs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MiniPlayerUiState.Hidden)
    val positionMs: StateFlow<Long> = controller.positionMs
    fun toggle() = controller.togglePlayPause()
    fun next() = controller.next()
}
```

  `MiniPlayer.kt`: `MiniPlayerContent` is

```kotlin
@Composable
fun MiniPlayerContent(state: MiniPlayerUiState.Showing, positionMs: () -> Long, onToggle: () -> Unit, onNext: () -> Unit, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    val problem = stringResource(R.string.state_playback_problem)
    val open = stringResource(R.string.mini_open_now_playing, state.title)
    GlassSurface(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag(TAG_MINI_PLAYER).semantics {
        if (state.status == PlayerStatus.ERROR) stateDescription = problem
    }, shape = RoundedCornerShape(16.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics(mergeDescendants = false) {}, verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).heightIn(min = 56.dp).clickable(onClickLabel = open, role = Role.Button, onClick = onExpand).semantics { contentDescription = open },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AriaImage(state.thumbnailUrl, null, Modifier.padding(8.dp).size(40.dp).clip(RoundedCornerShape(6.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text(state.artists.joinToString(stringResource(R.string.artist_separator)), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(48.dp).testTag(TAG_MINI_PLAY)) {
                    Icon(if (state.isPlaying) AriaIcons.Pause else AriaIcons.PlayArrow, contentDescription = stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp).testTag(TAG_MINI_NEXT)) { Icon(AriaIcons.SkipNext, contentDescription = stringResource(R.string.action_next)) }
            }
            LinearProgressIndicator(progress = { if (state.durationMs <= 0) 0f else (positionMs().toFloat() / state.durationMs).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun MiniPlayerHost(onExpand: () -> Unit, viewModel: MiniPlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val position = viewModel.positionMs.collectAsStateWithLifecycle()
    (state as? MiniPlayerUiState.Showing)?.let { MiniPlayerContent(it, { position.value }, viewModel::toggle, viewModel::next, onExpand) }
}
```

  (The row-level `clickable` merges title and artwork into the expand target; `positionMs` is read inside the progress lambda, so only that draw scope re-reads it.) `AriaRoot`: replace `miniPlayer = { /* Task 13 */ }` with `miniPlayer = { if (selected != null || dest?.hasRoute<QueueRoute>() == true) MiniPlayerHost(onExpand = { nav.navigate(NowPlayingRoute) }) }` (hidden on Now Playing itself).
- [ ] **Step 4:** Push; green on `verify` and `app-ui`. **Commit** `feat(app): mini player`.

---

### Task 14: Now Playing (Material skin)

Slice 2. One shared state, skin-independent actions (the Apple skin in Task 23 reuses `NowPlayingUiState` and `NowPlayingActions`).

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/ui/nowplaying/{NowPlayingState,NowPlayingViewModel,NowPlayingScreen,MaterialNowPlaying}.kt`
- Modify: `app/src/main/res/values/strings.xml`, `plurals.xml`, `ui/shell/AriaNavHost.kt` (register `NowPlayingRoute`)
- Test: `app/src/test/kotlin/app/aria/music/ui/nowplaying/NowPlayingViewModelTest.kt`, `app/src/androidTest/kotlin/app/aria/music/ui/nowplaying/NowPlayingTest.kt`

**Interfaces:**
- Consumes: `PlayerController`, `LibraryRepository.isLiked/setLiked`, `SettingsRepository.settings` (`sleepTimerDefaultMinutes`), shell components.
- Produces:

```kotlin
sealed interface NowPlayingUiState {
    data object Empty : NowPlayingUiState
    data class Content(
        val track: Track, val status: PlayerStatus, val durationMs: Long, val shuffle: Boolean, val repeat: RepeatMode,
        val sleepTimer: SleepTimer?, val liked: Boolean, val queueSize: Int, val queueIndex: Int, val sleepDefaultMinutes: Int,
    ) : NowPlayingUiState
}
data class NowPlayingActions(
    val onBack: () -> Unit, val onToggle: () -> Unit, val onNext: () -> Unit, val onPrevious: () -> Unit, val onSeek: (Long) -> Unit,
    val onShuffle: (Boolean) -> Unit, val onRepeat: () -> Unit, val onLike: () -> Unit, val onSleepMinutes: (Int) -> Unit,
    val onSleepEndOfTrack: () -> Unit, val onSleepOff: () -> Unit, val onOpenQueue: () -> Unit,
)
@HiltViewModel class NowPlayingViewModel @Inject constructor(controller: PlayerController, library: LibraryRepository, settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<NowPlayingUiState>; val positionMs: StateFlow<Long>; val actions: (onBack, onOpenQueue) -> NowPlayingActions }
@Composable fun NowPlayingContent(state: NowPlayingUiState, positionMs: () -> Long, actions: NowPlayingActions, modifier: Modifier = Modifier)   // Material skin
@Composable fun NowPlayingScreen(onBack: () -> Unit, onOpenQueue: () -> Unit, viewModel: NowPlayingViewModel = hiltViewModel())
@Composable fun LyricsSlot(modifier: Modifier = Modifier)     // empty until sub-project 4
tags: TAG_NP_PLAY, TAG_NP_SEEK, TAG_NP_LIKE, TAG_NP_QUEUE, TAG_NP_SLEEP, TAG_NP_ERROR, TAG_NP_OFFLINE, TAG_NP_LYRICS
```

**Acceptance Criteria:**
- States: `Empty` (nothing playing: empty state with a back button); `Content` with `status` driving loading (spinner on the play button while `BUFFERING`), offline banner (`WAITING_FOR_NETWORK`, tag `TAG_NP_OFFLINE`) and error banner (`ERROR`, tag `TAG_NP_ERROR`, with Retry that calls play).
- Controls: play/pause, previous, next, seek (slider with a state description "1:23 of 3:45"), shuffle (toggle, on/off in the description), repeat (cycles, description names the mode), like (description "Like" or "Remove like"), sleep timer menu (default minutes from settings, end of track, off; the label shows the active timer), queue button. All at least 48dp, all with resource descriptions.
- Position is read only inside the seek bar and its time labels; `liked` follows the library; the lyrics slot exists and is empty.
- Compose tests pass at font scale 2.0 and under `en-XA`.

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.nowplaying

import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModelTest {
    @BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private class Env(scope: kotlinx.coroutines.CoroutineScope) {
        val engine = FakePlaybackEngine(); val library = FakeLibraryRepository(); val settings = FakeSettingsRepository(AppSettings(sleepTimerDefaultMinutes = 20))
        val vm = NowPlayingViewModel(PlayerController(engine, ServiceLauncher { }, scope), library, settings)
        init { scope.launch { vm.state.collect { } } }
        fun play(status: PlaybackStatus = PlaybackStatus.Playing) {
            engine.mutableState.value = PlaybackState(queue = Samples.tracks(3).mapIndexed { i, t -> QueueItem(i + 1L, t) }, currentIndex = 1, status = status, durationMs = 180_000, shuffle = true, repeat = RepeatMode.ALL)
        }
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
        assertEquals(listOf(Samples.track(2)), e.library.songs("", LibrarySort.TITLE).let { kotlinx.coroutines.flow.first(it) }.map { it.track })
        a.onLike(); runCurrent()
        assertFalse((e.vm.state.value as NowPlayingUiState.Content).liked)
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

    @Test fun errorAndOfflineStatusesSurface() = runTest {
        val e = Env(backgroundScope); e.play(PlaybackStatus.WaitingForNetwork); runCurrent()
        assertEquals(PlayerStatus.WAITING_FOR_NETWORK, (e.vm.state.value as NowPlayingUiState.Content).status)
        e.play(PlaybackStatus.Error("x")); runCurrent()
        assertEquals(PlayerStatus.ERROR, (e.vm.state.value as NowPlayingUiState.Content).status)
    }
}
```

  (Replace the awkward `kotlinx.coroutines.flow.first(it)` call with a normal `import kotlinx.coroutines.flow.first` and `e.library.songs("", LibrarySort.TITLE).first()` when typing.)

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.nowplaying

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.aria.domain.*
import app.aria.music.R
import app.aria.music.player.PlayerStatus
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NowPlayingTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val track = Track(TrackId.youtube("dQw4w9WgXcQ"), "Title", listOf("Artist"), 200_000, null)
    private fun content(status: PlayerStatus = PlayerStatus.PAUSED, liked: Boolean = false, shuffle: Boolean = false, repeat: RepeatMode = RepeatMode.OFF, sleep: SleepTimer? = null) =
        NowPlayingUiState.Content(track, status, 200_000, shuffle, repeat, sleep, liked, 3, 0, 15)
    private class Calls { val log = mutableListOf<String>() }
    private fun actions(c: Calls) = NowPlayingActions(
        onBack = { c.log += "back" }, onToggle = { c.log += "toggle" }, onNext = { c.log += "next" }, onPrevious = { c.log += "previous" }, onSeek = { c.log += "seek:$it" },
        onShuffle = { c.log += "shuffle:$it" }, onRepeat = { c.log += "repeat" }, onLike = { c.log += "like" }, onSleepMinutes = { c.log += "sleep:$it" },
        onSleepEndOfTrack = { c.log += "sleepEnd" }, onSleepOff = { c.log += "sleepOff" }, onOpenQueue = { c.log += "queue" },
    )
    private fun show(s: NowPlayingUiState, c: Calls = Calls(), pseudo: Boolean = false, fontScale: Float = 1f) =
        rule.setContent { AriaTestTheme(pseudo, fontScale) { NowPlayingContent(s, { 65_000L }, actions(c)) } }

    @Test fun controlsHaveDescriptionsAndMinimumSize() {
        show(content())
        for (id in listOf(R.string.action_play, R.string.action_next, R.string.action_previous, R.string.np_like, R.string.np_queue, R.string.np_shuffle_off, R.string.np_repeat_off))
            rule.onNodeWithContentDescription(str(id)).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }
    @Test fun stateTogglesChangeDescriptions() {
        show(content(PlayerStatus.PLAYING, liked = true, shuffle = true, repeat = RepeatMode.ONE))
        for (id in listOf(R.string.action_pause, R.string.np_unlike, R.string.np_shuffle_on, R.string.np_repeat_one)) rule.onNodeWithContentDescription(str(id)).assertIsDisplayed()
    }
    @Test fun clicksReachTheActions() {
        val c = Calls(); show(content(), c)
        rule.onNodeWithTag(TAG_NP_PLAY).performClick(); rule.onNodeWithContentDescription(str(R.string.action_next)).performClick()
        rule.onNodeWithContentDescription(str(R.string.action_previous)).performClick(); rule.onNodeWithTag(TAG_NP_LIKE).performClick(); rule.onNodeWithTag(TAG_NP_QUEUE).performClick()
        rule.onNodeWithContentDescription(str(R.string.np_shuffle_off)).performClick(); rule.onNodeWithContentDescription(str(R.string.np_repeat_off)).performClick()
        assertEquals(listOf("toggle", "next", "previous", "like", "queue", "shuffle:true", "repeat"), c.log)
    }
    @Test fun sleepMenuOffersDefaultEndOfTrackAndOff() {
        val c = Calls(); show(content(), c)
        rule.onNodeWithTag(TAG_NP_SLEEP).performClick(); rule.onNodeWithText(str(R.string.np_sleep_in_minutes, 15)).performClick()
        rule.onNodeWithTag(TAG_NP_SLEEP).performClick(); rule.onNodeWithText(str(R.string.np_sleep_end_of_track)).performClick()
        rule.onNodeWithTag(TAG_NP_SLEEP).performClick(); rule.onNodeWithText(str(R.string.np_sleep_off)).performClick()
        assertEquals(listOf("sleep:15", "sleepEnd", "sleepOff"), c.log)
    }
    @Test fun seekBarDescribesPosition() {
        show(content()); rule.onNodeWithTag(TAG_NP_SEEK).assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.StateDescription))
        rule.onNodeWithText("1:05").assertIsDisplayed(); rule.onNodeWithText("3:20").assertIsDisplayed()
    }
    @Test fun errorOfferingRetryAndOfflineBanner() {
        val c = Calls(); show(content(PlayerStatus.ERROR), c)
        rule.onNodeWithTag(TAG_NP_ERROR).assertIsDisplayed(); rule.onNodeWithText(str(R.string.retry)).performClick(); assertEquals(listOf("toggle"), c.log)
    }
    @Test fun offlineBannerShows() { show(content(PlayerStatus.WAITING_FOR_NETWORK)); rule.onNodeWithTag(TAG_NP_OFFLINE).assertIsDisplayed() }
    @Test fun emptyStateHasABackButton() {
        val c = Calls(); show(NowPlayingUiState.Empty, c); rule.onNodeWithContentDescription(str(R.string.back)).performClick(); assertEquals(listOf("back"), c.log)
    }
    @Test fun lyricsSlotExistsButIsEmpty() { show(content()); rule.onNodeWithTag(TAG_NP_LYRICS).assertExists() }
    @Test fun survivesPseudolocaleAndBigFonts() {
        show(content(PlayerStatus.PLAYING, sleep = SleepTimer(null, true)), pseudo = true, fontScale = 2f)
        rule.onNodeWithTag(TAG_NP_PLAY).assertIsDisplayed()
    }
}
```

- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.** Strings: `back` "Back", `np_like` "Like", `np_unlike` "Remove like", `np_queue` "Queue", `np_shuffle_on` "Shuffle on", `np_shuffle_off` "Shuffle off", `np_repeat_off` "Repeat off", `np_repeat_all` "Repeat all", `np_repeat_one` "Repeat one", `np_sleep` "Sleep timer", `np_sleep_in_minutes` "Sleep in %1$d minutes", `np_sleep_end_of_track` "At end of this track", `np_sleep_off` "Turn off timer", `np_sleep_active` "Sleep timer on", `np_sleep_end_active` "Sleeps after this track", `np_seek_position` "%1$s of %2$s", `np_nothing_playing` "Nothing is playing", `np_offline_banner` "Waiting for network", `np_error_banner` "Playback stopped". ViewModel:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.nowplaying

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val controller: PlayerController, private val library: LibraryRepository, settings: SettingsRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val liked: Flow<Boolean> = controller.ui.map { it.current?.track?.id }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(false) else library.isLiked(id) }

    val state: StateFlow<NowPlayingUiState> = combine(controller.ui, liked, settings.settings.map { it.sleepTimerDefaultMinutes }.distinctUntilChanged()) { ui, liked, sleepDefault ->
        val item = ui.current
        if (item == null) NowPlayingUiState.Empty
        else NowPlayingUiState.Content(item.track, ui.status, ui.durationMs, ui.shuffle, ui.repeat, ui.sleepTimer, liked, ui.queue.size, ui.currentIndex, sleepDefault)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingUiState.Empty)

    val positionMs: StateFlow<Long> = controller.positionMs

    fun actions(onBack: () -> Unit, onOpenQueue: () -> Unit) = NowPlayingActions(
        onBack = onBack, onToggle = controller::togglePlayPause, onNext = controller::next, onPrevious = controller::previous, onSeek = controller::seekTo,
        onShuffle = controller::setShuffle, onRepeat = controller::cycleRepeat,
        onLike = {
            val c = state.value as? NowPlayingUiState.Content ?: return@NowPlayingActions
            viewModelScope.launch { library.setLiked(c.track, !c.liked) }
        },
        onSleepMinutes = controller::setSleepTimer, onSleepEndOfTrack = { controller.setSleepAtEndOfTrack(true) }, onSleepOff = { controller.setSleepTimer(null) },
        onOpenQueue = onOpenQueue,
    )
}
```

  `NowPlayingState.kt` holds `NowPlayingUiState` and `NowPlayingActions` as in Interfaces. `MaterialNowPlaying.kt` implements `NowPlayingContent`: `when (state) { Empty -> Column { IconButton(onBack, Modifier.size(48.dp)) { Icon(AriaIcons.ArrowBack, stringResource(R.string.back)) }; EmptyState(R.string.np_nothing_playing) }; is Content -> ... }`. For `Content`: a scrollable `Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp))` (scroll keeps font scale 2.0 usable) containing: top row (back `IconButton`, queue `IconButton` tagged `TAG_NP_QUEUE` with description `np_queue`); `AriaImage(track.thumbnailUrl, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))`; title (`headlineSmall`, 2 lines) and artists; banners: `if (status == WAITING_FOR_NETWORK) Text(stringResource(np_offline_banner), Modifier.testTag(TAG_NP_OFFLINE))`, `if (status == ERROR) Row(Modifier.testTag(TAG_NP_ERROR)) { Text(np_error_banner); TextButton(onClick = actions.onToggle) { Text(retry) } }`; `SeekBar(positionMs, durationMs, onSeek)`; the transport row (previous 48dp, play/pause 64dp tagged `TAG_NP_PLAY` showing `CircularProgressIndicator(Modifier.size(24.dp))` over the button while `BUFFERING` but keeping the "Pause" description, next); the mode row (shuffle `IconButton` description `np_shuffle_on/off`, repeat description `np_repeat_off/all/one` using `AriaIcons.RepeatOne` for ONE, like `AriaIcons.Favorite`/`FavoriteBorder` description `np_unlike`/`np_like` tagged `TAG_NP_LIKE`, sleep `TextButton` tagged `TAG_NP_SLEEP` with `Text(stringResource(when { sleep == null -> np_sleep; sleep.endOfTrack -> np_sleep_end_active; else -> np_sleep_active }))` opening a `DropdownMenu` with three `DropdownMenuItem`s (`np_sleep_in_minutes` with `state.sleepDefaultMinutes`, `np_sleep_end_of_track`, `np_sleep_off`)); `LyricsSlot(Modifier.testTag(TAG_NP_LYRICS))`. `SeekBar`:

```kotlin
@Composable
private fun SeekBar(positionMs: () -> Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val position = positionMs()                       // only this composable reads the position
    val duration = durationMs.coerceAtLeast(1L)
    val fraction = drag ?: (position.toFloat() / duration).coerceIn(0f, 1f)
    val shownMs = drag?.let { (it * duration).toLong() } ?: position
    val desc = stringResource(R.string.np_seek_position, Formatters.duration(shownMs), Formatters.duration(durationMs))
    Slider(value = fraction, onValueChange = { drag = it }, onValueChangeFinished = { drag?.let { onSeek((it * duration).toLong()) }; drag = null },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(TAG_NP_SEEK).semantics { stateDescription = desc })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(Formatters.duration(shownMs)); Text(Formatters.duration(durationMs)) }
}
```

  `NowPlayingScreen` collects `state` and `positionMs` with `collectAsStateWithLifecycle`, passes `{ position.value }`. `LyricsSlot` is `Box(modifier)` with no content (documented "filled by sub-project 4"). `AriaNavHost`: `composable<NowPlayingRoute> { NowPlayingScreen(onBack = { nav.popBackStack() }, onOpenQueue = { nav.navigate(QueueRoute) }) }` (the `AriaNavHost` signature already has `nav`).
- [ ] **Step 4:** Push; green on `verify`, `app-ui`. **Commit** `feat(app): Now Playing (Material skin)`.

---

### Task 15: Queue screen

Slice 2.

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/ui/queue/{QueueViewModel,QueueScreen}.kt`
- Modify: `strings.xml`, `plurals.xml`, `ui/shell/AriaNavHost.kt`
- Test: `app/src/test/kotlin/app/aria/music/ui/queue/QueueViewModelTest.kt`, `app/src/androidTest/kotlin/app/aria/music/ui/queue/QueueTest.kt`

**Interfaces:**
- Consumes: `PlayerController` (`ui`, `skipTo`, `removeAt`, `move`, `clearQueue`), `AriaImage`, `Formatters`.
- Produces:

```kotlin
data class QueueRow(val queueId: Long, val index: Int, val track: Track, val isCurrent: Boolean)
sealed interface QueueUiState { data object Empty : QueueUiState; data class Content(val rows: List<QueueRow>) : QueueUiState }
data class QueueActions(val onBack: () -> Unit, val onPlay: (Int) -> Unit, val onRemove: (Int) -> Unit, val onMoveUp: (Int) -> Unit, val onMoveDown: (Int) -> Unit, val onClear: () -> Unit)
@HiltViewModel class QueueViewModel @Inject constructor(controller: PlayerController) : ViewModel() { val state: StateFlow<QueueUiState>; fun play(i); fun remove(i); fun moveUp(i); fun moveDown(i); fun clear() }
@Composable fun QueueContent(state: QueueUiState, actions: QueueActions, modifier: Modifier = Modifier)
@Composable fun QueueScreen(onBack: () -> Unit, viewModel: QueueViewModel = hiltViewModel())
tags: TAG_QUEUE_LIST, "queue:row:<queueId>", "queue:menu:<queueId>"
```

**Acceptance Criteria:**
- Rows are keyed by `queueId` (stable keys; the same song twice stays two rows); the current row is marked (description "Now playing"); tapping a row plays it; each row's overflow menu offers Play, Move up (disabled at the top), Move down (disabled at the bottom), Remove; moving is by buttons, so it is fully usable with TalkBack (no drag-only gesture); "Clear queue" asks for confirmation.
- Empty state when the queue is empty; header shows a plural count; 500-song queues scroll lazily.
- ViewModel tests prove the commands map to controller calls with `move(i, i-1)` and `move(i, i+1)`, and that out-of-range moves are ignored.

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.queue

import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {
    @BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private fun TestScope.env(): Pair<FakePlaybackEngine, QueueViewModel> {
        val engine = FakePlaybackEngine(); val vm = QueueViewModel(PlayerController(engine, ServiceLauncher { }, backgroundScope))
        backgroundScope.launch { vm.state.collect { } }; return engine to vm
    }
    private fun FakePlaybackEngine.queue(n: Int, current: Int = 0) {
        mutableState.value = PlaybackState(queue = Samples.tracks(n).mapIndexed { i, t -> QueueItem(100L + i, t) }, currentIndex = current)
    }

    @Test fun emptyThenRowsWithCurrentMarked() = runTest {
        val (engine, vm) = env(); assertEquals(QueueUiState.Empty, vm.state.value)
        engine.queue(3, current = 1); runCurrent()
        val rows = (vm.state.value as QueueUiState.Content).rows
        assertEquals(listOf(100L, 101L, 102L), rows.map { it.queueId }); assertEquals(listOf(false, true, false), rows.map { it.isCurrent })
    }

    @Test fun duplicatesStayDistinctRows() = runTest {
        val (engine, vm) = env(); val t = Samples.track(1)
        engine.mutableState.value = PlaybackState(queue = listOf(QueueItem(1, t), QueueItem(2, t)), currentIndex = 0); runCurrent()
        assertEquals(2, (vm.state.value as QueueUiState.Content).rows.map { it.queueId }.toSet().size)
    }

    @Test fun commandsMapToTheController() = runTest {
        val (engine, vm) = env(); engine.queue(4); runCurrent()
        vm.play(2); vm.remove(3); vm.moveUp(2); vm.moveDown(1); vm.clear()
        assertEquals(listOf("skipTo:2", "removeAt:3", "move:2:1", "move:1:2", "clear"), engine.calls)
    }

    @Test fun outOfRangeMovesAreIgnored() = runTest {
        val (engine, vm) = env(); engine.queue(3); runCurrent()
        vm.moveUp(0); vm.moveDown(2); vm.moveUp(-1); vm.moveDown(9)
        assertTrue(engine.calls.isEmpty())
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.queue

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.aria.domain.Track
import app.aria.domain.TrackId
import app.aria.music.R
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QueueTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private fun row(i: Int, current: Boolean = false) = QueueRow(100L + i, i, Track(TrackId.youtube("track%06d".format(i)), "Song $i", listOf("A"), 1_000, null), current)
    private val log = mutableListOf<String>()
    private val actions = QueueActions({ log += "back" }, { log += "play:$it" }, { log += "remove:$it" }, { log += "up:$it" }, { log += "down:$it" }, { log += "clear" })
    private fun show(s: QueueUiState, pseudo: Boolean = false, fontScale: Float = 1f) { log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { QueueContent(s, actions) } } }

    @Test fun emptyState() { show(QueueUiState.Empty); rule.onNodeWithTag("state:empty").assertIsDisplayed() }

    @Test fun rowsMarkTheCurrentOneAndPlayOnTap() {
        show(QueueUiState.Content(listOf(row(0), row(1, current = true), row(2))))
        rule.onNodeWithTag("queue:row:101").assertContentDescriptionContains(str(R.string.queue_now_playing))
        rule.onNodeWithTag("queue:row:102").performClick(); assertEquals(listOf("play:2"), log)
    }

    @Test fun menuMovesAndRemoves() {
        show(QueueUiState.Content(listOf(row(0), row(1), row(2))))
        rule.onNodeWithTag("queue:menu:101").performClick(); rule.onNodeWithText(str(R.string.queue_move_up)).performClick()
        rule.onNodeWithTag("queue:menu:101").performClick(); rule.onNodeWithText(str(R.string.queue_move_down)).performClick()
        rule.onNodeWithTag("queue:menu:101").performClick(); rule.onNodeWithText(str(R.string.queue_remove)).performClick()
        assertEquals(listOf("up:1", "down:1", "remove:1"), log)
    }

    @Test fun edgeMovesAreDisabled() {
        show(QueueUiState.Content(listOf(row(0), row(1))))
        rule.onNodeWithTag("queue:menu:100").performClick(); rule.onNodeWithText(str(R.string.queue_move_up)).assertIsNotEnabled()
        rule.onNodeWithText(str(R.string.queue_move_down)).assertIsEnabled()
    }

    @Test fun clearAsksForConfirmation() {
        show(QueueUiState.Content(listOf(row(0))))
        rule.onNodeWithText(str(R.string.queue_clear)).performClick(); assertEquals(emptyList<String>(), log)
        rule.onNodeWithText(str(R.string.queue_clear_confirm)).performClick(); assertEquals(listOf("clear"), log)
    }

    @Test fun largeQueueScrollsAndSurvivesBigFonts() {
        show(QueueUiState.Content((0 until 500).map { row(it) }), pseudo = true, fontScale = 2f)
        rule.onNodeWithTag(TAG_QUEUE_LIST).performScrollToIndex(499); rule.onNodeWithTag("queue:row:599").assertIsDisplayed()
    }
}
```

- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.** Strings: `queue_title` "Queue", `queue_now_playing` "Now playing", `queue_move_up` "Move up", `queue_move_down` "Move down", `queue_remove` "Remove from queue", `queue_play` "Play", `queue_clear` "Clear queue", `queue_clear_confirm` "Clear", `queue_clear_title` "Clear the queue?", `queue_empty` "The queue is empty", `queue_row_more` "More actions for %1$s"; `<plurals name="queue_count"><item quantity="one">%1$d song</item><item quantity="other">%1$d songs</item></plurals>`. ViewModel:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.queue

@HiltViewModel
class QueueViewModel @Inject constructor(private val controller: PlayerController) : ViewModel() {
    val state: StateFlow<QueueUiState> = controller.ui.map { ui ->
        if (ui.queue.isEmpty()) QueueUiState.Empty
        else QueueUiState.Content(ui.queue.mapIndexed { i, q -> QueueRow(q.queueId, i, q.track, i == ui.currentIndex) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueueUiState.Empty)

    private val size get() = controller.ui.value.queue.size
    fun play(i: Int) = controller.skipTo(i)
    fun remove(i: Int) = controller.removeAt(i)
    fun moveUp(i: Int) { if (i in 1 until size) controller.move(i, i - 1) }
    fun moveDown(i: Int) { if (i in 0 until size - 1) controller.move(i, i + 1) }
    fun clear() = controller.clearQueue()
}
```

  `QueueScreen.kt`: `QueueContent` = `Column { TopAppBar-like Row(back IconButton 48dp, Text(queue_title), count plural, TextButton(queue_clear) opening `AlertDialog`(title `queue_clear_title`, confirm `queue_clear_confirm` calls `onClear`, dismiss "Not now" reusing `notif_not_now`)); LazyColumn(Modifier.testTag(TAG_QUEUE_LIST)) { items(rows, key = { it.queueId }) { r -> QueueRowItem(...) } } }`. `QueueRowItem`: `Row(Modifier.testTag("queue:row:${r.queueId}").heightIn(min = 56.dp).clickable(onClick = { actions.onPlay(r.index) }).semantics { if (r.isCurrent) contentDescription = nowPlaying + ", " + title })` with `AriaImage`, title/artist (`artist_separator`), duration `Formatters.duration`, and an overflow `IconButton(Modifier.size(48.dp).testTag("queue:menu:${r.queueId}"))` (`AriaIcons.MoreVert`, description `queue_row_more` with the title) opening `DropdownMenu` items Play, Move up (`enabled = r.index > 0`), Move down (`enabled = r.index < rows.lastIndex`), Remove. Empty → `EmptyState(R.string.queue_empty)`. `AriaNavHost`: `composable<QueueRoute> { QueueScreen(onBack = { nav.popBackStack() }) }`.
- [ ] **Step 4:** Push; green. **Commit** `feat(app): queue screen`.

---

### Task 16: Search (suggestions, grouped results, clearable history) and removal of the legacy screen

Slice 2 completes here: the app is a usable player (search, play, mini player, Now Playing, queue).

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/ui/common/TrackRow.kt`, `ui/search/{SearchViewModel,SearchScreen}.kt`
- Modify: `strings.xml`, `plurals.xml`, `ui/shell/AriaNavHost.kt`, `MainActivity.kt`, `scripts/ui-strings-baseline.txt`
- Delete: `app/src/main/kotlin/app/aria/music/PlayerScreen.kt`
- Test: `app/src/test/kotlin/app/aria/music/ui/search/SearchViewModelTest.kt`, `app/src/androidTest/kotlin/app/aria/music/ui/search/SearchTest.kt`

**Interfaces:**
- Consumes: `SearchRepository`, `SearchHistoryRepository`, `LibraryRepository.setLiked`, `PlayerController`, `NetworkMonitor`, `SearchResults`, shell states.
- Produces:

```kotlin
sealed interface SearchMode {
    data class Idle(val recent: List<String>) : SearchMode
    data class Suggestions(val suggestions: List<String>, val recent: List<String>) : SearchMode
    data object Loading : SearchMode
    data class Results(val results: SearchResults) : SearchMode
    data object Empty : SearchMode
    data object Error : SearchMode
    data object Offline : SearchMode
}
data class SearchUiState(val query: String, val mode: SearchMode)
@HiltViewModel class SearchViewModel @Inject constructor(search: SearchRepository, history: SearchHistoryRepository, library: LibraryRepository, controller: PlayerController, network: NetworkMonitor) : ViewModel() {
    val state: StateFlow<SearchUiState>
    fun onQueryChange(q: String); fun submit(q: String = state.value.query); fun retry(); fun removeHistory(q: String); fun clearHistory()
    fun playSong(songs: List<Track>, index: Int); fun playNext(t: Track); fun addToQueue(t: Track); fun toggleLike(t: Track, liked: Boolean)
}
data class SearchActions(val onQueryChange: (String) -> Unit, val onSubmit: (String) -> Unit, val onRetry: () -> Unit, val onRemoveHistory: (String) -> Unit, val onClearHistory: () -> Unit,
    val onPlaySong: (List<Track>, Int) -> Unit, val onTrackMenu: (Track, TrackMenuAction) -> Unit, val onOpenAlbum: (String) -> Unit, val onOpenArtist: (String) -> Unit, val onOpenPlaylist: (String) -> Unit)
enum class TrackMenuAction { PLAY_NEXT, ADD_TO_QUEUE, LIKE }
@Composable fun TrackRow(track: Track, onClick: () -> Unit, onMenu: (TrackMenuAction) -> Unit, modifier: Modifier = Modifier, extraActions: List<Pair<Int, () -> Unit>> = emptyList())
@Composable fun SearchContent(state: SearchUiState, actions: SearchActions, modifier: Modifier = Modifier)
@Composable fun SearchScreen(onAlbum: (String) -> Unit, onArtist: (String) -> Unit, onPlaylist: (String) -> Unit, viewModel: SearchViewModel = hiltViewModel())
```

**Acceptance Criteria:**
- Typing requests suggestions debounced by 300 ms (only the last query within the window is sent); blank query shows local history (newest first), removable one by one and clearable; submitting runs the search, stores the query in history and shows results grouped Songs, Albums, Artists, Playlists (sections only when non-empty); empty results, errors and offline each have their own state; offline never calls the network; retry reruns the last submitted query.
- Tapping a song plays the song list starting there through `PlayerController`; song rows have a menu (Play next, Add to queue, Like); album, artist and playlist rows navigate via callbacks.
- `PlayerScreen.kt` is deleted, `MainActivity` no longer injects `SearchSource` or `PlaybackEngine`, the legacy baseline entry is removed (the baseline file keeps only its comment), `AriaRoot` loses its `legacySearch` parameter, and `check-ui-strings.sh` passes with an empty baseline.

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.search

import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private class Env(scope: kotlinx.coroutines.CoroutineScope) {
        val search = FakeSearchRepository(); val history = FakeSearchHistoryRepository(listOf("old")); val library = FakeLibraryRepository(); val engine = FakePlaybackEngine(); val network = FakeNetworkMonitor()
        val vm = SearchViewModel(search, history, library, PlayerController(engine, ServiceLauncher { }, scope), network)
        init { scope.launch { vm.state.collect { } } }
    }
    private val results = SearchResults(Samples.tracks(2), listOf(Samples.album(1)), emptyList(), emptyList())

    @Test fun blankQueryShowsHistory() = runTest(dispatcher) {
        val e = Env(backgroundScope); advanceUntilIdle()
        assertEquals(SearchUiState("", SearchMode.Idle(listOf("old"))), e.vm.state.value)
    }

    @Test fun suggestionsAreDebouncedToTheLastQuery() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.search.suggestionResult = Result.success(listOf("daft punk", "daft"))
        e.vm.onQueryChange("d"); advanceTimeBy(100); e.vm.onQueryChange("da"); advanceTimeBy(100); e.vm.onQueryChange("daf"); advanceTimeBy(350); runCurrent()
        assertEquals(listOf("daf"), e.search.suggestionQueries)
        assertEquals(SearchMode.Suggestions(listOf("daft punk", "daft"), listOf("old")), e.vm.state.value.mode)
    }

    @Test fun suggestionFailureFallsBackToHistoryOnly() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.search.suggestionResult = Result.failure(RuntimeException())
        e.vm.onQueryChange("x"); advanceTimeBy(400); runCurrent()
        assertEquals(SearchMode.Suggestions(emptyList(), listOf("old")), e.vm.state.value.mode)
    }

    @Test fun submitSearchesStoresHistoryAndShowsResults() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.search.searchResult = Result.success(results)
        e.vm.onQueryChange("daft"); e.vm.submit(); advanceUntilIdle()
        assertEquals(SearchMode.Results(results), e.vm.state.value.mode); assertEquals("daft", e.history.items.value.first())
    }

    @Test fun emptyErrorAndOfflineStates() = runTest(dispatcher) {
        val e = Env(backgroundScope)
        e.search.searchResult = Result.success(SearchResults(emptyList(), emptyList(), emptyList(), emptyList())); e.vm.submit("a"); advanceUntilIdle()
        assertEquals(SearchMode.Empty, e.vm.state.value.mode)
        e.search.searchResult = Result.failure(java.io.IOException()); e.vm.submit("b"); advanceUntilIdle()
        assertEquals(SearchMode.Error, e.vm.state.value.mode)
        e.network.online.value = false; val calls = e.search.searchQueries.size; e.vm.submit("c"); advanceUntilIdle()
        assertEquals(SearchMode.Offline, e.vm.state.value.mode); assertEquals(calls, e.search.searchQueries.size)
    }

    @Test fun retryRerunsTheLastSubmittedQuery() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.search.searchResult = Result.failure(java.io.IOException()); e.vm.submit("q"); advanceUntilIdle()
        e.search.searchResult = Result.success(results); e.vm.retry(); advanceUntilIdle()
        assertEquals(listOf("q", "q"), e.search.searchQueries); assertTrue(e.vm.state.value.mode is SearchMode.Results)
    }

    @Test fun editingAfterResultsLeavesResultsMode() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.search.searchResult = Result.success(results); e.vm.submit("q"); advanceUntilIdle()
        e.vm.onQueryChange("qq"); advanceTimeBy(400); runCurrent()
        assertTrue(e.vm.state.value.mode is SearchMode.Suggestions)
    }

    @Test fun historyCanBeRemovedAndCleared() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.history.add("two"); advanceUntilIdle()
        e.vm.removeHistory("old"); advanceUntilIdle(); assertEquals(listOf("two"), e.history.items.value)
        e.vm.clearHistory(); advanceUntilIdle(); assertEquals(SearchMode.Idle(emptyList()), e.vm.state.value.mode)
    }

    @Test fun playingASongUsesTheControllerAndLikeWritesTheLibrary() = runTest(dispatcher) {
        val e = Env(backgroundScope); val songs = Samples.tracks(3)
        e.vm.playSong(songs, 1); e.vm.playNext(songs[0]); e.vm.addToQueue(songs[2]); e.vm.toggleLike(songs[0], true); advanceUntilIdle()
        assertEquals(listOf(songs to 1), e.engine.playNowCalls); assertTrue("playNext" in e.engine.calls)
        assertEquals(songs[0], kotlinx.coroutines.flow.first(e.library.songs("", LibrarySort.TITLE)).single().track)
    }
}
```

  (Use `import kotlinx.coroutines.flow.first` and `e.library.songs("", LibrarySort.TITLE).first()` when typing.)

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.search

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.aria.domain.*
import app.aria.domain.testing.Samples
import app.aria.music.R
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val log = mutableListOf<String>()
    private val actions = SearchActions(
        { log += "query:$it" }, { log += "submit:$it" }, { log += "retry" }, { log += "remove:$it" }, { log += "clear" },
        { songs, i -> log += "play:${songs.size}:$i" }, { t, a -> log += "menu:${t.title}:$a" }, { log += "album:$it" }, { log += "artist:$it" }, { log += "playlist:$it" },
    )
    private val results = SearchResults(Samples.tracks(2), listOf(Samples.album(1)), listOf(Samples.artist(1)), listOf(Samples.playlist(1)))
    private fun show(s: SearchUiState, pseudo: Boolean = false, fontScale: Float = 1f) { log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { SearchContent(s, actions) } } }

    @Test fun historyIsListedRemovableAndClearable() {
        show(SearchUiState("", SearchMode.Idle(listOf("daft", "punk"))))
        rule.onNodeWithText("daft").performClick(); rule.onNodeWithContentDescription(str(R.string.search_remove_history, "daft")).performClick(); rule.onNodeWithText(str(R.string.search_clear_history)).performClick()
        assertEquals(listOf("submit:daft", "remove:daft", "clear"), log)
    }

    @Test fun suggestionsSubmitOnTap() {
        show(SearchUiState("da", SearchMode.Suggestions(listOf("daft punk"), emptyList()))); rule.onNodeWithText("daft punk").performClick(); assertEquals(listOf("submit:daft punk"), log)
    }

    @Test fun resultsAreGroupedAndNavigable() {
        show(SearchUiState("q", SearchMode.Results(results)))
        for (id in listOf(R.string.search_section_songs, R.string.search_section_albums, R.string.search_section_artists, R.string.search_section_playlists)) rule.onNodeWithText(str(id)).assertExists()
        rule.onNodeWithText("Song 2").performClick(); rule.onNodeWithText("Album 1").performClick(); rule.onNodeWithText("Artist 1").performClick(); rule.onNodeWithText("Playlist 1").performClick()
        assertEquals(listOf("play:2:1", "album:MPREb_1", "artist:UC1", "playlist:PL1"), log)
    }

    @Test fun emptySectionsAreHidden() {
        show(SearchUiState("q", SearchMode.Results(SearchResults(Samples.tracks(1), emptyList(), emptyList(), emptyList()))))
        rule.onNodeWithText(str(R.string.search_section_albums)).assertDoesNotExist()
    }

    @Test fun songMenuOffersThreeActions() {
        show(SearchUiState("q", SearchMode.Results(results)))
        rule.onNodeWithContentDescription(str(R.string.track_more, "Song 1")).performClick(); rule.onNodeWithText(str(R.string.track_play_next)).performClick()
        assertEquals(listOf("menu:Song 1:PLAY_NEXT"), log)
    }

    @Test fun errorOfflineEmptyLoadingStates() {
        show(SearchUiState("q", SearchMode.Error)); rule.onNodeWithText(str(R.string.retry)).performClick(); assertEquals(listOf("retry"), log)
        show(SearchUiState("q", SearchMode.Offline)); rule.onNodeWithTag("state:offline").assertIsDisplayed()
        show(SearchUiState("q", SearchMode.Empty)); rule.onNodeWithTag("state:empty").assertIsDisplayed()
        show(SearchUiState("q", SearchMode.Loading)); rule.onNodeWithTag("state:loading").assertIsDisplayed()
    }

    @Test fun survivesPseudolocaleAndBigFonts() { show(SearchUiState("q", SearchMode.Results(results)), pseudo = true, fontScale = 2f); rule.onNodeWithText("Song 1").assertExists() }
}
```

  (A single `setContent` per test in Compose rules: `errorOfflineEmptyLoadingStates` must be split into four tests when typing, since `rule.setContent` may be called once per test.)
- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.** Strings: `search_hint` "Search songs, albums, artists", `search_clear_history` "Clear history", `search_remove_history` "Remove %1$s from history", `search_recent` "Recent searches", `search_section_songs` "Songs", `search_section_albums` "Albums", `search_section_artists` "Artists", `search_section_playlists` "Playlists", `search_empty` "No results", `search_clear_query` "Clear search text", `track_more` "More actions for %1$s", `track_play_next` "Play next", `track_add_to_queue` "Add to queue", `track_like` "Like", `track_unlike` "Remove like". ViewModel:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.search

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchRepository, private val history: SearchHistoryRepository, private val library: LibraryRepository,
    private val controller: PlayerController, private val network: NetworkMonitor,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val submitted = MutableStateFlow<Pair<Int, String>?>(null)   // (generation, query); the generation lets retry re-run the same text
    private var generation = 0

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val suggestions: Flow<List<String>> = query.debounce(300).distinctUntilChanged().mapLatest { q ->
        if (q.isBlank()) emptyList() else search.suggestions(q.trim()).getOrDefault(emptyList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val searched: Flow<SearchMode?> = submitted.flatMapLatest { s ->
        if (s == null) flowOf(null) else flow {
            if (!network.online.value) { emit(SearchMode.Offline); return@flow }
            emit(SearchMode.Loading)
            emit(search.search(s.second).fold({ if (it.isEmpty) SearchMode.Empty else SearchMode.Results(it) }, { SearchMode.Error }))
        }
    }

    val state: StateFlow<SearchUiState> = combine(query, suggestions, history.recent(HISTORY_LIMIT), searched) { q, sugg, recent, searchMode ->
        SearchUiState(q, when {
            searchMode != null -> searchMode
            q.isBlank() -> SearchMode.Idle(recent)
            else -> SearchMode.Suggestions(sugg, recent)
        })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState("", SearchMode.Idle(emptyList())))

    fun onQueryChange(q: String) { query.value = q; submitted.value = null }
    fun submit(q: String = query.value) {
        val text = q.trim(); if (text.isEmpty()) return
        query.value = text; submitted.value = ++generation to text
        viewModelScope.launch { history.add(text) }
    }
    fun retry() { submitted.value?.let { submitted.value = ++generation to it.second } }
    fun removeHistory(q: String) { viewModelScope.launch { history.remove(q) } }
    fun clearHistory() { viewModelScope.launch { history.clear() } }
    fun playSong(songs: List<Track>, index: Int) = controller.play(songs, index)
    fun playNext(t: Track) = controller.playNext(t)
    fun addToQueue(t: Track) = controller.addToQueue(listOf(t))
    fun toggleLike(t: Track, liked: Boolean) { viewModelScope.launch { library.setLiked(t, liked) } }

    private companion object { const val HISTORY_LIMIT = 20 }
}
```

  (If the "editing after results leaves results mode" test needs it, `onQueryChange` already clears `submitted`.) `TrackRow.kt` (shared, also used by Home, Library and detail pages): a `Row(Modifier.heightIn(min = 56.dp).clickable(onClick = onClick))` with `AriaImage`, title/artists, duration `Formatters.duration` and an overflow `IconButton(Modifier.size(48.dp))` described `track_more` (title) opening `DropdownMenu` with `track_play_next`, `track_add_to_queue`, `track_like` plus `extraActions` (resource id to callback pairs, used for "Add to playlist" in Task 20). `SearchScreen.kt`: `SearchContent` = `Column { OutlinedTextField(query, onQueryChange, placeholder = { Text(stringResource(R.string.search_hint)) }, keyboardOptions = ImeAction.Search, keyboardActions = onSearch { actions.onSubmit(query) }, trailingIcon = clear `IconButton` (description `search_clear_query`) when non-empty, modifier = Modifier.fillMaxWidth().padding(16.dp)); when (mode) { Idle -> history list (header `search_recent` + `TextButton(search_clear_history)`; rows `Row(clickable { onSubmit(it) }) { Text(it); IconButton(description search_remove_history) }` ); Suggestions -> suggestion rows then history; Loading -> LoadingState(); Results -> LazyColumn with section headers (`stickyHeader`-free plain items) and `TrackRow` / album / artist / playlist rows keyed by `BrowseItem.key`-style strings; Empty -> EmptyState(R.string.search_empty); Error -> ErrorState(onRetry); Offline -> OfflineState(onRetry) } }`. Album, artist and playlist rows are `ListItem`s with `AriaImage`, clickable (>= 56dp). `SearchScreen` maps `TrackMenuAction.PLAY_NEXT -> vm.playNext`, `ADD_TO_QUEUE -> vm.addToQueue`, `LIKE -> vm.toggleLike(track, true)`. `AriaNavHost`: remove `legacySearch`; `composable<SearchRoute> { SearchScreen(onAlbum = { nav.navigate(AlbumRoute(it)) }, onArtist = { nav.navigate(ArtistRoute(it)) }, onPlaylist = { nav.navigate(PlaylistRoute(it, local = false)) }) }` (the three destination routes exist since Task 12; their screens arrive in Task 19, until then navigating to them shows nothing, so the callbacks are wired in Task 19 and here they only call `nav.navigate` if a destination is registered: register temporary `composable<AlbumRoute>`, `ArtistRoute`, `PlaylistRoute` entries showing `EmptyState(R.string.coming_soon)` with new string `coming_soon` "Coming soon", replaced in Task 19). `AriaRoot(legacySearch)` becomes `AriaRoot()`; `MainActivity` drops `search`, `engine` and the `PlayerScreen` call. Delete `PlayerScreen.kt`; reduce `scripts/ui-strings-baseline.txt` to its comment line only.
- [ ] **Step 4:** Push; `verify` (strings check passes with the empty baseline), `app-ui` and `playback-service` green. **Commit** `feat(app): search and removal of the legacy player screen`. **Slice 2 complete.**

---

### Task 17: Home (Quick picks, YouTube Music shelves, Recently played, stale marker)

Slice 3, decision 3 (UI part). Local sections always work offline; remote shelves are cached, revalidated on open and fail independently.

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/ui/home/{HomeViewModel,HomeScreen}.kt`
- Modify: `strings.xml`, `plurals.xml` (remove `home_placeholder`), `ui/shell/AriaNavHost.kt`
- Test: `app/src/test/kotlin/app/aria/music/ui/home/HomeViewModelTest.kt`, `app/src/androidTest/kotlin/app/aria/music/ui/home/HomeTest.kt`

**Interfaces:**
- Consumes: `HomeRepository` (`quickPicks`, `recentlyPlayed`, `remote`, `needsRevalidation`, `refresh`), `ShelfState`, `Shelf`, `BrowseItem`, `PlayerController`, `NetworkMonitor`, `TrackRow`, shell states.
- Produces:

```kotlin
sealed interface RemoteSection {
    data object Loading : RemoteSection
    data class Content(val shelves: List<ShelfState>, val outdated: Boolean, val refreshFailed: Boolean) : RemoteSection
    data object Error : RemoteSection
    data object Offline : RemoteSection
}
data class HomeUiState(val quickPicks: List<Track>, val recent: List<Track>, val remote: RemoteSection, val refreshing: Boolean)
data class HomeActions(
    val onPlay: (List<Track>, Int) -> Unit, val onOpenAlbum: (String) -> Unit, val onOpenArtist: (String) -> Unit, val onOpenPlaylist: (String) -> Unit,
    val onRefresh: () -> Unit, val onOpenSettings: () -> Unit, val onTrackMenu: (Track, TrackMenuAction) -> Unit,
)
@HiltViewModel class HomeViewModel @Inject constructor(home: HomeRepository, library: LibraryRepository, controller: PlayerController, network: NetworkMonitor) : ViewModel() {
    val state: StateFlow<HomeUiState>; fun refresh(); fun play(tracks: List<Track>, index: Int); fun playNext(t: Track); fun addToQueue(t: Track); fun like(t: Track)
}
@Composable fun HomeContent(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier)
@Composable fun HomeScreen(onAlbum: (String) -> Unit, onArtist: (String) -> Unit, onPlaylist: (String) -> Unit, onSettings: () -> Unit, viewModel: HomeViewModel = hiltViewModel())
tags: TAG_HOME_STALE, TAG_HOME_SHELF_FAILED, "home:quick:<n>", "home:recent:<n>", TAG_HOME_LIST
```

**Acceptance Criteria:**
- Cached remote shelves show immediately; the screen revalidates on open only when `needsRevalidation()` is true; pull to refresh always refreshes; offline never calls the network.
- Remote section: `Loading` (no cache, refresh running), `Content` (cache or fresh; `outdated` shows the banner "Saved content, may be outdated" after 7 days; a failed shelf of a fresh response shows its own "This section could not load" placeholder with the shelf title while neighbours render; `refreshFailed` when a refresh failed but a cache is shown), `Error` (no cache, online, refresh failed, with Retry), `Offline` (no cache, offline).
- Quick picks and Recently played are independent of the remote section (hidden behind a one-line hint when empty: "Play something and your quick picks appear here").
- Tapping a song plays the section's songs from there; tiles open album, artist or playlist pages; the settings entry (48dp, described "Settings") navigates to `SettingsRoute`.

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.home

import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private class Env(scope: kotlinx.coroutines.CoroutineScope, online: Boolean = true) {
        val home = FakeHomeRepository(); val library = FakeLibraryRepository(); val engine = FakePlaybackEngine(); val network = FakeNetworkMonitor(online)
        val vm by lazy { HomeViewModel(home, library, PlayerController(engine, ServiceLauncher { }, scope), network).also { scope.launch { it.state.collect { } } } }
    }
    private fun shelf(t: String) = Shelf(t, null, listOf(BrowseItem.Song(Samples.track(1))))

    @Test fun cacheShowsImmediatelyAndRevalidatesOnlyWhenNeeded() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.home.remoteState.value = HomeRemote(listOf(shelf("A")), 1L, outdated = false); e.home.revalidate = false
        e.vm; advanceUntilIdle()
        val c = e.vm.state.value.remote as RemoteSection.Content
        assertEquals(listOf(ShelfState.Ready(shelf("A"))), c.shelves); assertFalse(c.outdated); assertEquals(0, e.home.refreshCalls)
    }

    @Test fun staleCacheIsMarkedAndRevalidated() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.home.remoteState.value = HomeRemote(listOf(shelf("A")), 1L, outdated = true)
        e.home.refreshResult = Result.failure(java.io.IOException())
        e.vm; advanceUntilIdle()
        val c = e.vm.state.value.remote as RemoteSection.Content
        assertTrue(c.outdated); assertTrue(c.refreshFailed); assertEquals(1, e.home.refreshCalls)
    }

    @Test fun freshResponseShowsFailedShelvesInPlace() = runTest(dispatcher) {
        val e = Env(backgroundScope)
        e.home.refreshResult = Result.success(HomeRefresh(listOf(ShelfState.Ready(shelf("A")), ShelfState.Failed("B"))))
        e.vm; advanceUntilIdle()
        val c = e.vm.state.value.remote as RemoteSection.Content
        assertEquals(listOf(ShelfState.Ready(shelf("A")), ShelfState.Failed("B")), c.shelves); assertFalse(c.refreshFailed)
    }

    @Test fun noCacheAndFailureIsErrorThenRetrySucceeds() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.home.refreshResult = Result.failure(java.io.IOException())
        e.vm; advanceUntilIdle(); assertEquals(RemoteSection.Error, e.vm.state.value.remote)
        e.home.refreshResult = Result.success(HomeRefresh(listOf(ShelfState.Ready(shelf("A"))))); e.vm.refresh(); advanceUntilIdle()
        assertTrue(e.vm.state.value.remote is RemoteSection.Content)
    }

    @Test fun offlineWithoutCacheNeverCallsTheNetwork() = runTest(dispatcher) {
        val e = Env(backgroundScope, online = false); e.vm; advanceUntilIdle()
        assertEquals(RemoteSection.Offline, e.vm.state.value.remote); assertEquals(0, e.home.refreshCalls)
    }

    @Test fun localSectionsAreIndependentOfTheRemoteOne() = runTest(dispatcher) {
        val e = Env(backgroundScope, online = false); e.home.quick.value = Samples.tracks(2); e.home.recent.value = Samples.tracks(1, from = 5)
        e.vm; advanceUntilIdle()
        assertEquals(2, e.vm.state.value.quickPicks.size); assertEquals(listOf(Samples.track(5)), e.vm.state.value.recent)
    }

    @Test fun playingUsesTheController() = runTest(dispatcher) {
        val e = Env(backgroundScope); e.vm.play(Samples.tracks(3), 2); advanceUntilIdle()
        assertEquals(listOf(Samples.tracks(3) to 2), e.engine.playNowCalls)
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.aria.domain.*
import app.aria.domain.testing.Samples
import app.aria.music.R
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val log = mutableListOf<String>()
    private val actions = HomeActions(
        { l, i -> log += "play:${l.size}:$i" }, { log += "album:$it" }, { log += "artist:$it" }, { log += "playlist:$it" }, { log += "refresh" }, { log += "settings" }, { t, a -> log += "menu:${t.title}:$a" },
    )
    private val shelf = Shelf("Fresh picks", "For you", listOf(BrowseItem.Song(Samples.track(1)), BrowseItem.Album(Samples.album(2)), BrowseItem.Artist(Samples.artist(3)), BrowseItem.Playlist(Samples.playlist(4))))
    private fun state(remote: RemoteSection, quick: List<Track> = Samples.tracks(2), recent: List<Track> = Samples.tracks(1, from = 9)) = HomeUiState(quick, recent, remote, refreshing = false)
    private fun show(s: HomeUiState, pseudo: Boolean = false, fontScale: Float = 1f) { log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { HomeContent(s, actions) } } }

    @Test fun localAndRemoteSectionsRenderAndNavigate() {
        show(state(RemoteSection.Content(listOf(ShelfState.Ready(shelf)), outdated = false, refreshFailed = false)))
        rule.onNodeWithText(str(R.string.home_quick_picks)).assertExists(); rule.onNodeWithText(str(R.string.home_recently_played)).assertExists(); rule.onNodeWithText("Fresh picks").assertExists()
        rule.onNodeWithTag("home:quick:0").performClick(); rule.onNodeWithText("Album 2").performClick(); rule.onNodeWithText("Artist 3").performClick(); rule.onNodeWithText("Playlist 4").performClick()
        assertEquals(listOf("play:2:0", "album:MPREb_2", "artist:UC3", "playlist:PL4"), log)
    }

    @Test fun staleBannerAndRefresh() {
        show(state(RemoteSection.Content(listOf(ShelfState.Ready(shelf)), outdated = true, refreshFailed = false)))
        rule.onNodeWithTag(TAG_HOME_STALE).assertIsDisplayed(); rule.onNodeWithText(str(R.string.home_refresh)).performClick(); assertEquals(listOf("refresh"), log)
    }

    @Test fun failedShelfShowsItsTitleAndNeighboursStay() {
        show(state(RemoteSection.Content(listOf(ShelfState.Ready(shelf), ShelfState.Failed("Charts")), false, false)))
        rule.onNodeWithTag(TAG_HOME_SHELF_FAILED).assertExists(); rule.onNodeWithText("Charts").assertExists(); rule.onNodeWithText("Fresh picks").assertExists()
    }

    @Test fun emptyLocalSectionsShowAHint() {
        show(state(RemoteSection.Offline, quick = emptyList(), recent = emptyList())); rule.onNodeWithText(str(R.string.home_quick_picks_hint)).assertExists(); rule.onNodeWithTag("state:offline").assertIsDisplayed()
    }

    @Test fun errorAndLoadingSections() { show(state(RemoteSection.Error)); rule.onNodeWithTag("state:error").assertIsDisplayed() }
    @Test fun loadingSection() { show(state(RemoteSection.Loading)); rule.onNodeWithTag("state:loading").assertExists() }
    @Test fun settingsEntryIsReachable() { show(state(RemoteSection.Loading)); rule.onNodeWithContentDescription(str(R.string.home_settings)).assertWidthIsAtLeast(androidx.compose.ui.unit.dp(48)).performClick(); assertEquals(listOf("settings"), log) }
    @Test fun survivesPseudolocaleAndBigFonts() { show(state(RemoteSection.Content(listOf(ShelfState.Ready(shelf)), true, true)), pseudo = true, fontScale = 2f); rule.onNodeWithTag(TAG_HOME_LIST).assertExists() }
}
```

  (`androidx.compose.ui.unit.dp(48)` stands for `48.dp` with `import androidx.compose.ui.unit.dp`; type it that way.)
- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.** Strings: `home_quick_picks` "Quick picks", `home_recently_played` "Recently played", `home_quick_picks_hint` "Play something and your quick picks appear here", `home_recent_hint` "Songs you play appear here", `home_stale` "Saved content, may be outdated", `home_refresh` "Refresh", `home_refresh_failed` "Could not refresh. Showing saved content.", `home_section_failed` "This section could not load", `home_settings` "Settings". Remove `home_placeholder`. ViewModel:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.home

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val home: HomeRepository, private val library: LibraryRepository, private val controller: PlayerController, private val network: NetworkMonitor,
) : ViewModel() {
    private sealed interface Refresh {
        data object Idle : Refresh; data object Running : Refresh; data object Offline : Refresh; data object Failed : Refresh
        data class Done(val shelves: List<ShelfState>) : Refresh
    }
    private val refresh = MutableStateFlow<Refresh>(Refresh.Idle)

    init { viewModelScope.launch { if (home.needsRevalidation()) refresh() } }

    fun refresh() {
        if (refresh.value == Refresh.Running) return
        viewModelScope.launch {
            if (!network.online.value) { refresh.value = Refresh.Offline; return@launch }
            refresh.value = Refresh.Running
            refresh.value = home.refresh().fold({ Refresh.Done(it.shelves) }, { Refresh.Failed })
        }
    }

    private val remoteSection: Flow<RemoteSection> = combine(home.remote(), refresh) { cache, r ->
        val cached = cache?.shelves?.map { ShelfState.Ready(it) }
        when {
            r is Refresh.Done -> RemoteSection.Content(r.shelves, outdated = false, refreshFailed = false)
            cached != null -> RemoteSection.Content(cached, cache.outdated, refreshFailed = r is Refresh.Failed)
            r is Refresh.Offline -> RemoteSection.Offline
            r is Refresh.Failed -> RemoteSection.Error
            else -> RemoteSection.Loading
        }
    }

    val state: StateFlow<HomeUiState> = combine(home.quickPicks(12), home.recentlyPlayed(12), remoteSection, refresh) { q, r, remote, ref ->
        HomeUiState(q, r, remote, refreshing = ref == Refresh.Running)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(emptyList(), emptyList(), RemoteSection.Loading, false))

    fun play(tracks: List<Track>, index: Int) = controller.play(tracks, index)
    fun playNext(t: Track) = controller.playNext(t)
    fun addToQueue(t: Track) = controller.addToQueue(listOf(t))
    fun like(t: Track) { viewModelScope.launch { library.setLiked(t, true) } }
}
```

  `HomeScreen.kt`: `HomeContent` = `PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = actions.onRefresh) { LazyColumn(Modifier.testTag(TAG_HOME_LIST)) { ... } }` with items: a header row (title `app_name` via `stringResource(R.string.app_name)` and the Settings `IconButton(Modifier.size(48.dp))` with `AriaIcons.MoreVert`, description `home_settings`); the Quick picks section (`SectionHeader(home_quick_picks)` then up to 12 `TrackRow`s tagged `home:quick:<n>` whose click is `onPlay(state.quickPicks, n)`, or the hint text when empty); the remote section (`Loading -> LoadingState` box of fixed height, `Error -> ErrorState(onRetry = actions.onRefresh)`, `Offline -> OfflineState(onRetry = actions.onRefresh)`, `Content -> if (outdated) stale banner `Row(Modifier.testTag(TAG_HOME_STALE)) { Text(home_stale); TextButton(onClick = actions.onRefresh) { Text(home_refresh) } }`, `if (refreshFailed) Text(home_refresh_failed)`, then per shelf: `Ready -> ShelfRow(shelf)`; `Failed -> Row(Modifier.testTag(TAG_HOME_SHELF_FAILED)) { Text(title ?: "" via a non-literal), Text(home_section_failed), TextButton(refresh) }` (the title comes from data, never a literal); the Recently played section like Quick picks tagged `home:recent:<n>`. `ShelfRow`: `Text(shelf.title, style = titleMedium)` + optional subtitle + `LazyRow { items(shelf.items, key = { it.key }) { Tile(it) } }`, where `Tile` is a 144dp wide `Column(Modifier.clickable(onClick = ...))` with `AriaImage` (square, rounded; artists circular) and a two-line title; tapping a `Song` calls `onPlay(shelfSongs, indexAmongSongs)`, `Album -> onOpenAlbum(album.id)`, `Artist -> onOpenArtist(artist.id)`, `Playlist -> onOpenPlaylist(playlist.id)`. `HomeScreen` maps `TrackMenuAction` to the ViewModel (`LIKE -> like`). `AriaNavHost`: `composable<HomeRoute> { HomeScreen(onAlbum = { nav.navigate(AlbumRoute(it)) }, onArtist = { nav.navigate(ArtistRoute(it)) }, onPlaylist = { nav.navigate(PlaylistRoute(it, local = false)) }, onSettings = { nav.navigate(SettingsRoute) }) }`, and a temporary `composable<SettingsRoute> { EmptyState(R.string.coming_soon) }` (replaced in Task 21).
- [ ] **Step 4 (CUTTABLE: Home shelf variety, cut order 2):** in `ShelfRow`, when every item of a shelf is a `Song` and the shelf has at least 4, render it as a two-column-per-page grid: `LazyRow` of `Column`s each holding two `TrackRow`-style compact rows (artwork 48dp, title, artist) so song shelves read as lists and album shelves as tiles; add a `HomeTest` case that a songs-only shelf shows its songs. If cut, every shelf stays a tile carousel and nothing else changes (Quick picks and Recently played are kept).
- [ ] **Step 5:** Push; green on `verify`, `app-ui`. **Commit** `feat(app): Home screen with cached shelves and local sections`.

---

### Task 18: Library (Songs, Albums, Artists, Playlists)

Slice 3, decisions 2 and 9. SQL-side sort and filter come from `LibraryRepository`; the list is a lazy list with stable keys; playing from it never queues more than 500.

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/ui/library/{LibraryViewModel,LibraryScreen}.kt`
- Modify: `strings.xml`, `plurals.xml` (remove `library_placeholder`), `ui/shell/AriaNavHost.kt`
- Test: `app/src/test/kotlin/app/aria/music/ui/library/LibraryViewModelTest.kt`, `app/src/androidTest/kotlin/app/aria/music/ui/library/LibraryTest.kt`

**Interfaces:**
- Consumes: `LibraryRepository`, `PlaylistRepository.playlists`, `PlayerController`, `LibrarySong`, `LibrarySort`, `AlbumRef`, `ArtistRef`, `PlaylistRef`, `TrackRow`.
- Produces:

```kotlin
enum class LibraryTab { SONGS, ALBUMS, ARTISTS, PLAYLISTS }
sealed interface LibraryContent {
    data object Loading : LibraryContent
    data object Empty : LibraryContent            // nothing in this tab at all
    data object NoMatches : LibraryContent        // the filter matches nothing
    data object Error : LibraryContent
    data class Songs(val rows: List<LibrarySong>) : LibraryContent
    data class Albums(val rows: List<AlbumRef>) : LibraryContent
    data class Artists(val rows: List<ArtistRef>) : LibraryContent
    data class Playlists(val rows: List<PlaylistRef>) : LibraryContent
}
data class LibraryUiState(val tab: LibraryTab, val query: String, val sort: LibrarySort, val content: LibraryContent)
data class LibraryActions(
    val onTab: (LibraryTab) -> Unit, val onQuery: (String) -> Unit, val onSort: (LibrarySort) -> Unit, val onPlaySong: (List<Track>, Int) -> Unit,
    val onPlayAll: () -> Unit, val onShuffleAll: () -> Unit, val onTrackMenu: (Track, LibraryTrackAction) -> Unit,
    val onOpenAlbum: (String) -> Unit, val onOpenArtist: (String) -> Unit, val onOpenPlaylist: (String) -> Unit,
)
enum class LibraryTrackAction { PLAY_NEXT, ADD_TO_QUEUE, UNLIKE, REMOVE_FROM_LIBRARY }
@HiltViewModel class LibraryViewModel @Inject constructor(library: LibraryRepository, playlists: PlaylistRepository, controller: PlayerController) : ViewModel() {
    val state: StateFlow<LibraryUiState>; fun setTab(t: LibraryTab); fun setQuery(q: String); fun setSort(s: LibrarySort)
    fun play(tracks: List<Track>, index: Int); fun playAll(); fun shuffleAll(); fun trackAction(t: Track, a: LibraryTrackAction)
}
@Composable fun LibraryContent(state: LibraryUiState, actions: LibraryActions, modifier: Modifier = Modifier)
@Composable fun LibraryScreen(onAlbum: (String) -> Unit, onArtist: (String) -> Unit, onPlaylist: (String) -> Unit, viewModel: LibraryViewModel = hiltViewModel())
tags: "library:tab:<TAB>", TAG_LIBRARY_LIST, TAG_LIBRARY_QUERY, "library:row:<key>"
```

**Acceptance Criteria:**
- Four tabs (each at least 48dp); the filter field and sort chips (Recently added, A to Z) feed the repository, which sorts and filters in SQL; rows are keyed by stable IDs (track ID, album ID, artist ID, playlist ID); a 1 500-song list scrolls and the last row is reachable.
- Empty tab (e.g. "Like or add songs to see them here") and "No matches" are different states; a repository failure shows the error state; the screen works offline (all data is local; only opening a page needs the network, and those pages have their own offline state).
- Tapping a song plays the visible list from that row (`PlayerController.play`, which windows to 500 and emits the message for longer lists); Play and Shuffle play the whole filtered list (Shuffle shuffles first); the song menu offers Play next, Add to queue, Remove like, Remove from library (Task 20 adds Add to playlist).

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.library

import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private class Env(scope: kotlinx.coroutines.CoroutineScope, val library: LibraryRepository = FakeLibraryRepository()) {
        val playlists = FakePlaylistRepository(); val engine = FakePlaybackEngine(); val messages = mutableListOf<PlayerMessage>()
        val controller = PlayerController(engine, ServiceLauncher { }, scope)
        val vm = LibraryViewModel(library, playlists, controller).also { scope.launch { it.state.collect { } }; scope.launch { controller.messages.collect { m -> messages += m } } }
    }

    @Test fun emptyThenSongsThenNoMatches() = runTest {
        val e = Env(backgroundScope); assertEquals(LibraryContent.Empty, e.vm.state.value.content)
        e.library.setInLibrary(Samples.track(1), true); e.library.setInLibrary(Samples.track(2, "Zebra"), true)
        assertEquals(2, (e.vm.state.value.content as LibraryContent.Songs).rows.size)
        e.vm.setQuery("nothing-like-this"); assertEquals(LibraryContent.NoMatches, e.vm.state.value.content)
    }

    @Test fun sortAndFilterAreAppliedByTheRepository() = runTest {
        val e = Env(backgroundScope); e.library.setInLibrary(Samples.track(1, "Banana"), true); e.library.setInLibrary(Samples.track(2, "apple"), true)
        assertEquals(listOf("apple", "Banana"), run { e.vm.setSort(LibrarySort.TITLE); (e.vm.state.value.content as LibraryContent.Songs).rows.map { it.track.title } })
        e.vm.setQuery("ban"); assertEquals(listOf("Banana"), (e.vm.state.value.content as LibraryContent.Songs).rows.map { it.track.title })
    }

    @Test fun tabsSwitchContent() = runTest {
        val e = Env(backgroundScope); e.library.setAlbumSaved(Samples.album(1), true); e.library.setArtistFollowed(Samples.artist(1), true); e.playlists.create("Mix")
        e.vm.setTab(LibraryTab.ALBUMS); assertEquals(listOf(Samples.album(1)), (e.vm.state.value.content as LibraryContent.Albums).rows)
        e.vm.setTab(LibraryTab.ARTISTS); assertEquals(listOf(Samples.artist(1)), (e.vm.state.value.content as LibraryContent.Artists).rows)
        e.vm.setTab(LibraryTab.PLAYLISTS); assertEquals(listOf("Mix"), (e.vm.state.value.content as LibraryContent.Playlists).rows.map { it.title })
    }

    @Test fun playingFromARowQueuesTheVisibleListFromThere() = runTest {
        val e = Env(backgroundScope); val tracks = Samples.tracks(3); tracks.forEach { e.library.setInLibrary(it, true) }
        val shown = (e.vm.state.value.content as LibraryContent.Songs).rows.map { it.track }
        e.vm.play(shown, 1)
        assertEquals(listOf(shown to 1), e.engine.playNowCalls)
    }

    @Test fun listsLongerThanTheCapQueueAWindowAndTellTheUser() = runTest {
        val e = Env(backgroundScope); val many = Samples.tracks(700)
        e.vm.play(many, 650)
        assertEquals(50, e.engine.playNowCalls.single().first.size); assertEquals(listOf<PlayerMessage>(PlayerMessage.QueueWindowed(50, 700)), e.messages)
    }

    @Test fun playAllAndShuffleAllUseTheWholeFilteredList() = runTest {
        val e = Env(backgroundScope); Samples.tracks(5).forEach { e.library.setInLibrary(it, true) }
        e.vm.playAll(); e.vm.shuffleAll()
        assertEquals(5, e.engine.playNowCalls[0].first.size); assertEquals(5, e.engine.playNowCalls[1].first.size)
        assertEquals(e.engine.playNowCalls[0].first.toSet(), e.engine.playNowCalls[1].first.toSet())
    }

    @Test fun songActionsWriteTheLibraryOrQueue() = runTest {
        val e = Env(backgroundScope); val t = Samples.track(1); e.library.setLiked(t, true)
        e.vm.trackAction(t, LibraryTrackAction.UNLIKE); assertFalse(e.library.isLiked(t.id).first())
        e.vm.trackAction(t, LibraryTrackAction.REMOVE_FROM_LIBRARY); assertFalse(e.library.isInLibrary(t.id).first())
        e.vm.trackAction(t, LibraryTrackAction.PLAY_NEXT); assertTrue("playNext" in e.engine.calls)
    }

    @Test fun repositoryFailureShowsTheErrorState() = runTest {
        val broken = object : LibraryRepository by FakeLibraryRepository() { override fun songs(query: String, sort: LibrarySort) = flow<List<LibrarySong>> { error("db") } }
        assertEquals(LibraryContent.Error, Env(backgroundScope, broken).vm.state.value.content)
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.library

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.aria.domain.*
import app.aria.domain.testing.Samples
import app.aria.music.R
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val log = mutableListOf<String>()
    private val actions = LibraryActions(
        { log += "tab:$it" }, { log += "query:$it" }, { log += "sort:$it" }, { l, i -> log += "play:${l.size}:$i" }, { log += "playAll" }, { log += "shuffleAll" },
        { t, a -> log += "menu:${t.title}:$a" }, { log += "album:$it" }, { log += "artist:$it" }, { log += "playlist:$it" },
    )
    private fun songs(n: Int) = LibraryContent.Songs(Samples.tracks(n).map { LibrarySong(it, it.title.endsWith("1")) })
    private fun show(s: LibraryUiState, pseudo: Boolean = false, fontScale: Float = 1f) { log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { LibraryContent(s, actions) } } }
    private fun state(c: LibraryContent, tab: LibraryTab = LibraryTab.SONGS, q: String = "") = LibraryUiState(tab, q, LibrarySort.RECENTLY_ADDED, c)

    @Test fun tabsAreBigEnoughAndSwitch() {
        show(state(songs(2)))
        for (t in LibraryTab.entries) rule.onNodeWithTag("library:tab:${t.name}").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("library:tab:ALBUMS").performClick(); assertEquals(listOf("tab:ALBUMS"), log)
    }

    @Test fun songRowsPlayFromTheTappedRowAndPlayShuffleWork() {
        show(state(songs(3)))
        rule.onNodeWithText("Song 3").performClick(); rule.onNodeWithText(str(R.string.library_play_all)).performClick(); rule.onNodeWithText(str(R.string.library_shuffle_all)).performClick()
        assertEquals(listOf("play:3:2", "playAll", "shuffleAll"), log)
    }

    @Test fun emptyAndNoMatchesAreDifferent() {
        show(state(LibraryContent.Empty)); rule.onNodeWithText(str(R.string.library_empty_songs)).assertExists()
    }
    @Test fun noMatches() { show(state(LibraryContent.NoMatches, q = "zzz")); rule.onNodeWithText(str(R.string.library_no_matches)).assertExists() }
    @Test fun errorAndLoading() { show(state(LibraryContent.Error)); rule.onNodeWithTag("state:error").assertExists() }
    @Test fun loadingState() { show(state(LibraryContent.Loading)); rule.onNodeWithTag("state:loading").assertExists() }

    @Test fun albumsArtistsPlaylistsNavigate() {
        show(state(LibraryContent.Albums(listOf(Samples.album(1))), LibraryTab.ALBUMS)); rule.onNodeWithText("Album 1").performClick()
    }
    @Test fun artistRowNavigates() { show(state(LibraryContent.Artists(listOf(Samples.artist(2))), LibraryTab.ARTISTS)); rule.onNodeWithText("Artist 2").performClick(); assertEquals(listOf("artist:UC2"), log) }
    @Test fun playlistRowNavigates() { show(state(LibraryContent.Playlists(listOf(Samples.playlist(3))), LibraryTab.PLAYLISTS)); rule.onNodeWithText("Playlist 3").performClick(); assertEquals(listOf("playlist:PL3"), log) }

    @Test fun sortAndQueryFeedTheActions() {
        show(state(songs(2))); rule.onNodeWithText(str(R.string.library_sort_title)).performClick(); rule.onNodeWithTag(TAG_LIBRARY_QUERY).performTextInput("ab")
        assertEquals(listOf("sort:TITLE", "query:ab"), log)
    }

    @Test fun aLargeListScrollsToTheEnd() {
        show(state(songs(1_500))); rule.onNodeWithTag(TAG_LIBRARY_LIST).performScrollToKey("song:track001500"); rule.onNodeWithText("Song 1500").assertIsDisplayed()
    }

    @Test fun songMenuHasLibraryActions() {
        show(state(songs(1))); rule.onNodeWithContentDescription(str(R.string.track_more, "Song 1")).performClick(); rule.onNodeWithText(str(R.string.library_remove_from_library)).performClick()
        assertEquals(listOf("menu:Song 1:REMOVE_FROM_LIBRARY"), log)
    }

    @Test fun survivesPseudolocaleAndBigFonts() { show(state(songs(3)), pseudo = true, fontScale = 2f); rule.onNodeWithTag("library:tab:SONGS").assertIsDisplayed() }
}
```

  (Split the multi-`show` tests as noted for Task 16; `performScrollToKey` needs the list items keyed as `song:<raw id>` with the item `key` returned by `TrackRow` hosting `LazyColumn` — the key string is `"song:" + track.id.raw`.)
- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.** Strings: `library_tab_songs` "Songs", `library_tab_albums` "Albums", `library_tab_artists` "Artists", `library_tab_playlists` "Playlists", `library_play_all` "Play", `library_shuffle_all` "Shuffle", `library_sort_recent` "Recently added", `library_sort_title` "A to Z", `library_filter_hint` "Filter this list", `library_empty_songs` "Like or add songs to see them here", `library_empty_albums` "Save albums to see them here", `library_empty_artists` "Follow artists to see them here", `library_empty_playlists` "Create a playlist to see it here", `library_no_matches` "Nothing matches your filter", `library_remove_like` "Remove like", `library_remove_from_library` "Remove from library". Remove `library_placeholder`. ViewModel:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.library

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository, private val playlists: PlaylistRepository, private val controller: PlayerController,
) : ViewModel() {
    private val tab = MutableStateFlow(LibraryTab.SONGS)
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(LibrarySort.RECENTLY_ADDED)

    private data class Req(val tab: LibraryTab, val query: String, val sort: LibrarySort)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val content: Flow<LibraryContent> = combine(tab, query.debounceIfTyping(), sort) { t, q, s -> Req(t, q, s) }.flatMapLatest { r ->
        val rows: Flow<LibraryContent> = when (r.tab) {
            LibraryTab.SONGS -> library.songs(r.query, r.sort).map { LibraryContent.Songs(it) }
            LibraryTab.ALBUMS -> library.albums(r.query, r.sort).map { LibraryContent.Albums(it) }
            LibraryTab.ARTISTS -> library.artists(r.query, r.sort).map { LibraryContent.Artists(it) }
            LibraryTab.PLAYLISTS -> playlists.playlists(r.query, r.sort).map { LibraryContent.Playlists(it) }
        }
        rows.map { c -> if (c.isEmpty()) (if (r.query.isBlank()) LibraryContent.Empty else LibraryContent.NoMatches) else c }
            .onStart { emit(LibraryContent.Loading) }.catch { emit(LibraryContent.Error) }
    }

    val state: StateFlow<LibraryUiState> = combine(tab, query, sort, content) { t, q, s, c -> LibraryUiState(t, q, s, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState(LibraryTab.SONGS, "", LibrarySort.RECENTLY_ADDED, LibraryContent.Loading))

    fun setTab(t: LibraryTab) { tab.value = t }
    fun setQuery(q: String) { query.value = q }
    fun setSort(s: LibrarySort) { sort.value = s }
    fun play(tracks: List<Track>, index: Int) = controller.play(tracks, index)
    private fun visibleTracks(): List<Track> = (state.value.content as? LibraryContent.Songs)?.rows?.map { it.track }.orEmpty()
    fun playAll() = controller.play(visibleTracks(), 0)
    fun shuffleAll() = controller.play(visibleTracks().shuffled(), 0)
    fun trackAction(t: Track, a: LibraryTrackAction) {
        when (a) {
            LibraryTrackAction.PLAY_NEXT -> controller.playNext(t)
            LibraryTrackAction.ADD_TO_QUEUE -> controller.addToQueue(listOf(t))
            LibraryTrackAction.UNLIKE -> viewModelScope.launch { library.setLiked(t, false) }
            LibraryTrackAction.REMOVE_FROM_LIBRARY -> viewModelScope.launch { library.setInLibrary(t, false) }
        }
    }
}

private fun LibraryContent.isEmpty() = when (this) {
    is LibraryContent.Songs -> rows.isEmpty(); is LibraryContent.Albums -> rows.isEmpty()
    is LibraryContent.Artists -> rows.isEmpty(); is LibraryContent.Playlists -> rows.isEmpty(); else -> false
}
```

  (`query.debounceIfTyping()` is `query.debounce(150)`; the test `Env` uses an unconfined dispatcher, where `debounce` still delays: if a test needs an immediate result, use `query` directly and put the debounce in the composable's text field instead. Decide in implementation: debounce in the composable (`LaunchedEffect(text) { delay(150); actions.onQuery(text) }`) and drop the operator here, so the ViewModel stays synchronous and the tests above pass as written.) `LibraryScreen.kt`: `LibraryContent` = `Column { ScrollableTabRow-free `Row` of four `Tab`s (`Modifier.testTag("library:tab:${t.name}").heightIn(min = 48.dp)`), a filter `OutlinedTextField` (tag `TAG_LIBRARY_QUERY`, hint `library_filter_hint`), a chip row (`FilterChip` Recently added / A to Z), on the Songs tab a Play and a Shuffle `Button`, then `when (content)`: `Loading -> LoadingState()`, `Empty -> EmptyState(emptyTitleFor(tab))`, `NoMatches -> EmptyState(R.string.library_no_matches)`, `Error -> ErrorState(onRetry = { actions.onTab(state.tab) })`, `Songs -> LazyColumn(Modifier.testTag(TAG_LIBRARY_LIST)) { itemsIndexed(rows, key = { _, r -> "song:" + r.track.id.raw }) { i, r -> TrackRow(r.track, onClick = { actions.onPlaySong(rows.map { it.track }, i) }, onMenu = { }, extraActions = libraryExtras(r)) } }` where the library menu uses `LibraryTrackAction` (Play next, Add to queue, Remove like shown only when `liked`, Remove from library) through `extraActions` and a `TrackRow` flag `showDefaultMenu = false`, `Albums/Artists/Playlists -> LazyColumn` of keyed `ListItem` rows (`"album:" + id`, `"artist:" + id`, `"playlist:" + id`) that call the open callbacks }`. Add the parameter `showDefaultMenu: Boolean = true` to `TrackRow` (Task 16 file) in this task. `rows.map { it.track }` is computed once with `remember(rows)`. `AriaNavHost`: `composable<LibraryRoute> { LibraryScreen(onAlbum = ..., onArtist = ..., onPlaylist = { nav.navigate(PlaylistRoute(it, local = true)) }) }`.
- [ ] **Step 4:** Push; green. **Commit** `feat(app): Library screen with SQL-backed lists and play windows`.

---

### Task 19: Album, Artist and remote Playlist pages (replace the temporary routes)

Slice 3. Detail pages load from the network (nothing cached in this sub-project), each with loading, error, offline and content states.

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/ui/detail/{AlbumViewModel,AlbumScreen,ArtistViewModel,ArtistScreen,PlaylistViewModel,PlaylistScreen}.kt`, `app/src/test/resources/robolectric.properties` (`sdk=35`)
- Modify: `app/build.gradle.kts` (`testImplementation(libs.robolectric)`), `strings.xml`, `plurals.xml`, `ui/shell/AriaNavHost.kt` (replace the `coming_soon` routes; keep one for local playlists until Task 20)
- Test: `app/src/test/kotlin/app/aria/music/ui/detail/{AlbumViewModelTest,ArtistViewModelTest,PlaylistViewModelTest}.kt`, `app/src/androidTest/kotlin/app/aria/music/ui/detail/DetailPagesTest.kt`

**Interfaces:**
- Consumes: `BrowseSource`, `LibraryRepository` (`isAlbumSaved`, `setAlbumSaved`, `isArtistFollowed`, `setArtistFollowed`, `setLiked`), `PlayerController`, `NetworkMonitor`, `AlbumDetail`, `ArtistDetail`, `PlaylistDetail`, `TrackPage`, `TrackRow`, `SavedStateHandle` (the route arguments are stored under the property names: `id`).
- Produces:

```kotlin
sealed interface AlbumUiState { data object Loading; data class Content(val detail: AlbumDetail, val saved: Boolean) : AlbumUiState; data object Error; data object Offline }
@HiltViewModel class AlbumViewModel @Inject constructor(handle: SavedStateHandle, browse: BrowseSource, library: LibraryRepository, controller: PlayerController, network: NetworkMonitor) : ViewModel() {
    val state: StateFlow<AlbumUiState>; fun retry(); fun play(index: Int = 0); fun shuffle(); fun addToQueue(); fun toggleSaved(); fun trackAction(t: Track, a: TrackMenuAction) }
sealed interface ArtistUiState { data object Loading; data class Content(val detail: ArtistDetail, val followed: Boolean) : ArtistUiState; data object Error; data object Offline }
@HiltViewModel class ArtistViewModel @Inject constructor(handle: SavedStateHandle, browse: BrowseSource, library: LibraryRepository, controller: PlayerController, network: NetworkMonitor) : ViewModel() {
    val state: StateFlow<ArtistUiState>; fun retry(); fun playTop(); fun shuffleTop(); fun playShelfSongs(tracks: List<Track>, index: Int); fun toggleFollowed(); fun trackAction(t: Track, a: TrackMenuAction) }
sealed interface PlaylistUiState { data object Loading; data class Content(val detail: PlaylistDetail, val loadingMore: Boolean, val moreFailed: Boolean) : PlaylistUiState; data object Error; data object Offline }
@HiltViewModel class PlaylistViewModel @Inject constructor(handle: SavedStateHandle, browse: BrowseSource, library: LibraryRepository, controller: PlayerController, network: NetworkMonitor) : ViewModel() {
    val state: StateFlow<PlaylistUiState>; fun retry(); fun loadMore(); fun play(index: Int = 0); fun shuffle(); fun addToQueue(); fun trackAction(t: Track, a: TrackMenuAction) }
data class AlbumActions(val onBack: () -> Unit, val onPlay: (Int) -> Unit, val onShuffle: () -> Unit, val onAddToQueue: () -> Unit, val onToggleSaved: () -> Unit, val onTrackMenu: (Track, TrackMenuAction) -> Unit, val onOpenAlbum: (String) -> Unit, val onRetry: () -> Unit)
data class ArtistActions(val onBack: () -> Unit, val onPlayTop: () -> Unit, val onShuffleTop: () -> Unit, val onToggleFollowed: () -> Unit, val onPlaySongs: (List<Track>, Int) -> Unit, val onOpenAlbum: (String) -> Unit, val onOpenArtist: (String) -> Unit, val onOpenPlaylist: (String) -> Unit, val onTrackMenu: (Track, TrackMenuAction) -> Unit, val onRetry: () -> Unit)
data class PlaylistActions(val onBack: () -> Unit, val onPlay: (Int) -> Unit, val onShuffle: () -> Unit, val onAddToQueue: () -> Unit, val onLoadMore: () -> Unit, val onTrackMenu: (Track, TrackMenuAction) -> Unit, val onRetry: () -> Unit)
@Composable fun AlbumContent(state: AlbumUiState, actions: AlbumActions, modifier: Modifier = Modifier); fun ArtistContent(state: ArtistUiState, actions: ArtistActions, modifier: Modifier = Modifier); fun PlaylistContent(state: PlaylistUiState, actions: PlaylistActions, modifier: Modifier = Modifier)
@Composable fun AlbumScreen(onBack, onAlbum); fun ArtistScreen(onBack, onAlbum, onArtist, onPlaylist); fun PlaylistScreen(onBack)
tags: TAG_DETAIL_SAVE, TAG_DETAIL_PLAY, TAG_DETAIL_SHUFFLE, TAG_DETAIL_MORE_FAILED
```

**Acceptance Criteria:**
- Each page: `Loading` then `Content`, `Error` (with Retry that reloads), `Offline` (no network call when `NetworkMonitor.online` is false); every network failure is a returned `Result`, never a crash.
- Album: header (artwork, title, artists, year), Play, Shuffle, Add to queue, Save to library toggle (description "Save to library"/"Remove from library", reflects `isAlbumSaved`), tracks play the album from the tapped one, "Other versions" tiles open other albums. Artist: Play and Shuffle use `topTracks`; Follow toggle; shelves of songs (play from there), albums, artists, playlists. Playlist: tracks with automatic "load more" (`playlistMore`, de-duplicated; a failure shows an inline Retry and keeps the loaded tracks); Play/Shuffle/Add to queue use the loaded tracks (windowed by the controller above 500).
- Opening a missing ID shows `Error`, not a crash; the temporary `coming_soon` routes for album and artist and remote playlist are gone (local playlists keep one until Task 20).

- [ ] **Step 1: Failing tests** (`@RunWith(RobolectricTestRunner::class)` because `SavedStateHandle` may touch Android classes):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AlbumViewModelTest {
    @Before fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun resetMain() = Dispatchers.resetMain()

    private class Env(scope: kotlinx.coroutines.CoroutineScope, online: Boolean = true) {
        val browse = FakeBrowseSource(); val library = FakeLibraryRepository(); val engine = FakePlaybackEngine(); val network = FakeNetworkMonitor(online)
        val detail = AlbumDetail(Samples.album(1), Samples.tracks(3), listOf(Samples.album(2)))
        init { browse.albums["MPREb_1"] = Result.success(detail) }
        val vm = AlbumViewModel(SavedStateHandle(mapOf("id" to "MPREb_1")), browse, library, PlayerController(engine, ServiceLauncher { }, scope), network)
            .also { scope.launch { it.state.collect { } } }
    }

    @Test fun loadsContentWithSavedFlag() = runTest {
        val e = Env(backgroundScope); val c = e.vm.state.value as AlbumUiState.Content
        assertEquals(e.detail, c.detail); assertFalse(c.saved)
    }
    @Test fun toggleSavedWritesTheLibraryAndFlipsTheFlag() = runTest {
        val e = Env(backgroundScope); e.vm.toggleSaved()
        assertTrue((e.vm.state.value as AlbumUiState.Content).saved); assertEquals(listOf(Samples.album(1)), e.library.albums("", LibrarySort.TITLE).first())
        e.vm.toggleSaved(); assertFalse((e.vm.state.value as AlbumUiState.Content).saved)
    }
    @Test fun playStartsFromTheTappedTrackAndShuffleUsesAllTracks() = runTest {
        val e = Env(backgroundScope); e.vm.play(2); e.vm.shuffle(); e.vm.addToQueue()
        assertEquals(Samples.tracks(3) to 2, e.engine.playNowCalls[0]); assertEquals(3, e.engine.playNowCalls[1].first.size); assertEquals(1, e.engine.addedToEnd.size)
    }
    @Test fun missingAlbumIsAnErrorAndRetryReloads() = runTest {
        val e = Env(backgroundScope); e.browse.albums["MPREb_1"] = Result.failure(java.io.IOException())
        e.vm.retry(); assertEquals(AlbumUiState.Error, e.vm.state.value)
        e.browse.albums["MPREb_1"] = Result.success(e.detail); e.vm.retry(); assertTrue(e.vm.state.value is AlbumUiState.Content)
    }
    @Test fun offlineDoesNotCallTheNetwork() = runTest {
        val e = Env(backgroundScope, online = false); assertEquals(AlbumUiState.Offline, e.vm.state.value)
    }
    @Test fun trackMenuActionsReachTheControllerAndLibrary() = runTest {
        val e = Env(backgroundScope); val t = Samples.track(1)
        e.vm.trackAction(t, TrackMenuAction.PLAY_NEXT); e.vm.trackAction(t, TrackMenuAction.ADD_TO_QUEUE); e.vm.trackAction(t, TrackMenuAction.LIKE)
        assertTrue("playNext" in e.engine.calls); assertTrue(e.library.isLiked(t.id).first())
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ArtistViewModelTest {
    @Before fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun resetMain() = Dispatchers.resetMain()

    private class Env(scope: kotlinx.coroutines.CoroutineScope) {
        val browse = FakeBrowseSource(); val library = FakeLibraryRepository(); val engine = FakePlaybackEngine()
        val songs = Shelf("Songs", null, Samples.tracks(2).map { BrowseItem.Song(it) })
        val detail = ArtistDetail(Samples.artist(1), "About", "1M", listOf(songs, Shelf("Albums", null, listOf(BrowseItem.Album(Samples.album(1))))))
        init { browse.artists["UC1"] = Result.success(detail) }
        val vm = ArtistViewModel(SavedStateHandle(mapOf("id" to "UC1")), browse, library, PlayerController(engine, ServiceLauncher { }, scope), FakeNetworkMonitor())
            .also { scope.launch { it.state.collect { } } }
    }

    @Test fun contentAndFollowToggle() = runTest {
        val e = Env(backgroundScope); assertFalse((e.vm.state.value as ArtistUiState.Content).followed)
        e.vm.toggleFollowed(); assertTrue((e.vm.state.value as ArtistUiState.Content).followed); assertEquals(listOf(Samples.artist(1)), e.library.artists("", LibrarySort.TITLE).first())
    }
    @Test fun playAndShuffleUseTheTopTracks() = runTest {
        val e = Env(backgroundScope); e.vm.playTop(); e.vm.shuffleTop()
        assertEquals(Samples.tracks(2) to 0, e.engine.playNowCalls[0]); assertEquals(2, e.engine.playNowCalls[1].first.size)
    }
    @Test fun playingAShelfSongStartsThere() = runTest { val e = Env(backgroundScope); e.vm.playShelfSongs(Samples.tracks(2), 1); assertEquals(Samples.tracks(2) to 1, e.engine.playNowCalls.single()) }
    @Test fun failureThenRetry() = runTest {
        val e = Env(backgroundScope); e.browse.artists["UC1"] = Result.failure(java.io.IOException()); e.vm.retry(); assertEquals(ArtistUiState.Error, e.vm.state.value)
        e.browse.artists["UC1"] = Result.success(e.detail); e.vm.retry(); assertTrue(e.vm.state.value is ArtistUiState.Content)
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaylistViewModelTest {
    @Before fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun resetMain() = Dispatchers.resetMain()

    private class Env(scope: kotlinx.coroutines.CoroutineScope, online: Boolean = true) {
        val browse = FakeBrowseSource(); val engine = FakePlaybackEngine()
        init { browse.playlists["PL1"] = Result.success(PlaylistDetail(Samples.playlist(1), Samples.tracks(2), continuation = "c1")) }
        val vm = PlaylistViewModel(SavedStateHandle(mapOf("id" to "PL1")), browse, FakeLibraryRepository(), PlayerController(engine, ServiceLauncher { }, scope), FakeNetworkMonitor(online))
            .also { scope.launch { it.state.collect { } } }
    }

    @Test fun loadMoreAppendsAndDeduplicatesUntilNoContinuation() = runTest {
        val e = Env(backgroundScope); e.browse.more["c1"] = Result.success(TrackPage(Samples.tracks(3, from = 2), "c2")); e.browse.more["c2"] = Result.success(TrackPage(Samples.tracks(1, from = 9), null))
        e.vm.loadMore(); e.vm.loadMore(); e.vm.loadMore()
        val c = e.vm.state.value as PlaylistUiState.Content
        assertEquals(listOf(1, 2, 3, 4, 9).map { Samples.track(it) }, c.detail.tracks); assertNull(c.detail.continuation); assertFalse(c.moreFailed)
    }
    @Test fun failedLoadMoreKeepsTracksAndCanRetry() = runTest {
        val e = Env(backgroundScope); e.vm.loadMore()
        var c = e.vm.state.value as PlaylistUiState.Content; assertTrue(c.moreFailed); assertEquals(2, c.detail.tracks.size)
        e.browse.more["c1"] = Result.success(TrackPage(Samples.tracks(1, from = 7), null)); e.vm.loadMore()
        c = e.vm.state.value as PlaylistUiState.Content; assertFalse(c.moreFailed); assertEquals(3, c.detail.tracks.size)
    }
    @Test fun playUsesLoadedTracks() = runTest { val e = Env(backgroundScope); e.vm.play(1); assertEquals(Samples.tracks(2) to 1, e.engine.playNowCalls.single()) }
    @Test fun offlineAndMissing() = runTest {
        assertEquals(PlaylistUiState.Offline, Env(backgroundScope, online = false).vm.state.value)
        val e = Env(backgroundScope); e.browse.playlists["PL1"] = Result.failure(java.io.IOException()); e.vm.retry(); assertEquals(PlaylistUiState.Error, e.vm.state.value)
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.aria.domain.*
import app.aria.domain.testing.Samples
import app.aria.music.R
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DetailPagesTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val log = mutableListOf<String>()
    private val album = AlbumActions({ log += "back" }, { log += "play:$it" }, { log += "shuffle" }, { log += "queue" }, { log += "save" }, { t, a -> log += "menu:${t.title}:$a" }, { log += "album:$it" }, { log += "retry" })
    private val artist = ArtistActions({ log += "back" }, { log += "playTop" }, { log += "shuffleTop" }, { log += "follow" }, { l, i -> log += "songs:${l.size}:$i" }, { log += "album:$it" }, { log += "artist:$it" }, { log += "playlist:$it" }, { t, a -> log += "menu:${t.title}:$a" }, { log += "retry" })
    private val playlist = PlaylistActions({ log += "back" }, { log += "play:$it" }, { log += "shuffle" }, { log += "queue" }, { log += "more" }, { t, a -> log += "menu:${t.title}:$a" }, { log += "retry" })
    private val albumDetail = AlbumDetail(Samples.album(1), Samples.tracks(2), listOf(Samples.album(2)))

    private fun showAlbum(s: AlbumUiState, pseudo: Boolean = false, fontScale: Float = 1f) { log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { AlbumContent(s, album) } } }

    @Test fun albumHeaderActionsAndTracks() {
        showAlbum(AlbumUiState.Content(albumDetail, saved = false))
        rule.onNodeWithText("Album 1").assertExists(); rule.onNodeWithTag(TAG_DETAIL_PLAY).performClick(); rule.onNodeWithTag(TAG_DETAIL_SHUFFLE).performClick()
        rule.onNodeWithTag(TAG_DETAIL_SAVE).assertWidthIsAtLeast(48.dp).performClick(); rule.onNodeWithText("Song 2").performClick(); rule.onNodeWithText("Album 2").performClick()
        assertEquals(listOf("play:0", "shuffle", "save", "play:1", "album:MPREb_2"), log)
    }
    @Test fun saveToggleDescriptionFlips() { showAlbum(AlbumUiState.Content(albumDetail, saved = true)); rule.onNodeWithContentDescription(str(R.string.detail_unsave)).assertExists() }
    @Test fun albumErrorOfflineLoading() { showAlbum(AlbumUiState.Error); rule.onNodeWithText(str(R.string.retry)).performClick(); assertEquals(listOf("retry"), log) }
    @Test fun albumOffline() { showAlbum(AlbumUiState.Offline); rule.onNodeWithTag("state:offline").assertIsDisplayed() }
    @Test fun albumLoading() { showAlbum(AlbumUiState.Loading); rule.onNodeWithTag("state:loading").assertIsDisplayed() }
    @Test fun albumSurvivesPseudolocaleAndBigFonts() { showAlbum(AlbumUiState.Content(albumDetail, false), pseudo = true, fontScale = 2f); rule.onNodeWithTag(TAG_DETAIL_PLAY).assertExists() }

    @Test fun artistPageShelvesAndActions() {
        val shelves = listOf(Shelf("Songs", null, Samples.tracks(2).map { BrowseItem.Song(it) }), Shelf("Albums", null, listOf(BrowseItem.Album(Samples.album(1)))))
        log.clear(); rule.setContent { AriaTestTheme { ArtistContent(ArtistUiState.Content(ArtistDetail(Samples.artist(1), "About text", "1M", shelves), followed = false), artist) } }
        rule.onNodeWithText("About text").assertExists(); rule.onNodeWithTag(TAG_DETAIL_PLAY).performClick(); rule.onNodeWithTag(TAG_DETAIL_SAVE).performClick(); rule.onNodeWithText("Song 2").performClick(); rule.onNodeWithText("Album 1").performClick()
        assertEquals(listOf("playTop", "follow", "songs:2:1", "album:MPREb_1"), log)
    }

    @Test fun playlistShowsMoreFailedWithRetryAndLoadsMoreAtTheEnd() {
        log.clear(); rule.setContent { AriaTestTheme { PlaylistContent(PlaylistUiState.Content(PlaylistDetail(Samples.playlist(1), Samples.tracks(30), "c"), loadingMore = false, moreFailed = true), playlist) } }
        rule.onNodeWithTag("detail:list").performScrollToIndex(31); rule.onNodeWithTag(TAG_DETAIL_MORE_FAILED).assertExists(); rule.onNodeWithText(str(R.string.retry)).performClick()
        assert("more" in log)
    }
}
```

- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.** Add `testImplementation(libs.robolectric)` to `app/build.gradle.kts` and `app/src/test/resources/robolectric.properties` containing `sdk=35`. Strings: `detail_play` "Play", `detail_shuffle` "Shuffle", `detail_add_to_queue` "Add to queue", `detail_save` "Save to library", `detail_unsave` "Remove from library", `detail_follow` "Follow", `detail_unfollow` "Unfollow", `detail_other_versions` "Other versions", `detail_about` "About", `detail_more_failed` "Could not load more songs", `detail_loading_more` "Loading more songs". Plural: `<plurals name="detail_track_count"><item quantity="one">%1$d song</item><item quantity="other">%1$d songs</item></plurals>`. Each ViewModel follows this skeleton (Album shown; Artist and Playlist differ only in their calls, below):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.detail

@HiltViewModel
class AlbumViewModel @Inject constructor(
    handle: SavedStateHandle, private val browse: BrowseSource, private val library: LibraryRepository,
    private val controller: PlayerController, private val network: NetworkMonitor,
) : ViewModel() {
    private val id: String = checkNotNull(handle.get<String>("id")) { "AlbumRoute id missing" }
    private sealed interface Load { data object Loading : Load; data class Done(val detail: AlbumDetail) : Load; data object Failed : Load; data object Offline : Load }
    private val load = MutableStateFlow<Load>(Load.Loading)

    init { retry() }

    fun retry() {
        viewModelScope.launch {
            if (!network.online.value) { load.value = Load.Offline; return@launch }
            load.value = Load.Loading
            load.value = browse.album(id).fold({ Load.Done(it) }, { Load.Failed })
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AlbumUiState> = load.flatMapLatest { l ->
        when (l) {
            Load.Loading -> flowOf(AlbumUiState.Loading); Load.Failed -> flowOf(AlbumUiState.Error); Load.Offline -> flowOf(AlbumUiState.Offline)
            is Load.Done -> library.isAlbumSaved(l.detail.album.id).map { AlbumUiState.Content(l.detail, it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumUiState.Loading)

    private fun detail() = (state.value as? AlbumUiState.Content)?.detail
    fun play(index: Int = 0) { detail()?.let { controller.play(it.tracks, index) } }
    fun shuffle() { detail()?.let { controller.play(it.tracks.shuffled(), 0) } }
    fun addToQueue() { detail()?.let { controller.addToQueue(it.tracks) } }
    fun toggleSaved() { val c = state.value as? AlbumUiState.Content ?: return; viewModelScope.launch { library.setAlbumSaved(c.detail.album, !c.saved) } }
    fun trackAction(t: Track, a: TrackMenuAction) {
        when (a) {
            TrackMenuAction.PLAY_NEXT -> controller.playNext(t); TrackMenuAction.ADD_TO_QUEUE -> controller.addToQueue(listOf(t))
            TrackMenuAction.LIKE -> viewModelScope.launch { library.setLiked(t, true) }
        }
    }
}
```

  `ArtistViewModel` is the same with `browse.artist(id)`, `Load.Done(detail: ArtistDetail)`, content `ArtistUiState.Content(l.detail, followed)` from `library.isArtistFollowed(l.detail.artist.id)`, and `fun playTop() { detail()?.topTracks?.takeIf { it.isNotEmpty() }?.let { controller.play(it, 0) } }`, `fun shuffleTop() { ... controller.play(it.shuffled(), 0) }`, `fun playShelfSongs(tracks: List<Track>, index: Int) = controller.play(tracks, index)`, `fun toggleFollowed() { ... library.setArtistFollowed(c.detail.artist, !c.followed) }`. `PlaylistViewModel` keeps `MutableStateFlow<PlaylistUiState>` directly: `retry()` loads `browse.playlist(id)` into `Content(detail, loadingMore = false, moreFailed = false)`; `loadMore()`:

```kotlin
    private var loadingMore = false
    fun loadMore() {
        val c = state.value as? PlaylistUiState.Content ?: return
        val token = c.detail.continuation ?: return
        if (loadingMore) return
        loadingMore = true
        state.value = c.copy(loadingMore = true, moreFailed = false)
        viewModelScope.launch {
            browse.playlistMore(token).fold(
                { page ->
                    val known = c.detail.tracks.mapTo(HashSet()) { it.id }
                    state.value = PlaylistUiState.Content(c.detail.copy(tracks = c.detail.tracks + page.tracks.filter { known.add(it.id) }, continuation = page.continuation), false, false)
                },
                { state.value = c.copy(loadingMore = false, moreFailed = true) },
            )
            loadingMore = false
        }
    }
```

  (with `val state` exposed as `StateFlow` over a private `MutableStateFlow`; the test `AlbumViewModelTest`-style `.collect` works with it.) Screens: each `XContent` is a `LazyColumn(Modifier.testTag("detail:list"))` with a header item (artwork 160dp via `AriaImage`, title, subtitle line from data, a track-count plural for albums and playlists, a row of `Button(TAG_DETAIL_PLAY)` "Play", `OutlinedButton(TAG_DETAIL_SHUFFLE)` "Shuffle", `IconButton(onAddToQueue)`, and the 48dp save/follow `IconButton(Modifier.testTag(TAG_DETAIL_SAVE))` with `AriaIcons.Favorite`/`FavoriteBorder` and the description from `detail_save`/`detail_unsave` or `detail_follow`/`detail_unfollow`), then `itemsIndexed(tracks, key = { i, t -> "track:$i:${t.id.raw}" })` of `TrackRow(onClick = { actions.onPlay(i) }, onMenu = { a -> actions.onTrackMenu(track, a) })`; albums add an "Other versions" `LazyRow` of tiles; artists render the description (`detail_about`), then each shelf like Home's `ShelfRow` (reuse: move `ShelfRow` and `Tile` from `HomeScreen.kt` to `ui/common/Shelves.kt` in this task and import them in both); playlists trigger `actions.onLoadMore()` from a `LaunchedEffect` when the last item becomes visible (`snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }`) and render a footer (`detail_loading_more` spinner, or `Row(Modifier.testTag(TAG_DETAIL_MORE_FAILED)) { Text(detail_more_failed); TextButton(onClick = actions.onLoadMore) { Text(retry) } }`). Loading/Error/Offline render the shared states inside a column with a back `IconButton` on top. `AriaNavHost`: replace the temporary entries: `composable<AlbumRoute> { AlbumScreen(onBack = { nav.popBackStack() }, onAlbum = { nav.navigate(AlbumRoute(it)) }) }`, `composable<ArtistRoute> { ArtistScreen(onBack = ..., onAlbum = ..., onArtist = { nav.navigate(ArtistRoute(it)) }, onPlaylist = { nav.navigate(PlaylistRoute(it, local = false)) }) }`, `composable<PlaylistRoute> { entry -> if (entry.toRoute<PlaylistRoute>().local) EmptyState(R.string.coming_soon) else PlaylistScreen(onBack = { nav.popBackStack() }) }`.
- [ ] **Step 4:** Push; green on `verify`, `app-ui`. **Commit** `feat(app): album, artist and playlist pages`.

---

### Task 20: Playlist management UI: create, rename, delete, add tracks, reorder (transactional)

Slice 3. The reorder step is the third item in the cut order; everything else stays.

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/ui/playlists/{LocalPlaylistViewModel,LocalPlaylistScreen,PlaylistPicker,NameDialog}.kt`
- Modify: `strings.xml`, `plurals.xml`, `ui/shell/AriaNavHost.kt`, `ui/library/{LibraryViewModel,LibraryScreen}.kt` (New playlist), `ui/search/SearchScreen.kt`, `ui/library/LibraryScreen.kt`, `ui/detail/{AlbumScreen,ArtistScreen,PlaylistScreen}.kt`, `ui/home/HomeScreen.kt` (Add to playlist in track menus)
- Test: `app/src/test/kotlin/app/aria/music/ui/playlists/{LocalPlaylistViewModelTest,PlaylistPickerViewModelTest}.kt`, `app/src/androidTest/kotlin/app/aria/music/ui/playlists/PlaylistsTest.kt`

**Interfaces:**
- Consumes: `PlaylistRepository` (`create`, `rename`, `delete`, `addTracks`, `removeEntry`, `reorder`, `playlist`, `tracks`, `playlists`), `PlayerController`, `TrackRow(extraActions)`.
- Produces:

```kotlin
sealed interface LocalPlaylistUiState { data object Loading; data class Content(val playlist: PlaylistRef, val rows: List<PlaylistTrackRow>) : LocalPlaylistUiState; data object NotFound; data object Error }
@HiltViewModel class LocalPlaylistViewModel @Inject constructor(handle: SavedStateHandle, playlists: PlaylistRepository, controller: PlayerController) : ViewModel() {
    val state: StateFlow<LocalPlaylistUiState>; val deleted: StateFlow<Boolean>
    fun play(index: Int = 0); fun shuffle(); fun addToQueue(); fun rename(name: String); fun delete(); fun remove(entryId: Int); fun moveUp(entryId: Int); fun moveDown(entryId: Int); fun trackAction(t: Track, a: TrackMenuAction)
}
object PlaylistNames { const val MAX = 100; fun clean(raw: String): String? }       // trimmed, non-blank, at most 100 chars
@HiltViewModel class PlaylistPickerViewModel @Inject constructor(playlists: PlaylistRepository) : ViewModel() {
    val playlists: StateFlow<List<PlaylistRef>>; suspend fun addTo(playlistId: String, track: Track); suspend fun createAndAdd(name: String, track: Track): String? ; suspend fun create(name: String): String? }
data class LocalPlaylistActions(val onBack: () -> Unit, val onPlay: (Int) -> Unit, val onShuffle: () -> Unit, val onAddToQueue: () -> Unit, val onRename: (String) -> Unit, val onDelete: () -> Unit, val onRemove: (Int) -> Unit, val onMoveUp: (Int) -> Unit, val onMoveDown: (Int) -> Unit, val onTrackMenu: (Track, TrackMenuAction) -> Unit)
@Composable fun LocalPlaylistContent(state: LocalPlaylistUiState, actions: LocalPlaylistActions, modifier: Modifier = Modifier)
@Composable fun NameDialog(@StringRes title: Int, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit)
@Composable fun AddToPlaylistDialog(track: Track, onDismiss: () -> Unit, viewModel: PlaylistPickerViewModel = hiltViewModel())
tags: "playlist:row:<entryId>", "playlist:menu:<entryId>", TAG_PLAYLIST_RENAME, TAG_PLAYLIST_DELETE, TAG_NEW_PLAYLIST
```

**Acceptance Criteria:**
- Create (Library Playlists tab "New playlist", dialog rejects blank names), rename, delete with confirmation (entries cascade; the page closes via `deleted`); "Add to playlist" appears in the track menu of Search, Library, Home, Album, Artist and Playlist rows, lists the user's playlists plus "New playlist", adds the track (songs are upserted but not added to the library) and shows "Added to <name>" before closing.
- The local playlist page plays, shuffles and queues its tracks; rows are keyed by `entryId` (duplicates allowed); remove deletes one entry.
- **Reorder** (CUTTABLE, third in the cut order): per-row Move up/Move down in the row menu (disabled at the ends) call `PlaylistRepository.reorder` with the full new id order, a single transactional rewrite (proven in Task 4); the view model never writes partial orders; edges and unknown ids are ignored.

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.playlists

import androidx.lifecycle.SavedStateHandle
import app.aria.domain.*
import app.aria.domain.testing.*
import app.aria.music.player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LocalPlaylistViewModelTest {
    @Before fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun resetMain() = Dispatchers.resetMain()

    private class Env(scope: kotlinx.coroutines.CoroutineScope) {
        val repo = FakePlaylistRepository(); val engine = FakePlaybackEngine(); var id = ""
        lateinit var vm: LocalPlaylistViewModel
        suspend fun init(scope: kotlinx.coroutines.CoroutineScope): Env {
            id = repo.create("Mine"); repo.addTracks(id, Samples.tracks(3))
            vm = LocalPlaylistViewModel(SavedStateHandle(mapOf("id" to id)), repo, PlayerController(engine, ServiceLauncher { }, scope)); scope.launch { vm.state.collect { } }; return this
        }
        fun titles() = ((vm.state.value as LocalPlaylistUiState.Content).rows.map { it.track.title })
        fun entry(i: Int) = (vm.state.value as LocalPlaylistUiState.Content).rows[i].entryId
    }

    @Test fun contentPlayShuffleQueue() = runTest {
        val e = Env(backgroundScope).init(backgroundScope)
        assertEquals(listOf("Song 1", "Song 2", "Song 3"), e.titles())
        e.vm.play(1); e.vm.shuffle(); e.vm.addToQueue()
        assertEquals(Samples.tracks(3) to 1, e.engine.playNowCalls[0]); assertEquals(3, e.engine.playNowCalls[1].first.size); assertEquals(1, e.engine.addedToEnd.size)
    }

    @Test fun moveUpAndDownRewriteTheWholeOrder() = runTest {
        val e = Env(backgroundScope).init(backgroundScope)
        e.vm.moveUp(e.entry(2)); assertEquals(listOf("Song 1", "Song 3", "Song 2"), e.titles())
        e.vm.moveDown(e.entry(0)); assertEquals(listOf("Song 3", "Song 1", "Song 2"), e.titles())
    }

    @Test fun edgeMovesAndUnknownIdsAreIgnored() = runTest {
        val e = Env(backgroundScope).init(backgroundScope)
        e.vm.moveUp(e.entry(0)); e.vm.moveDown(e.entry(2)); e.vm.moveUp(9_999)
        assertEquals(listOf("Song 1", "Song 2", "Song 3"), e.titles())
    }

    @Test fun removeRenameAndDelete() = runTest {
        val e = Env(backgroundScope).init(backgroundScope)
        e.vm.remove(e.entry(1)); assertEquals(listOf("Song 1", "Song 3"), e.titles())
        e.vm.rename("  New name  "); assertEquals("New name", (e.vm.state.value as LocalPlaylistUiState.Content).playlist.title)
        e.vm.rename("   "); assertEquals("New name", (e.vm.state.value as LocalPlaylistUiState.Content).playlist.title)
        e.vm.delete(); assertTrue(e.vm.deleted.value); assertEquals(LocalPlaylistUiState.NotFound, e.vm.state.value)
    }

    @Test fun namesAreCleaned() {
        assertEquals("A", PlaylistNames.clean("  A ")); assertNull(PlaylistNames.clean("   ")); assertEquals(100, PlaylistNames.clean("x".repeat(300))!!.length)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistPickerViewModelTest {
    @Before fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun addsToAnExistingPlaylistWithoutPuttingTheSongInTheLibrary() = runTest {
        val repo = FakePlaylistRepository(); val id = repo.create("Mix"); val vm = PlaylistPickerViewModel(repo); backgroundScope.launch { vm.playlists.collect { } }
        assertEquals(listOf("Mix"), vm.playlists.value.map { it.title })
        vm.addTo(id, Samples.track(1)); assertEquals(1, repo.tracks(id).first().size)
    }

    @Test fun createAndAddRejectsBlankNames() = runTest {
        val repo = FakePlaylistRepository(); val vm = PlaylistPickerViewModel(repo)
        assertNull(vm.createAndAdd("  ", Samples.track(1))); val id = vm.createAndAdd(" New ", Samples.track(1))!!
        assertEquals("New", repo.playlist(id).first()?.title); assertEquals(1, repo.tracks(id).first().size)
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.playlists

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.aria.domain.*
import app.aria.domain.testing.Samples
import app.aria.music.R
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaylistsTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val log = mutableListOf<String>()
    private val actions = LocalPlaylistActions({ log += "back" }, { log += "play:$it" }, { log += "shuffle" }, { log += "queue" }, { log += "rename:$it" }, { log += "delete" }, { log += "remove:$it" }, { log += "up:$it" }, { log += "down:$it" }, { t, a -> log += "menu:${t.title}:$a" })
    private fun rows(n: Int) = Samples.tracks(n).mapIndexed { i, t -> PlaylistTrackRow(10 + i, t) }
    private fun content(n: Int = 3) = LocalPlaylistUiState.Content(PlaylistRef("LP1", "Mine", null, null, n), rows(n))
    private fun show(s: LocalPlaylistUiState, pseudo: Boolean = false, fontScale: Float = 1f) { log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { LocalPlaylistContent(s, actions) } } }

    @Test fun rowsPlayAndMenuMovesAndRemoves() {
        show(content())
        rule.onNodeWithText("Song 2").performClick()
        rule.onNodeWithTag("playlist:menu:11").performClick(); rule.onNodeWithText(str(R.string.queue_move_up)).performClick()
        rule.onNodeWithTag("playlist:menu:11").performClick(); rule.onNodeWithText(str(R.string.queue_move_down)).performClick()
        rule.onNodeWithTag("playlist:menu:11").performClick(); rule.onNodeWithText(str(R.string.playlist_remove_track)).performClick()
        assertEquals(listOf("play:1", "up:11", "down:11", "remove:11"), log)
    }
    @Test fun firstAndLastMovesAreDisabled() {
        show(content()); rule.onNodeWithTag("playlist:menu:10").performClick(); rule.onNodeWithText(str(R.string.queue_move_up)).assertIsNotEnabled()
    }
    @Test fun renameDialogRejectsBlankAndConfirmsTrimmedInput() {
        show(content()); rule.onNodeWithTag(TAG_PLAYLIST_RENAME).performClick()
        rule.onNodeWithText(str(R.string.playlist_name_confirm)).assertIsNotEnabled()
        rule.onNodeWithTag("name:field").performTextInput("Road trip"); rule.onNodeWithText(str(R.string.playlist_name_confirm)).performClick()
        assertEquals(listOf("rename:Road trip"), log)
    }
    @Test fun deleteAsksForConfirmation() {
        show(content()); rule.onNodeWithTag(TAG_PLAYLIST_DELETE).performClick(); assertEquals(emptyList<String>(), log)
        rule.onNodeWithText(str(R.string.playlist_delete_confirm)).performClick(); assertEquals(listOf("delete"), log)
    }
    @Test fun notFoundAndErrorStates() { show(LocalPlaylistUiState.NotFound); rule.onNodeWithTag("state:empty").assertIsDisplayed() }
    @Test fun survivesPseudolocaleAndBigFonts() { show(content(), pseudo = true, fontScale = 2f); rule.onNodeWithTag(TAG_PLAYLIST_RENAME).assertExists() }
}
```

- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.** Strings: `playlist_new` "New playlist", `playlist_name_title_new` "Name your playlist", `playlist_name_title_rename` "Rename playlist", `playlist_name_hint` "Playlist name", `playlist_name_confirm` "Save", `playlist_rename` "Rename", `playlist_delete` "Delete playlist", `playlist_delete_title` "Delete this playlist?", `playlist_delete_confirm` "Delete", `playlist_remove_track` "Remove from playlist", `playlist_not_found` "This playlist no longer exists", `track_add_to_playlist` "Add to playlist", `playlist_pick_title` "Add to playlist", `playlist_added_to` "Added to %1$s", `playlist_done` "Done". `PlaylistNames`:

```kotlin
object PlaylistNames {
    const val MAX = 100
    fun clean(raw: String): String? = raw.trim().take(MAX).trim().takeIf { it.isNotEmpty() }
}
```

  `LocalPlaylistViewModel`:

```kotlin
@HiltViewModel
class LocalPlaylistViewModel @Inject constructor(handle: SavedStateHandle, private val playlists: PlaylistRepository, private val controller: PlayerController) : ViewModel() {
    private val id: String = checkNotNull(handle.get<String>("id"))
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    val state: StateFlow<LocalPlaylistUiState> = combine(playlists.playlist(id), playlists.tracks(id)) { p, rows ->
        if (p == null) LocalPlaylistUiState.NotFound else LocalPlaylistUiState.Content(p, rows)
    }.catch { emit(LocalPlaylistUiState.Error) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalPlaylistUiState.Loading)

    private fun rows() = (state.value as? LocalPlaylistUiState.Content)?.rows.orEmpty()
    fun play(index: Int = 0) { val r = rows(); if (r.isNotEmpty()) controller.play(r.map { it.track }, index) }
    fun shuffle() { val r = rows(); if (r.isNotEmpty()) controller.play(r.map { it.track }.shuffled(), 0) }
    fun addToQueue() { controller.addToQueue(rows().map { it.track }) }
    fun rename(name: String) { val n = PlaylistNames.clean(name) ?: return; viewModelScope.launch { playlists.rename(id, n) } }
    fun delete() { viewModelScope.launch { playlists.delete(id); _deleted.value = true } }
    fun remove(entryId: Int) { viewModelScope.launch { playlists.removeEntry(id, entryId) } }
    fun moveUp(entryId: Int) = move(entryId, -1)
    fun moveDown(entryId: Int) = move(entryId, +1)

    /** Computes the complete new order and rewrites it in one repository call (one transaction). */
    private fun move(entryId: Int, delta: Int) {
        val ids = rows().map { it.entryId }
        val from = ids.indexOf(entryId); val to = from + delta
        if (from < 0 || to !in ids.indices) return
        val next = ids.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch { playlists.reorder(id, next) }
    }

    fun trackAction(t: Track, a: TrackMenuAction) {
        when (a) { TrackMenuAction.PLAY_NEXT -> controller.playNext(t); TrackMenuAction.ADD_TO_QUEUE -> controller.addToQueue(listOf(t)); TrackMenuAction.LIKE -> Unit }
    }
}
```

  `PlaylistPickerViewModel`: `val playlists: StateFlow<List<PlaylistRef>> = repo.playlists("", LibrarySort.RECENTLY_ADDED).stateIn(viewModelScope, WhileSubscribed(5_000), emptyList())`; `suspend fun addTo(id, track) = repo.addTracks(id, listOf(track))`; `suspend fun create(name): String? = PlaylistNames.clean(name)?.let { repo.create(it) }`; `suspend fun createAndAdd(name, track): String? = create(name)?.also { repo.addTracks(it, listOf(track)) }`. `NameDialog`: `AlertDialog` with an `OutlinedTextField(Modifier.testTag("name:field"))` (hint `playlist_name_hint`, `singleLine`, input capped at 100 chars) and a confirm `TextButton` (`playlist_name_confirm`, `enabled = PlaylistNames.clean(text) != null`) calling `onConfirm(PlaylistNames.clean(text)!!)`. `AddToPlaylistDialog`: a `Dialog` listing `playlists` rows (`Text(title)`, 56dp clickable) plus a "New playlist" row opening `NameDialog`; choosing a playlist runs `addTo` in `rememberCoroutineScope()` and switches the dialog body to `Text(stringResource(R.string.playlist_added_to, name))` with a `Done` button; `onDismiss` otherwise. `LocalPlaylistContent`: back button, header (name, plural track count, Play/Shuffle/Queue buttons, `IconButton(TAG_PLAYLIST_RENAME)` described `playlist_rename`, `IconButton(TAG_PLAYLIST_DELETE)` described `playlist_delete` opening a confirm dialog), `LazyColumn` of keyed rows (`key = { it.entryId }`) tagged `playlist:row:<entryId>` with the overflow menu tagged `playlist:menu:<entryId>` (Play, Move up (disabled for the first), Move down (disabled for the last), Remove from playlist); `NotFound -> EmptyState(R.string.playlist_not_found)`, `Error -> ErrorState`, `Loading -> LoadingState`. `LocalPlaylistScreen` pops back when `deleted` becomes true. `AriaNavHost`: `PlaylistRoute` with `local = true` now shows `LocalPlaylistScreen(onBack = { nav.popBackStack() })` (the last `coming_soon` for playlists is gone). Library: `LibraryViewModel.createPlaylist(name: String)` via `PlaylistRepository.create(PlaylistNames.clean(name) ?: return)`; `LibraryContent` shows a `Button(TAG_NEW_PLAYLIST)` "New playlist" above the Playlists tab list opening `NameDialog`. Add-to-playlist wiring: in each of `SearchScreen`, `LibraryScreen`, `HomeScreen`, `AlbumScreen`, `ArtistScreen`, `PlaylistScreen` hold `var pending by remember { mutableStateOf<Track?>(null) }`, pass `extraActions = listOf(R.string.track_add_to_playlist to { pending = track })` to `TrackRow`, and show `pending?.let { AddToPlaylistDialog(it, onDismiss = { pending = null }) }`.
- [ ] **Step 4 (CUTTABLE: reorder, cut order 3):** the Move up/Move down items, `moveUp`/`moveDown` and their tests are this step; if cut, delete them and the two menu items, and the repository `reorder` (already tested in Task 4) stays unused.
- [ ] **Step 5:** Push; green on `verify`, `app-ui`. **Commit** `feat(app): playlist management with transactional reorder`. **Slice 3 complete.**

---

### Task 21: Settings screen

Slice 4, decision 5 (UI part). The screen only edits `SettingsRepository`; applying values to the engine is the Application's job (Task 9), so nothing here touches playback.

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/ui/settings/{SettingsViewModel,SettingsScreen}.kt`
- Modify: `strings.xml`, `plurals.xml`, `ui/shell/AriaNavHost.kt` (replace the temporary `SettingsRoute`)
- Test: `app/src/test/kotlin/app/aria/music/ui/settings/SettingsViewModelTest.kt`, `app/src/androidTest/kotlin/app/aria/music/ui/settings/SettingsTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository`, `AppSettings`, `BuildConfig.VERSION_NAME`, `BuildConfig.BRAND_NAME`.
- Produces:

```kotlin
sealed interface SettingsUiState { data object Loading : SettingsUiState; data class Content(val settings: AppSettings) : SettingsUiState }
object SettingsChoices { val SLEEP_MINUTES = listOf(5, 10, 15, 30, 45, 60, 90); const val HISTORY_STEP_S = 5 }
@HiltViewModel class SettingsViewModel @Inject constructor(repo: SettingsRepository) : ViewModel() {
    val state: StateFlow<SettingsUiState>
    fun setHistorySeconds(seconds: Int); fun setEndlessRadio(on: Boolean); fun setSkipSilence(on: Boolean); fun setNormalizeLoudness(on: Boolean); fun setSleepDefaultMinutes(minutes: Int)
}
data class SettingsActions(val onBack: () -> Unit, val onHistorySeconds: (Int) -> Unit, val onEndlessRadio: (Boolean) -> Unit, val onSkipSilence: (Boolean) -> Unit, val onNormalize: (Boolean) -> Unit, val onSleepDefault: (Int) -> Unit)
@Composable fun SettingsContent(state: SettingsUiState, versionName: String, brandName: String, actions: SettingsActions, modifier: Modifier = Modifier)
@Composable fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel())
tags: TAG_SETTINGS_HISTORY, TAG_SETTINGS_RADIO, TAG_SETTINGS_SILENCE, TAG_SETTINGS_LOUDNESS, TAG_SETTINGS_SLEEP, TAG_SETTINGS_ABOUT
```

**Acceptance Criteria:**
- Settings: history threshold (slider 10-120 s in 5 s steps, value shown with a plural, state description), endless radio, skip silence, loudness normalization (switches with the label as the accessible name), default sleep timer (menu of 5, 10, 15, 30, 45, 60, 90 minutes). Every change is persisted through the repository and clamped by `AppSettings.normalized()`.
- About section: app name, version, the non-affiliation notice ("not affiliated with, endorsed by, or sponsored by Google, YouTube or YouTube Music"), and "No analytics. Nothing is sent to the Aria project." This closes the release-gate line about the About screen notice.
- Rows are at least 48dp; layout survives font scale 2.0 and `en-XA`; a `Loading` state exists.

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.settings

import app.aria.domain.AppSettings
import app.aria.domain.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private fun TestScope.env(): Pair<FakeSettingsRepository, SettingsViewModel> {
        val repo = FakeSettingsRepository(); val vm = SettingsViewModel(repo); backgroundScope.launch { vm.state.collect { } }; return repo to vm
    }

    @Test fun showsStoredValues() = runTest {
        val repo = FakeSettingsRepository(AppSettings(historyThresholdMs = 60_000, skipSilence = true)); val vm = SettingsViewModel(repo); backgroundScope.launch { vm.state.collect { } }
        val c = vm.state.value as SettingsUiState.Content; assertEquals(60_000L, c.settings.historyThresholdMs); assertTrue(c.settings.skipSilence)
    }

    @Test fun everyChangeIsPersisted() = runTest {
        val (repo, vm) = env()
        vm.setHistorySeconds(45); vm.setEndlessRadio(false); vm.setSkipSilence(true); vm.setNormalizeLoudness(false); vm.setSleepDefaultMinutes(30)
        assertEquals(AppSettings(historyThresholdMs = 45_000, endlessRadio = false, skipSilence = true, normalizeLoudness = false, sleepTimerDefaultMinutes = 30), repo.current)
    }

    @Test fun historyIsClampedToTenThroughOneTwentySeconds() = runTest {
        val (repo, vm) = env()
        vm.setHistorySeconds(1); assertEquals(10_000L, repo.current.historyThresholdMs)
        vm.setHistorySeconds(9_999); assertEquals(120_000L, repo.current.historyThresholdMs)
    }

    @Test fun onboardingFlagIsNotTouchedBySettingsChanges() = runTest {
        val repo = FakeSettingsRepository(AppSettings(onboardingDone = true)); val vm = SettingsViewModel(repo); vm.setEndlessRadio(false)
        assertTrue(repo.current.onboardingDone)
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.aria.domain.AppSettings
import app.aria.music.R
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val log = mutableListOf<String>()
    private val actions = SettingsActions({ log += "back" }, { log += "history:$it" }, { log += "radio:$it" }, { log += "silence:$it" }, { log += "loudness:$it" }, { log += "sleep:$it" })
    private fun show(s: SettingsUiState = SettingsUiState.Content(AppSettings()), pseudo: Boolean = false, fontScale: Float = 1f) {
        log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { SettingsContent(s, "0.1.0", "Aria", actions) } }
    }

    @Test fun switchesReflectStateAndInvokeActions() {
        show()
        rule.onNodeWithTag(TAG_SETTINGS_RADIO).assertIsOn().performClick()
        rule.onNodeWithTag(TAG_SETTINGS_SILENCE).assertIsOff().performClick()
        rule.onNodeWithTag(TAG_SETTINGS_LOUDNESS).assertIsOn().performClick()
        assertEquals(listOf("radio:false", "silence:true", "loudness:false"), log)
    }
    @Test fun rowsAreAtLeast48dp() {
        show(); for (t in listOf(TAG_SETTINGS_RADIO, TAG_SETTINGS_SILENCE, TAG_SETTINGS_LOUDNESS, TAG_SETTINGS_SLEEP)) rule.onNodeWithTag(t).assertHeightIsAtLeast(48.dp)
    }
    @Test fun historySliderDescribesItsValue() {
        show(); rule.onNodeWithTag(TAG_SETTINGS_HISTORY).assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.StateDescription))
        rule.onNodeWithText(str(R.string.settings_history_title)).assertExists()
    }
    @Test fun sleepDefaultMenuOffersTheChoices() {
        show(); rule.onNodeWithTag(TAG_SETTINGS_SLEEP).performClick(); rule.onNodeWithText(str(R.string.np_sleep_in_minutes, 45)).performClick(); assertEquals(listOf("sleep:45"), log)
    }
    @Test fun aboutShowsNonAffiliationAndNoAnalytics() {
        show(); rule.onNodeWithTag(TAG_SETTINGS_ABOUT).performScrollTo()
        rule.onNodeWithText(str(R.string.settings_about_non_affiliation)).assertExists(); rule.onNodeWithText(str(R.string.settings_about_no_analytics)).assertExists(); rule.onNodeWithText(str(R.string.settings_version, "0.1.0")).assertExists()
    }
    @Test fun loadingState() { show(SettingsUiState.Loading); rule.onNodeWithTag("state:loading").assertIsDisplayed() }
    @Test fun survivesPseudolocaleAndBigFonts() { show(pseudo = true, fontScale = 2f); rule.onNodeWithTag(TAG_SETTINGS_RADIO).assertExists() }
}
```

- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.** Strings: `settings_title` "Settings", `settings_playback` "Playback", `settings_history_title` "Count a play after", `settings_radio` "Endless radio", `settings_radio_body` "Add related songs when the queue runs low", `settings_silence` "Skip silence", `settings_loudness` "Normalize loudness", `settings_sleep_default` "Default sleep timer", `settings_about` "About", `settings_version` "Version %1$s", `settings_about_non_affiliation` "%1$s is an independent project. It is not affiliated with, endorsed by, or sponsored by Google, YouTube or YouTube Music.", `settings_about_no_analytics` "No analytics. Nothing is sent to the Aria project."; plural `<plurals name="settings_history_value"><item quantity="one">%1$d second</item><item quantity="other">%1$d seconds</item></plurals>`. (The non-affiliation string takes the brand name as `%1$s`; update the test's `str(R.string.settings_about_non_affiliation)` call to pass `"Aria"`.) ViewModel:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.settings

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    val state: StateFlow<SettingsUiState> = repo.settings.map<AppSettings, SettingsUiState> { SettingsUiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    private fun edit(f: (AppSettings) -> AppSettings) { viewModelScope.launch { repo.update(f) } }
    fun setHistorySeconds(seconds: Int) = edit { it.copy(historyThresholdMs = seconds * 1_000L) }
    fun setEndlessRadio(on: Boolean) = edit { it.copy(endlessRadio = on) }
    fun setSkipSilence(on: Boolean) = edit { it.copy(skipSilence = on) }
    fun setNormalizeLoudness(on: Boolean) = edit { it.copy(normalizeLoudness = on) }
    fun setSleepDefaultMinutes(minutes: Int) = edit { it.copy(sleepTimerDefaultMinutes = minutes) }
}
```

  `SettingsScreen.kt`: `SettingsContent` = scrollable `Column` with a top row (back `IconButton` 48dp, title) then: `SwitchRow(tag, @StringRes label, @StringRes body?, checked, onChange)` = `Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange).testTag(tag)) { Column(Modifier.weight(1f)) { Text(label); body?.let { Text(it) } }; Switch(checked = checked, onCheckedChange = null) }` (the row is the single accessible switch); the history `Column(Modifier.testTag(TAG_SETTINGS_HISTORY))` holds the label, a `Slider(value = seconds.toFloat(), onValueChange = { raw -> onHistorySeconds(((raw / 5).roundToInt() * 5)) }, valueRange = 10f..120f, steps = 21)` whose `semantics { stateDescription = pluralStringResource(R.plurals.settings_history_value, seconds, seconds) }` and a text of the same value; the sleep row `Row(Modifier.heightIn(min = 56.dp).clickable { open = true }.testTag(TAG_SETTINGS_SLEEP))` with a `DropdownMenu` of `SettingsChoices.SLEEP_MINUTES` items showing `stringResource(R.string.np_sleep_in_minutes, m)`; the About column tagged `TAG_SETTINGS_ABOUT` (name `brandName`, `settings_version`, `settings_about_non_affiliation` with `brandName`, `settings_about_no_analytics`). `Loading -> LoadingState()`. `SettingsScreen` passes `BuildConfig.VERSION_NAME` and `BuildConfig.BRAND_NAME` (`BuildConfig` is generated because `buildFeatures.buildConfig = true`). `AriaNavHost`: `composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }`.
- [ ] **Step 4:** Push; green. **Commit** `feat(app): settings screen`.

---

### Task 22: First-run onboarding (with the notification permission offered)

Slice 4. The permission rationale at the moment of first playback (Task 12) stays as the fallback; onboarding adds an optional up-front request.

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/ui/onboarding/{OnboardingViewModel,OnboardingScreen}.kt`, `app/src/main/kotlin/app/aria/music/ui/shell/ShellViewModel.kt`
- Modify: `app/src/main/kotlin/app/aria/music/ui/shell/AriaNavHost.kt` (root start destination and `ShellViewModel` move), `strings.xml`
- Test: `app/src/test/kotlin/app/aria/music/ui/{shell/ShellViewModelTest,onboarding/OnboardingViewModelTest}.kt`, `app/src/androidTest/kotlin/app/aria/music/ui/onboarding/OnboardingTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository` (`onboardingDone`), `NotificationPermissionPolicy`.
- Produces:

```kotlin
@HiltViewModel class ShellViewModel @Inject constructor(val controller: PlayerController, settings: SettingsRepository) : ViewModel() { val onboardingDone: StateFlow<Boolean?> }   // null until the first read
@HiltViewModel class OnboardingViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() { fun finish() }
enum class OnboardingPage { WELCOME, PRIVACY, NOTIFICATIONS }
data class OnboardingActions(val onFinish: () -> Unit, val onRequestNotifications: () -> Unit)
@Composable fun OnboardingContent(showNotificationPage: Boolean, actions: OnboardingActions, modifier: Modifier = Modifier)
@Composable fun OnboardingScreen(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel())
tags: TAG_ONBOARDING_NEXT, TAG_ONBOARDING_BACK, TAG_ONBOARDING_SKIP, TAG_ONBOARDING_FINISH, TAG_ONBOARDING_NOTIFY, TAG_ONBOARDING_PAGE
```

**Acceptance Criteria:**
- On first run (`onboardingDone == false`) the app opens `OnboardingRoute`; afterwards `HomeRoute`; while the flag is still loading nothing but an empty surface is shown (no flash of Home). Finishing (or skipping) stores `onboardingDone = true` and navigates to Home, clearing onboarding from the back stack.
- Three pages: Welcome (what the app does; that music comes from YouTube Music), Privacy (local library and playlists, no account, no analytics, the YouTube and image-CDN requests listed in plain words, non-affiliation), Notifications (API 33+ and permission missing only: explains why, "Allow notifications" button, never required). The permission is never requested without a tap.
- Next, Back, Skip and Get started are 48dp+, labelled from resources; the page counter is announced ("Page 2 of 3").

- [ ] **Step 1: Failing tests.**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

import app.aria.domain.AppSettings
import app.aria.domain.testing.*
import app.aria.music.player.PlayerController
import app.aria.music.player.ServiceLauncher
import app.aria.music.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {
    @BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun resetMain() = Dispatchers.resetMain()

    @Test fun nullUntilTheFlagIsRead() = runTest {
        val vm = ShellViewModel(PlayerController(FakePlaybackEngine(), ServiceLauncher { }, backgroundScope), FakeSettingsRepository())
        assertNull(vm.onboardingDone.value)
        backgroundScope.launch { vm.onboardingDone.collect { } }
        assertEquals(false, vm.onboardingDone.value)
    }

    @Test fun reflectsTheStoredFlag() = runTest {
        val vm = ShellViewModel(PlayerController(FakePlaybackEngine(), ServiceLauncher { }, backgroundScope), FakeSettingsRepository(AppSettings(onboardingDone = true)))
        backgroundScope.launch { vm.onboardingDone.collect { } }; assertEquals(true, vm.onboardingDone.value)
    }

    @Test fun finishingOnboardingStoresTheFlagAndKeepsOtherSettings() = runTest {
        val repo = FakeSettingsRepository(AppSettings(endlessRadio = false)); OnboardingViewModel(repo).finish()
        assertTrue(repo.current.onboardingDone); assertFalse(repo.current.endlessRadio)
    }
}
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.onboarding

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.aria.music.R
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingTest {
    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val log = mutableListOf<String>()
    private val actions = OnboardingActions({ log += "finish" }, { log += "notify" })
    private fun show(notify: Boolean = true, pseudo: Boolean = false, fontScale: Float = 1f) { log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { OnboardingContent(notify, actions) } } }

    @Test fun walksThroughThreePagesAndFinishes() {
        show()
        rule.onNodeWithText(str(R.string.onboarding_welcome_title)).assertIsDisplayed(); rule.onNodeWithTag(TAG_ONBOARDING_NEXT).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText(str(R.string.onboarding_privacy_title)).assertIsDisplayed(); rule.onNodeWithTag(TAG_ONBOARDING_NEXT).performClick()
        rule.onNodeWithText(str(R.string.onboarding_notifications_title)).assertIsDisplayed(); rule.onNodeWithTag(TAG_ONBOARDING_FINISH).performClick()
        assertEquals(listOf("finish"), log)
    }
    @Test fun backReturnsToThePreviousPage() {
        show(); rule.onNodeWithTag(TAG_ONBOARDING_NEXT).performClick(); rule.onNodeWithTag(TAG_ONBOARDING_BACK).performClick(); rule.onNodeWithText(str(R.string.onboarding_welcome_title)).assertIsDisplayed()
    }
    @Test fun skipFinishesImmediately() { show(); rule.onNodeWithTag(TAG_ONBOARDING_SKIP).performClick(); assertEquals(listOf("finish"), log) }
    @Test fun notificationButtonOnlyRequestsOnTap() {
        show(); rule.onNodeWithTag(TAG_ONBOARDING_NEXT).performClick(); rule.onNodeWithTag(TAG_ONBOARDING_NEXT).performClick()
        assertEquals(emptyList<String>(), log); rule.onNodeWithTag(TAG_ONBOARDING_NOTIFY).performClick(); assertEquals(listOf("notify"), log)
    }
    @Test fun withoutTheNotificationPageThePrivacyPageFinishes() {
        show(notify = false); rule.onNodeWithTag(TAG_ONBOARDING_NEXT).performClick(); rule.onNodeWithTag(TAG_ONBOARDING_FINISH).assertIsDisplayed(); rule.onNodeWithText(str(R.string.onboarding_notifications_title)).assertDoesNotExist()
    }
    @Test fun pageCounterIsAnnounced() { show(); rule.onNodeWithTag(TAG_ONBOARDING_PAGE).assertContentDescriptionContains(str(R.string.onboarding_page_of, 1, 3)) }
    @Test fun survivesPseudolocaleAndBigFonts() { show(pseudo = true, fontScale = 2f); rule.onNodeWithTag(TAG_ONBOARDING_NEXT).assertExists() }
}
```

- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.** Strings: `onboarding_welcome_title` "Welcome", `onboarding_welcome_body` "Search and play music from YouTube Music, keep a library and playlists on your phone, and control playback from the notification and lock screen.", `onboarding_privacy_title` "Your data stays here", `onboarding_privacy_body` "Your library, playlists and history stay on this device. There is no account and no analytics. Searching and playing contact YouTube Music, and artwork loads from Google's image servers, so Google sees your IP address. %1$s is not affiliated with Google or YouTube.", `onboarding_notifications_title` "Playback controls", `onboarding_notifications_body` "Allow notifications to control music from the notification shade and the lock screen. Music plays either way.", `onboarding_notify_allow` "Allow notifications", `onboarding_next` "Next", `onboarding_back` "Back", `onboarding_skip` "Skip", `onboarding_finish` "Get started", `onboarding_page_of` "Page %1$d of %2$d". `ShellViewModel.kt` (moved out of `AriaNavHost.kt`):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.shell

@HiltViewModel
class ShellViewModel @Inject constructor(val controller: PlayerController, settings: SettingsRepository) : ViewModel() {
    val onboardingDone: StateFlow<Boolean?> = settings.settings.map<AppSettings, Boolean?> { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
```

```kotlin
@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    fun finish() { viewModelScope.launch { settings.update { it.copy(onboardingDone = true) } } }
}
```

  `AriaRoot` becomes `fun AriaRoot(shell: ShellViewModel = hiltViewModel())`: `val done by shell.onboardingDone.collectAsStateWithLifecycle(); if (done == null) { Box(Modifier.fillMaxSize()); return }` before building the scaffold, passes `start = if (done == true) HomeRoute else OnboardingRoute` to `AriaNavHost(nav, start, modifier)` (`NavHost(nav, startDestination = start, ...)`), and `composable<OnboardingRoute> { OnboardingScreen(onDone = { nav.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } } }) }`. `OnboardingContent`: `var index by rememberSaveable { mutableIntStateOf(0) }`; `val pages = listOfNotNull(WELCOME, PRIVACY, NOTIFICATIONS.takeIf { showNotificationPage })`; a `Column` with a `Text` (tag `TAG_ONBOARDING_PAGE`, `semantics { contentDescription = stringResource(R.string.onboarding_page_of, index + 1, pages.size) }`) and the page body (title `headlineSmall`, body text; the Privacy body gets `stringResource(R.string.onboarding_privacy_body, stringResource(R.string.app_name))`; the Notifications page adds `Button(TAG_ONBOARDING_NOTIFY, Modifier.heightIn(min = 48.dp), onClick = actions.onRequestNotifications) { Text(onboarding_notify_allow) }`); a bottom row with `TextButton(TAG_ONBOARDING_SKIP)`, `TextButton(TAG_ONBOARDING_BACK)` (hidden on page 0), and `Button(TAG_ONBOARDING_NEXT)` or, on the last page, `Button(TAG_ONBOARDING_FINISH)` calling `actions.onFinish` (all `heightIn(min = 48.dp)`). `OnboardingScreen` computes `showNotificationPage = NotificationPermissionPolicy.shouldExplain(Build.VERSION.SDK_INT, granted, alreadyAsked = false, hasQueue = true)` with the same `checkSelfPermission` as Task 12, launches `ActivityResultContracts.RequestPermission` for `onRequestNotifications`, and calls `viewModel.finish()` then `onDone()` for `onFinish`.
- [ ] **Step 4 (CUTTABLE: onboarding polish, cut order 4):** swap the `index` state for a `HorizontalPager` (swipe between pages, the buttons still work) with an animated dot indicator (decorative, `clearAndSetSemantics {}`; the announced page counter stays), and add a simple vector illustration above each title. If cut, the three text pages above are the shipped onboarding; everything in Acceptance Criteria still holds.
- [ ] **Step 5:** Push; green on `verify`, `app-ui`, `playback-service`. **Commit** `feat(app): first-run onboarding`. **Slice 4 complete.**

---

### Task 23: Apple-style Now Playing skin (first to cut)

Slice 5, cut order 1. Same `NowPlayingUiState` and `NowPlayingActions`; a second composable chosen by a persisted setting.

**Files:**
- Create: `app/src/main/kotlin/app/aria/music/ui/nowplaying/{AppleNowPlaying,NowPlayingParts}.kt`
- Modify: `domain/src/main/kotlin/app/aria/domain/Settings.kt`, `data/src/main/kotlin/app/aria/data/DataStoreSettingsRepository.kt`, `ui/nowplaying/{MaterialNowPlaying,NowPlayingViewModel,NowPlayingScreen}.kt`, `ui/settings/{SettingsViewModel,SettingsScreen}.kt`, `strings.xml`
- Test: `data/src/test/.../DataStoreSettingsRepositoryTest.kt` (add a case), `app/src/test/.../NowPlayingViewModelTest.kt` and `SettingsViewModelTest.kt` (add cases), `app/src/androidTest/kotlin/app/aria/music/ui/nowplaying/NowPlayingSkinContractTest.kt`, `SettingsTest.kt` (add a case)

**Interfaces:**
- Consumes: `NowPlayingUiState`, `NowPlayingActions`, `SettingsRepository`, `GlassSurface`.
- Produces:

```kotlin
enum class NowPlayingSkin { MATERIAL, APPLE }                                // :domain
data class AppSettings(..., val nowPlayingSkin: NowPlayingSkin = NowPlayingSkin.MATERIAL)   // new last property
NowPlayingViewModel.skin: StateFlow<NowPlayingSkin>
SettingsViewModel.setNowPlayingSkin(skin: NowPlayingSkin); SettingsActions.onSkin: (NowPlayingSkin) -> Unit  // new last property
@Composable fun AppleNowPlayingContent(state: NowPlayingUiState, positionMs: () -> Long, actions: NowPlayingActions, modifier: Modifier = Modifier)
internal @Composable fun NowPlayingBanners(status: PlayerStatus, onRetry: () -> Unit); internal fun SeekBar(positionMs: () -> Long, durationMs: Long, onSeek: (Long) -> Unit); internal fun SleepMenu(state: NowPlayingUiState.Content, actions: NowPlayingActions)
tags: TAG_SETTINGS_SKIN
```

**Acceptance Criteria:**
- Both skins satisfy one contract test (same tags, descriptions, 48dp targets, callbacks, offline and error banners, empty state) run parameterized over `NowPlayingContent` and `AppleNowPlayingContent`, at font scale 2.0 and under `en-XA`.
- The Apple skin: large rounded artwork with a shadow, centered title and artists, the seek bar and a centered round play button inside a `GlassSurface` panel (fallback on API 26-30), secondary controls below; it adds no behaviour.
- The setting persists (DataStore key `now_playing_skin`, unknown stored values fall back to `MATERIAL`), Settings has a two-option selector (radio semantics), and `NowPlayingScreen` renders the chosen skin. Cutting this task removes only the files above; nothing earlier depends on it.

- [ ] **Step 1: Failing tests.** Data and view model cases:

```kotlin
    // DataStoreSettingsRepositoryTest: add
    @Test fun skinPersistsAndUnknownValuesFallBack() = runBlocking {
        val file = tmp.newFile("e.preferences_pb").also { it.delete() }
        val s1 = CoroutineScope(Job() + Dispatchers.IO)
        open(file, s1).update { it.copy(nowPlayingSkin = NowPlayingSkin.APPLE) }
        s1.cancel(); s1.coroutineContext[Job]!!.join()
        val s2 = CoroutineScope(Job() + Dispatchers.IO)
        assertEquals(NowPlayingSkin.APPLE, open(file, s2).settings.first().nowPlayingSkin)
        s2.cancel()
    }
```

```kotlin
    // SettingsViewModelTest: add
    @Test fun skinIsPersisted() = runTest { val (repo, vm) = env(); vm.setNowPlayingSkin(NowPlayingSkin.APPLE); assertEquals(NowPlayingSkin.APPLE, repo.current.nowPlayingSkin) }
    // NowPlayingViewModelTest: add (uses Env.settings)
    @Test fun skinFollowsTheSetting() = runTest {
        val e = Env(backgroundScope); backgroundScope.launch { e.vm.skin.collect { } }
        assertEquals(NowPlayingSkin.MATERIAL, e.vm.skin.value); e.settings.update { it.copy(nowPlayingSkin = NowPlayingSkin.APPLE) }; assertEquals(NowPlayingSkin.APPLE, e.vm.skin.value)
    }
```

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.aria.domain.*
import app.aria.music.R
import app.aria.music.player.PlayerStatus
import app.aria.music.ui.AriaTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

typealias Skin = @Composable (NowPlayingUiState, () -> Long, NowPlayingActions, Modifier) -> Unit

@RunWith(Parameterized::class)
class NowPlayingSkinContractTest(private val name: String, private val skin: Skin) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun skins(): List<Array<Any>> = listOf(
            arrayOf("material", { s: NowPlayingUiState, p: () -> Long, a: NowPlayingActions, m: Modifier -> NowPlayingContent(s, p, a, m) } as Skin),
            arrayOf("apple", { s: NowPlayingUiState, p: () -> Long, a: NowPlayingActions, m: Modifier -> AppleNowPlayingContent(s, p, a, m) } as Skin),
        )
    }

    @get:Rule val rule = createComposeRule()
    private fun str(id: Int, vararg a: Any) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *a)
    private val track = Track(TrackId.youtube("dQw4w9WgXcQ"), "Title", listOf("Artist"), 200_000, null)
    private fun content(status: PlayerStatus = PlayerStatus.PAUSED, liked: Boolean = false) = NowPlayingUiState.Content(track, status, 200_000, false, RepeatMode.OFF, null, liked, 3, 0, 15)
    private val log = mutableListOf<String>()
    private val actions = NowPlayingActions({ log += "back" }, { log += "toggle" }, { log += "next" }, { log += "previous" }, { log += "seek:$it" }, { log += "shuffle:$it" }, { log += "repeat" }, { log += "like" }, { log += "sleep:$it" }, { log += "sleepEnd" }, { log += "sleepOff" }, { log += "queue" })
    private fun show(s: NowPlayingUiState, pseudo: Boolean = false, fontScale: Float = 1f) { log.clear(); rule.setContent { AriaTestTheme(pseudo, fontScale) { skin(s, { 65_000L }, actions, Modifier) } } }

    @Test fun controlsAreDescribedAndBigEnough() {
        show(content())
        for (id in listOf(R.string.action_play, R.string.action_next, R.string.action_previous, R.string.np_like, R.string.np_queue, R.string.np_shuffle_off, R.string.np_repeat_off))
            rule.onNodeWithContentDescription(str(id)).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }
    @Test fun callbacksFire() {
        show(content()); rule.onNodeWithTag(TAG_NP_PLAY).performClick(); rule.onNodeWithTag(TAG_NP_LIKE).performClick(); rule.onNodeWithTag(TAG_NP_QUEUE).performClick()
        assertEquals(listOf("toggle", "like", "queue"), log)
    }
    @Test fun bannersAndEmptyState() { show(content(PlayerStatus.WAITING_FOR_NETWORK)); rule.onNodeWithTag(TAG_NP_OFFLINE).assertIsDisplayed() }
    @Test fun errorBannerOffersRetry() { show(content(PlayerStatus.ERROR)); rule.onNodeWithText(str(R.string.retry)).performClick(); assertEquals(listOf("toggle"), log) }
    @Test fun emptyState() { show(NowPlayingUiState.Empty); rule.onNodeWithContentDescription(str(R.string.back)).performClick(); assertEquals(listOf("back"), log) }
    @Test fun seekBarAndLyricsSlot() { show(content()); rule.onNodeWithTag(TAG_NP_SEEK).assertExists(); rule.onNodeWithTag(TAG_NP_LYRICS).assertExists() }
    @Test fun survivesPseudolocaleAndBigFonts() { show(content(PlayerStatus.PLAYING), pseudo = true, fontScale = 2f); rule.onNodeWithTag(TAG_NP_PLAY).assertIsDisplayed() }
}
```

  (In `SettingsTest` add `@Test fun skinSelectorOffersBothStyles()` that taps `str(R.string.settings_skin_apple)` and expects the action log `skin:APPLE`; `SettingsActions` gains `onSkin` as its last parameter, so the existing test's `actions` gets `{ log += "skin:$it" }` appended.)
- [ ] **Step 2:** Push; CI fails. Expected: FAIL.
- [ ] **Step 3: Implement.** `Settings.kt`: add `enum class NowPlayingSkin { MATERIAL, APPLE }` and `val nowPlayingSkin: NowPlayingSkin = NowPlayingSkin.MATERIAL` as the last `AppSettings` property. `DataStoreSettingsRepository`: `val nowPlayingSkin = stringPreferencesKey("now_playing_skin")` (import `stringPreferencesKey`); write `p[Keys.nowPlayingSkin] = next.nowPlayingSkin.name`; read `NowPlayingSkin.entries.firstOrNull { it.name == this[Keys.nowPlayingSkin] } ?: d.nowPlayingSkin`. `NowPlayingViewModel`: `val skin: StateFlow<NowPlayingSkin> = settings.settings.map { it.nowPlayingSkin }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingSkin.MATERIAL)` (keep `settings` as a constructor property: `private val settings: SettingsRepository`, used by both flows). Refactor `MaterialNowPlaying.kt`: move the banners (offline and error with Retry), `SeekBar` (make it `internal`) and the sleep menu into `NowPlayingParts.kt` as `NowPlayingBanners`, `SeekBar`, `SleepMenu` and call them from the Material skin; the Task 14 tests must stay green. `AppleNowPlaying.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui.nowplaying

@Composable
fun AppleNowPlayingContent(state: NowPlayingUiState, positionMs: () -> Long, actions: NowPlayingActions, modifier: Modifier = Modifier) {
    when (state) {
        NowPlayingUiState.Empty -> NowPlayingEmpty(actions.onBack, modifier)
        is NowPlayingUiState.Content -> Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = actions.onBack, modifier = Modifier.size(48.dp)) { Icon(AriaIcons.ExpandMore, contentDescription = stringResource(R.string.back)) }
                IconButton(onClick = actions.onOpenQueue, modifier = Modifier.size(48.dp).testTag(TAG_NP_QUEUE)) { Icon(AriaIcons.QueueMusic, contentDescription = stringResource(R.string.np_queue)) }
            }
            AriaImage(state.track.thumbnailUrl, null, Modifier.fillMaxWidth(0.86f).aspectRatio(1f).shadow(24.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)))
            Spacer(Modifier.height(24.dp))
            Text(state.track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            Text(state.track.artists.joinToString(stringResource(R.string.artist_separator)), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1)
            NowPlayingBanners(state.status, actions.onToggle)
            GlassSurface(Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(32.dp)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SeekBar(positionMs, state.durationMs, actions.onSeek)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        IconButton(onClick = actions.onPrevious, modifier = Modifier.size(56.dp)) { Icon(AriaIcons.SkipPrevious, stringResource(R.string.action_previous), Modifier.size(32.dp)) }
                        FilledIconButton(onClick = actions.onToggle, modifier = Modifier.size(72.dp).testTag(TAG_NP_PLAY)) {
                            val playing = state.status == PlayerStatus.PLAYING || state.status == PlayerStatus.BUFFERING
                            Icon(if (playing) AriaIcons.Pause else AriaIcons.PlayArrow, stringResource(if (playing) R.string.action_pause else R.string.action_play), Modifier.size(36.dp))
                        }
                        IconButton(onClick = actions.onNext, modifier = Modifier.size(56.dp)) { Icon(AriaIcons.SkipNext, stringResource(R.string.action_next), Modifier.size(32.dp)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                SecondaryControls(state, actions)      // shuffle, repeat, like (TAG_NP_LIKE), SleepMenu (TAG_NP_SLEEP): moved to NowPlayingParts.kt and shared with the Material skin
            }
            LyricsSlot(Modifier.testTag(TAG_NP_LYRICS))
        }
    }
}
```

  (`NowPlayingEmpty(onBack, modifier)` and `SecondaryControls(state, actions)` are the empty-state column and the mode row extracted from `MaterialNowPlaying.kt` into `NowPlayingParts.kt` in the same refactor, so both skins share them.) `NowPlayingScreen`: `val skin by viewModel.skin.collectAsStateWithLifecycle()` and `when (skin) { MATERIAL -> NowPlayingContent(...); APPLE -> AppleNowPlayingContent(...) }`. Settings: `SettingsViewModel.setNowPlayingSkin(skin) = edit { it.copy(nowPlayingSkin = skin) }`; `SettingsActions` gains `val onSkin: (NowPlayingSkin) -> Unit` last; `SettingsContent` adds a "Now Playing style" group (`Column(Modifier.selectableGroup().testTag(TAG_SETTINGS_SKIN))` with two `Row(Modifier.selectable(selected, role = Role.RadioButton, onClick).heightIn(min = 48.dp)) { RadioButton(selected, onClick = null); Text(label) }`); strings `settings_skin_title` "Now Playing style", `settings_skin_material` "Material", `settings_skin_apple` "Apple-style".
- [ ] **Step 4:** Push; green on `verify` (data, app unit tests) and `app-ui` (contract test on both skins, API 26 and 34). **Commit** `feat(app): Apple-style Now Playing skin and style setting`. **Slice 5 complete.**

---

### Task 24: Final checks, documentation and review

Definition of done (spec section 7). No new features.

**Files:**
- Create: `app/src/androidTest/kotlin/app/aria/music/ui/PseudoLocaleMatrixTest.kt`
- Modify: `docs/data-flow.md`, `PRIVACY.md`, `README.md`, `CONTRIBUTING.md`, `docs/manual-device-checklist.md`, `docs/release-gates.md`, `docs/providers/youtube-music.md`, `docs/provenance.csv` (the `app` row text), `app/src/main/res/values/strings.xml` (remove `coming_soon`), `scripts/ui-strings-baseline.txt` (must be comment-only)

**Interfaces:** none new.

**Acceptance Criteria:**
- `PseudoLocaleMatrixTest` renders every screen's stateless content (mini player, Now Playing in both skins, Queue, Search, Home, Library, Album, Artist, Playlist, Local playlist, Settings, Onboarding) under `en-XA` at font scale 2.0 and asserts the screen's primary node exists and, for bottom-bar and primary controls, is displayed (the layout check of decision 8); it passes on API 26 and 34.
- Docs match behaviour: `docs/data-flow.md` lists browse requests, suggestions as typed, Google image CDN requests (IP revealed to Google's image servers; 100 MB cache, never backed up) and the DataStore exclusion; `PRIVACY.md` says the same in plain words; `README.md` status paragraph describes the usable player; `CONTRIBUTING.md` lists `check-ui-strings.sh`, `check-screen-imports.sh` and `capture-innertube-fixtures`.
- `docs/release-gates.md`: the About-screen non-affiliation line is ticked (the Settings About section); "Parsers verified against live responses" stays unchecked with its instructions; the PoToken real-device gate is unchanged; a new unchecked line "TalkBack and font-scale pass on a device (manual checklist, UI shell section)".
- `docs/manual-device-checklist.md` has a "UI shell" section: TalkBack walkthrough of mini player, Now Playing and queue; font scale 2.0 and display size large; rotate during playback; first-run onboarding and the notification prompt on Android 13+; Library with 1 500 songs (scroll, sort, filter), play from song 900 and see the "queued 500 of N" message; offline Home shows the cached shelves and the "may be outdated" banner after 7 days (set the clock forward); airplane-mode search shows the offline state; Settings changes (history threshold, endless radio) take effect without restarting; Apple-style skin toggle.
- Greps return nothing outside `.internal/`: `coming_soon`, `Task 13`, `legacySearch`, `PlayerScreen`, `Canary`, `TODO`, `FIXME`. `scripts/ui-strings-baseline.txt` has no entries. `checkModuleGraph`, `checkLicense`, `check-no-destructive-migration.sh`, `check-backup-rules.sh` pass.

- [ ] **Step 1: Matrix test.** One test per screen using the same pattern (sample states are the ones the per-screen tests use):

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.music.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.aria.domain.*
import app.aria.domain.testing.Samples
import app.aria.music.player.PlayerStatus
import app.aria.music.ui.detail.*
import app.aria.music.ui.home.*
import app.aria.music.ui.library.*
import app.aria.music.ui.miniplayer.*
import app.aria.music.ui.nowplaying.*
import app.aria.music.ui.onboarding.*
import app.aria.music.ui.playlists.*
import app.aria.music.ui.queue.*
import app.aria.music.ui.search.*
import app.aria.music.ui.settings.*
import org.junit.Rule
import org.junit.Test

/** Decision 8: every screen's layout holds under the en-XA pseudolocale (long accented strings) and font scale 2.0. */
class PseudoLocaleMatrixTest {
    @get:Rule val rule = createComposeRule()
    private fun check(tag: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { AriaTestTheme(pseudo = true, fontScale = 2f) { content() } }
        rule.onNodeWithTag(tag).assertExists()
    }
    private val track = Samples.track(1)
    private val np = NowPlayingUiState.Content(track, PlayerStatus.PLAYING, 180_000, true, RepeatMode.ALL, SleepTimer(null, true), true, 3, 0, 15)
    private val npActions = NowPlayingActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Test fun miniPlayer() = check(TAG_MINI_PLAY) { MiniPlayerContent(MiniPlayerUiState.Showing("Title", listOf("A"), null, true, PlayerStatus.PLAYING, 1_000), { 0L }, {}, {}, {}) }
    @Test fun nowPlayingMaterial() = check(TAG_NP_PLAY) { NowPlayingContent(np, { 0L }, npActions) }
    @Test fun nowPlayingApple() = check(TAG_NP_PLAY) { AppleNowPlayingContent(np, { 0L }, npActions) }
    @Test fun queue() = check(TAG_QUEUE_LIST) { QueueContent(QueueUiState.Content(listOf(QueueRow(1, 0, track, true))), QueueActions({}, {}, {}, {}, {}, {})) }
    @Test fun settings() = check(TAG_SETTINGS_RADIO) { SettingsContent(SettingsUiState.Content(AppSettings()), "0.1.0", "Aria", SettingsActions({}, {}, {}, {}, {}, {}, {})) }
    @Test fun onboarding() = check(TAG_ONBOARDING_NEXT) { OnboardingContent(true, OnboardingActions({}, {})) }
    @Test fun home() = check(TAG_HOME_LIST) { HomeContent(HomeUiState(listOf(track), listOf(track), RemoteSection.Loading, false), HomeActions({ _, _ -> }, {}, {}, {}, {}, {}, { _, _ -> })) }
    @Test fun library() = check(TAG_LIBRARY_LIST) {
        LibraryContent(LibraryUiState(LibraryTab.SONGS, "", LibrarySort.RECENTLY_ADDED, LibraryContent.Songs(listOf(LibrarySong(track, false)))), LibraryActions({}, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, {}, {}, {}))
    }
}
```

  (Album, Artist, Playlist, Local playlist and Search follow the same one-line pattern using the tags `detail:list`, `detail:list`, `detail:list`, `playlist:menu:10`, and the search field tag; write them out when typing, using the states from `DetailPagesTest`, `PlaylistsTest` and `SearchTest`. `SettingsActions` includes `onSkin` since Task 23, hence seven lambdas.)
- [ ] **Step 1b: Manifest attack-surface guard (stress-test S6).** Create `scripts/check-manifest-surface.sh` plus `scripts/test_check_manifest_surface.py` (pytest, same style as `check-webview-safety.sh`): fail on any `app/src/main/AndroidManifest.xml` change that adds an `android:exported="true"` component other than `MainActivity`, an `<intent-filter>` other than the launcher on `MainActivity`, a `<uses-permission>` other than INTERNET, ACCESS_NETWORK_STATE and POST_NOTIFICATIONS, `usesCleartextTraffic="true"`, or a changed `allowBackup`; and fail on `navDeepLink` or `deepLinks` anywhere under `app/src/main/kotlin`. Pytest samples cover each failing case and the current manifest passing. Run it in the `verify` job next to the other `scripts/check-*` steps.
- [ ] **Step 2: Docs and cleanup.** Apply the Acceptance Criteria edits to the listed docs. Remove `coming_soon` from `strings.xml`. Update the `app` row of `docs/provenance.csv` to "Application module written for Aria: Hilt wiring, navigation shell, Compose screens (home, search, library, detail pages, now playing, queue, settings, onboarding), PlayerController, image pipeline, crash log, backup rules, foss/gms flavors, UI guard scripts". Run the greps.
- [ ] **Step 3: Final verification.** (1) Push; every CI job (`verify`, `glass-ui`, `playback-service`, `app-ui`) green on the final commit on API 26 and 34. (2) Dispatch `debug-apk` once, confirm both flavors build, hand the maintainer the run link and remind them to delete the artifact after downloading (ADR-0002). (3) The controller dispatches a final whole-branch review (fresh reviewer; this plan and the spec as inputs); fix findings and re-check CI. (4) Report to the maintainer: the manual checklist to run, that "Parsers verified against live responses" and the PoToken real-device gate remain open, and that no public release comes from this sub-project.
- [ ] **Step 4: Commit** `docs,test: pseudolocale matrix, docs and release gates for the UI shell`.

---

## Spec coverage table

Spec sections 1 to 8 and the nine resolved decisions of "Stress Test Results: UI shell".

| Spec item | Task(s) |
|---|---|
| 1 Scope: navigation shell, three tabs, persistent mini player | 12, 13 |
| 1 Home (Quick picks, shelves, Recently played, pull to refresh, cached copy) | 7, 17 |
| 1 Search and results (debounced suggestions, grouped results, clearable local history) | 4, 6, 16 |
| 1 Library: Songs, Albums, Artists, Playlists; likes in the database; create, rename, reorder playlists; play, shuffle, add to queue from anywhere | 4, 18, 20 (queue actions: 16, 17, 18, 19) |
| 1 Album, Artist, Playlist pages | 6, 19 |
| 1 Now Playing, two skins on one shared state; play/pause, seek, next/previous, repeat, shuffle, like, sleep timer, queue; lyrics slot empty | 14 (Material), 23 (Apple) |
| 1 Queue | 15 |
| 1 Settings (history threshold, endless radio, skip silence, loudness, sleep defaults) persisted in DataStore | 1, 2, 5, 9, 21 |
| 1 First-run onboarding including notification permission | 12 (rationale at first play), 22 |
| 1 Out of scope items (downloads, lyrics, Spotify, Auto, widget, Cast, stats, EQ, Discord, recognition) | not built; lyrics slot only (14) |
| 2.1-2.3 Decisions: usable player; data layer first; Navigation Compose with type-safe routes, per-screen Hilt ViewModels, screens as packages in `:app` | 0 (versions), 1-9 (data first), 12 (routes), every screen task (ViewModels) |
| 3 Phase A: domain contracts, `:data` DAOs and repositories with Room tests, `:innertube` adapters with fixture tests, per-shelf failure, fakes for every contract, `ModuleGraphRules` unchanged | 1, 3, 4, 6, 7 |
| 3 Phase B: shell, mini player above the bar, `PlayerController` as the only playback entry | 8, 12, 13 |
| 3 Conventions: one ViewModel and one `StateFlow<UiState>` per screen; loading/empty/error/offline everywhere; glass only through `GlassSurface`; playback errors as non-blocking messages | Global Constraints (conventions), 12 (states, message host), every screen task, 13 and 23 (GlassSurface) |
| 4 Images over https via `StreamUrlPolicy`, invalid artwork dropped | 11 |
| 4 No analytics, no crash services; history, likes, playlists local; Compose escapes text; no new WebViews | Global Constraints, 4, 24 (docs) |
| 4 Content descriptions, 48dp targets, font scaling, TalkBack labels, checked in Compose UI tests | 12-23 (each UI test), 24 (matrix, manual TalkBack) |
| 5 Testing: ViewModel tests on fakes, repository tests on Room, parser fixture tests, Compose UI tests in API 26/34 emulator jobs | 1 (fakes), 4, 6, 7, 8-23, 12 (`app-ui` job) |
| 5 CI iteration policy (max 4 rounds, never weaken) | Global Constraints |
| 6 Risks (parsing churn, scope, no real-device confirmation) | Risks section, 6, cut order, 24 |
| 7 Definition of done: CI green on API 26/34, manual checklist, debug APK workflow, docs and module graph, whole-branch review, no public release | 24 |
| 8 Open items (name, package ID, verification metadata, Dependabot, PoToken confirmation) | not in scope; stay open in `docs/release-gates.md` (24) |
| ADR-0003 library semantics (inLibrary, like, add, plays-only, upsert, no sync, reserved tokens) | 1 (contracts), 4 (SQL and tests), 16, 18, 19 (UI use) |
| Decision 1: synthetic fixtures, capture script, stale-fixture test, release gate, adapters over `YouTube.*` | 6 (and 0 findings, 24 checklist) |
| Decision 2: library definition, upserts, no sync | 4 |
| Decision 3: Home cache table, SWR, 7-day marker, drop undecodable rows, nothing else cached, offline local sections, migration with test and regenerated schema | 3 (table, migration), 7 (logic, codec, drop), 17 (UI, marker, offline) |
| Decision 4: `PlayerController` singleton, slow state vs position flow, message channel, service-start test | 8 |
| Decision 5: `SettingsRepository` in `:domain`, DataStore in `:data`, applied by the Application, `historyThresholdMs` clamp 10-120 s, stored value reaches engine without a screen | 1, 2, 5, 9 (test), 21 |
| Decision 6: Coil 3 + network module verified, URL/host interceptor in one place with tests, 100 MB cache excluded from backup, decoded size capped, `docs/data-flow.md` IP disclosure | 0, 11, 24 |
| Decision 7: five slices and the fixed cut order, UI tests with each slice | slices marked in every task; cut order in Global Constraints and Tasks 17 (step 4), 20 (step 4), 22 (step 4), 23 |
| Decision 8: strings in `strings.xml`, CI script with pytest, plurals, locale-aware formatting, English only, en-XA layout check | 10 (script), 12 (formatters, plurals, `AriaTestTheme`, pseudolocales), every UI test, 24 (matrix) |
| Decision 9: Room Flow lists with SQL sort and filter, slim projections, stable keys, no Paging, 500-item window with message, transactional playlist reorder with test | 4 (SQL, projections, reorder test), 8 (window), 18 (keys, UI), 20 (reorder UI) |

## Risks

- **No dependency version is verified.** The sandbox cannot reach Maven. Task 0 treats every new coordinate as a candidate and resolves them through CI (`Could not resolve` shows in the first push). Navigation Compose, Coil 3 and DataStore APIs were written from memory; Task 0 reads their sources, CI compile is the arbiter, and affected tasks list the exact lines to change.
- **Room schema v2.** The first CI run of Task 3 is expected red on the schema-unchanged step until `2.json` is committed. `AppModule` must keep the migration registered or installed debug builds crash on open (the migration test guards the SQL, not the registration; `HiltWiringTest` opens the real database on the emulator).
- **`java-test-fixtures` consumed from Android modules** (Task 1) is unproven here; the documented fallback moves the fakes into `:domain` main (not ideal, but harmless).
- **DAO base class `TrackWriterDao`** (Task 4) relies on Room reading `@Insert` methods from a non-annotated abstract superclass; fallback duplicates the three inserts.
- **Live YouTube Music shapes are unverified.** Synthetic fixtures prove mapping logic and null-safety only. `AlbumPage.getSong` uses `!!` and one odd row can fail a whole album (shown as the error state). The release gate "Parsers verified against live responses" stays open until a maintainer runs the capture script; the freshness test only fails 60 days after a capture exists. No nightly check of browse parsers exists (parking lot).
- **Region reason (decided, stress-test S3).** The engine reports only "unavailable" and "network error" skip reasons (it is unchanged), so messages say those; unknown reasons say "could not play". Region-specific wording is a ticket for after the first real-device run (extractor error mapping needs real data). The spec text is aligned with this.
- **Compose UI tests need the emulator job** (`app-ui`, 6-9 min per API level; 25 tasks times 4 CI rounds is the worst case). `package=app.aria.music.ui` filtering of sub-packages is assumed; Task 12 lists the fallback. Pseudolocale `en-XA` resolution through `createConfigurationContext` is assumed.
- **Own icon vectors** (Task 12) use hand-typed path data; a wrong path draws a wrong glyph but cannot crash (`PathParser` errors are caught by CI tests only if parsing throws). Check visually in the manual checklist.
- **`PullToRefreshBox`** (Task 17) exists in Material3 1.3+; the alpha27 signature is unverified.
- **Temporary legacy exemption** in `scripts/ui-strings-baseline.txt` (Task 10) exists for `PlayerScreen.kt` only and is removed in Task 16; the script fails on stale entries.
- **Scope:** 25 tasks. Cut order is fixed (Apple skin, Home shelf variety, reorder, onboarding polish); each cut leaves every earlier acceptance criterion intact.

## Self-Review

**Spec conflicts and deviations (surfaced to the maintainer):**
1. Spec section 6 and decision 3 ask for a Room schema change "with Migration test", while the foundation spec and the playback plan say the schema stays at version 1 until the first beta. This plan follows the newer instruction (version 2 and `MIGRATION_1_2`) because `AppModule` forbids destructive migration and an installed debug build would otherwise crash.
2. Spec section 3 says onboarding includes the notification permission; section 3 also says it is explained "at the moment it is needed". Both are implemented: an optional request on the last onboarding page (Task 22) and the rationale dialog at first playback (Task 12).
3. Spec section 3 wants skip reasons offline, unavailable and region. The engine (unchanged by design) exposes two string reasons; "region" cannot be reported (Risks).
4. Decision 4 says `PlayerController` "wraps `PlaybackEngineProvider`"; the plan wraps the `PlaybackEngine` interface (the same engine instance Hilt provides) so it can be unit-tested with a fake; the provider interface stays the service's hook.
5. Decision 3 says "keyed by shelf type"; the remote Home feed has no stable type, so rows are keyed by slot (`yt:00`, `yt:01`, ...) and replaced as a set.
6. Library Playlists show user-created playlists only; saving a YouTube playlist is not in scope (it would need copying thousands of tracks), and `YouTube.library` is account-based, which ADR-0003 rules out.
7. The manual-dispatch debug APK remains public while the repo is public (ADR-0002); the final task repeats the warning.

**Consistency checks performed:** every type and function used by a later task is defined earlier with the same name and signature: `Track` (unchanged), `AlbumRef/ArtistRef/PlaylistRef` (Task 1) used in Tasks 4, 6, 7, 16-20; `LibraryRepository` (Task 1, implemented Task 4, faked Task 1) used with identical members in Tasks 14, 16-19; `PlaylistRepository.reorder(playlistId, orderedEntryIds)` (Tasks 1, 4, 20); `PlayerController` members (Task 8) used verbatim in Tasks 13-22; `TrackMenuAction` and `TrackRow` (Task 16, extended by `showDefaultMenu` in Task 18 and `extraActions` use in Task 20); `AppSettings` property order (Task 1) with `nowPlayingSkin` appended last (Task 23); `SettingsActions` gains `onSkin` last (Task 23, reflected in Task 24); `AriaRoot()` signature changes in Tasks 16 and 22 and the NavHost builder takes `start` after Task 22. Route classes are defined once (Task 12) and used by name (`AlbumRoute(id)`, `ArtistRoute(id)`, `PlaylistRoute(id, local)`). Compose tests use the tags defined in the owning task.

**Placeholders and known rough edges to clean while typing (none change a signature):** a few test snippets carry a "when typing" note where a fully qualified call must become an import (`first`), a couple of screen composables are specified by exact structure, tags and strings instead of full code (Tasks 14, 16-22), and Task 24's matrix lists five screens by pattern. The only forward reference in production code is the `/* Task 13 */` slot in Task 12, replaced in Task 13 and checked by Task 24's grep.


## Stress Test Results: UI shell plan

Date: 2026-11-11. Six branches interrogated; all recommendations accepted. Labels S1-S6 are referenced in the tasks above.

### Resolved Decisions
- **S1 Compile canary:** Task 0 adds a throwaway `Canary.kt` exercising every new third-party API as later tasks use it; mismatches are fixed in the plan before dependent tasks run; removed in Task 12, grepped in Task 24.
- **S2 Emulator cost:** `app-ui` runs API 34 only per task; API 26 and 34 at slice boundaries (Tasks 16, 20, 22) and on the final commit.
- **S3 Region wording:** accepted as a ticket; messages say unavailable / network error / could not play; spec text aligned.
- **S4 Image allowlist failure mode:** debug-only log of the rejected host (host only) and a placeholder test in Task 11. No other change: hostile-host cases were already covered.
- **S5 Execution process:** per-step local commits and a progress file; verify-then-resume on agent failure; pause for device feedback after Task 16.
- **S6 Attack-surface guard:** `scripts/check-manifest-surface.sh` with pytest samples in `verify`; `navDeepLink` banned (Task 24 Step 1b).

### Changes Made
Task 0 Step 3b, Task 11 Step 3b, Task 24 Step 1b, Global Constraints (CI scope, execution process), Risks (region), and the spec's error-message line.

### Deferred / Parking Lot
Region-specific error wording; API 26-only failures are found at slice boundaries by design.

### Confidence Assessment
- Overall: Medium. The plan is thorough and honest about unverified APIs, but about 25 tasks of Compose code are uncompiled; the canary and per-task CI limit the blast radius.
- Areas of concern: Material3 1.5.0-alpha27 signatures, `java-test-fixtures` from Android modules, emulator-job time.

## Controller notes during execution

- Task 1 review (2026-11-12): `BrowseItem.key` is unique per kind+id, not per list. Task 6 adapters must `distinctBy { it.key }` shelf and section items (as search results already do), and Task 17's `ShelfRow` must not rely on keys being unique without that (suffix the index if a duplicate can reach it). Minor fake clean-ups noted: `FakeSettingsRepository.update` should use `state.update`; `FakePlaylistRepository` throws on unknown ids inconsistently with the interface KDoc; `FakeHomeRepository` ignores `limit`.
- Task 0: licence allowlist gained the spelling "The 3-Clause BSD License" (DataStore's protobuf); revisit in the pre-beta allowlist review.
