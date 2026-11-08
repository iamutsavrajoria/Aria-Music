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
- Playback errors surface as a non-blocking message with the reason (offline, unavailable, region); engine retry/skip behaviour is unchanged.

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
