# Aria — UI Shell Design (Sub-project 3)

Date: 2026-11-08. Status: approved in brainstorming, pending written-spec review.
Builds on: foundation (sub-project 1) and playback core (sub-project 2, CI-green at ab2eaab).

## 1. Goal and scope

A usable YouTube-Music-only player: the screens on top of the playback core. Together with sub-project 2 this is the "alpha" candidate from foundation spec section 9.1 (public builds still need the final name, trademark check and package ID).

**In scope:** navigation shell (Home, Search, Library tabs); persistent mini player; Home; Search and results; Library (Songs, Albums, Artists, Playlists); Album, Artist and Playlist pages; Now Playing with Material and Apple-style skins on one shared state; Queue; Settings; first-run onboarding (including notification permission); the data layer these need.

**Out of scope:** downloads and offline, lyrics (an empty slot only), Spotify, Android Auto, widget, Cast, listening statistics, equalizer and crossfade, Discord, recognition.

## 2. Decisions (from brainstorming)

1. Scope: "Usable player" (above).
2. The missing data layer is part of this sub-project and is built first.
3. Navigation Compose (type-safe routes), per-screen Hilt ViewModels, screens as packages in `:app`. No feature modules yet; revisit when screens settle. Navigation 3 was considered and rejected for CI risk (CI is the only compiler).

## 3. Architecture

### Phase A: data layer
- `:domain`: browse contracts (home shelves; album, artist and playlist pages), library contracts (likes, saved albums and artists, playlists, recently played) and UI-facing models next to `Track`. Pure Kotlin.
- `:data`: DAOs and repositories on the existing tables (schema JSON regenerated via CI when it changes), with Room tests under Robolectric.
- `:innertube`: browse adapters over InnerTubeX behind the domain contracts. Parsers are covered by fixture tests; each shelf fails independently ("section failed" state).
- Fakes for every contract, used by ViewModel tests and the UI phase.
- `ModuleGraphRules` unchanged: `:app` depends on `:domain` interfaces; screens never import `:innertube` or `:data`.

### Phase B: shell
- Navigation Compose, type-safe routes, three-tab bottom bar. Mini player above the bar expands to Now Playing.
- `PlayerController` in `:app` wraps `PlaybackEngineProvider` and exposes one `PlayerUiState`; it is the only playback entry point for screens.

### Phase C: screens
- Home: Quick picks (local history), YouTube Music shelves, Recently played. Pull to refresh; cached copy offline.
- Search: debounced suggestions, results grouped Songs/Albums/Artists/Playlists, clearable local history.
- Library: tabs for Songs, Albums, Artists, Playlists; likes in the database; create, rename, reorder playlists; play, shuffle, add to queue from anywhere.
- Now Playing: shared state, two skins; play/pause, seek, next/previous, repeat, shuffle, like, sleep timer, queue; lyrics slot empty until sub-project 4.
- Settings: history threshold, endless radio, skip silence, loudness, sleep timer defaults, persisted in DataStore (the playback core kept these in memory only).
- Onboarding: first run, notification permission explained at the moment it is needed.

### Conventions
- One ViewModel per screen exposing one `StateFlow<UiState>`.
- Every screen has loading, empty, error and offline states.
- Glass only through `GlassSurface` with its API 26–30 fallback.
- Playback errors surface as a non-blocking message with the reason (offline, unavailable; region-specific wording is deferred until the extractor reports it); engine retry/skip behaviour is unchanged.

## 4. Security, privacy, accessibility
- Remote images load only over https via the existing `StreamUrlPolicy`; invalid artwork is dropped.
- No analytics or crash services. Search history, likes and playlists stay local.
- Compose escapes all text; no new WebViews.
- Content descriptions, 48dp targets, font scaling, TalkBack labels for player controls; checked in Compose UI tests where practical.

## 5. Testing
- ViewModel unit tests on fakes; repository tests on Room (Robolectric); parser fixture tests; Compose UI tests for shell, mini player, Now Playing and glass fallback, run in the existing API 26/34 emulator jobs.
- CI iteration policy from sub-project 2 applies (max 4 CI rounds per task, never weaken tests or security checks to get green).

## 6. Risks
- YouTube Music browse parsing is unofficial and changes often: fixture tests, per-shelf failure isolation, parsers isolated in `:innertube`.
- Scope is large (data layer plus about eight screens): phased plan, each task CI-verified, cut-or-slip applies to Onboarding polish and the Apple skin before anything else.
- No real-device confirmation of playback yet (PoToken gate from sub-project 2 remains open).

## 7. Definition of done
All new tests green in CI including API 26 and 34 jobs; manual device checklist written; debug APK workflow still works; docs and module graph updated; final whole-branch review clean. No public release from this sub-project.

## 8. Open items (not blocking)
Final name, trademark check, package ID; `verification-metadata.xml`; Dependabot PRs #1 and #3; PoToken real-device confirmation.

## Stress Test Results: UI shell

Date: 2026-11-08. Nine branches interrogated (eight mapped up front, one added by the one-time self-review). All nine recommendations were accepted.

### Resolved Decisions
1. **Parser fixtures:** this sandbox cannot reach YouTube Music, so hand-written fixtures test mapping logic and null-safety only. A maintainer-run `scripts/capture-innertube-fixtures` saves real sanitised responses into `innertube/src/test/resources`; a stale-fixture CI test fails loudly. "Parsers verified against live responses" becomes a release gate in `docs/release-gates.md` and the manual checklist. Phase A is mostly adapters over the already-ported `YouTube.home/album/artist/playlist/search/library`, not new network code.
2. **Library definition:** Library Songs = songs with `inLibrary != null`. Like sets `inLibrary`; "Add to library" sets it without a like; plain plays stay in history only. Songs are upserted on first like, add, playlist-add or play. Albums and artists follow the same rule. No YouTube account sync now; `libraryAddToken`/`libraryRemoveToken` stay unused and are documented as reserved.
3. **Home cache:** parsed domain models in a small Room table keyed by shelf type with a fetched-at time; stale-while-revalidate; marked "may be outdated" after 7 days; undecodable rows are dropped and refetched. Nothing else (search, detail pages) is cached in this sub-project. Local-history sections work fully offline.
4. **PlayerController:** Hilt singleton; slow `PlayerUiState` (`distinctUntilChanged`) separate from a `positionMs` flow only the seek bar and mini-player progress collect; engine events become one-shot messages via a controller-owned channel; a test proves a command sent with no running service starts the service and sets the queue.
5. **Settings:** `SettingsRepository` interface in `:domain`, Preferences DataStore implementation in `:data`. The Application (not a screen) applies stored values to the engine at startup. `historyThresholdMs` becomes an engine setting clamped to 10–120 s, with tests, including one that a stored value reaches the engine with no Settings screen created. DataStore's version is verified against Maven before use.
6. **Images (security):** Coil 3 with its network module (names and versions verified first). An interceptor rejects URLs failing `StreamUrlPolicy` or an image-host allowlist (configured in one place, tested). Disk cache capped at 100 MB and excluded from backup; decoded size capped. `docs/data-flow.md` documents that image requests reveal the IP to Google's image CDN.
7. **Delivery order:** five vertical slices, each CI-green and usable: (1) data layer and `PlayerController`; (2) shell, mini player, Now Playing (Material skin), Queue and Search; (3) Home, Library and detail pages; (4) Settings and onboarding; (5) Apple-style skin. Fixed cut order: Apple skin, then Home shelf variety (keep Quick picks and Recently played), then playlist reordering, then onboarding polish. Security and accessibility items are never cut. UI tests land with each slice.
8. **Strings:** every user-visible string, including content descriptions, lives in `strings.xml` from the first screen; a CI script (like `check-webview-safety.sh`) fails on string literals passed to `Text(`, `contentDescription =` and similar in `app/src/main`; plurals and locale-aware formatting; English only, with a pseudolocale (`en-XA`) layout check.
9. **Scale (added by reflexion):** Library lists are Room `Flow` queries with sort and filter in SQL, slim projection classes, stable `LazyColumn` keys, no Paging library for now. Playing from a list longer than the engine's 500-item cap queues a 500-item window starting at the tapped item and tells the user. Playlist reorder is a transactional position rewrite with a test.

### Changes Made
- Decisions above are binding on the plan; sections 3, 4, 5 and 6 of this spec are amended by reference rather than rewritten.
- New dependencies to verify before planning: `androidx.datastore:datastore-preferences`, Coil 3 and its network module.

### Deferred / Parking Lot
- YouTube account login and library sync (uses the reserved token columns).
- Paging 3 if real libraries show list-query slowness.
- Caching search results and detail pages for offline.
- Translations (Crowdin) beyond English.

### Confidence Assessment
- Overall: Medium-High.
- Areas of concern: live YouTube Music response shapes cannot be verified in the sandbox (gate, decision 1); size of the sub-project (cut order, decision 7); new dependencies compiled only in CI.
