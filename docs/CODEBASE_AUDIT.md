<!-- SPDX-License-Identifier: GPL-3.0-only -->
# ARIA — CODEBASE AUDIT & DEVELOPMENT REFERENCE
### Current Implementation · Module Map · Source Files · Technical Decisions · Remaining Work

**Project:** Aria  
**Repository:** https://github.com/iamutsavrajoria/Aria-Music  
**Branch inspected:** `main` (97 commits)  
**Inspection date:** 10 November 2026  
**Package ID:** `app.aria.music` (placeholder)  
**Licence:** GPL-3.0-only  
**Document type:** Codebase-specific technical reference  
**Purpose:** Keep an accurate picture of the existing codebase while developing Aria.  
**Supersedes:** the 2 November 2026 audit, which described the walking skeleton. Playback core (sub-project 2) has since been built.

---

# 1. Current Project Status

## 1.1 What Aria currently is

Aria is an Android music application built with Kotlin. It is a fresh shell (ADR-0001) that ports selected code from Echo Music and Meld.

Its intended purpose is to combine:

- YouTube Music search and playback.
- Spotify-driven music discovery (later).
- Synchronized lyrics (later).
- Audio customization (later).
- Local library management.
- A modern Android interface.

**What exists today:** a tested playback engine, a Media3 service, YouTube Music search, stream extraction with a confined PoToken generator, queue persistence, and a minimal player screen. **It is not yet a usable music player** because the library, home, search and detail screens belong to sub-project 3, which is designed and planned but not built.

### Sub-project roadmap (foundation spec, section 9)

| # | Sub-project | State |
|---|---|---|
| 1 | Foundation | Done |
| 2 | Playback core | Done, CI-green at `ab2eaab` (final-review fixes applied; real-device playback not yet confirmed) |
| 3 | UI shell | Spec approved and stress-tested; plan **draft in progress** (see 1.3) |
| 4 | Lyrics | Not started |
| 5 | Spotify | Not started |
| 6 | Extras (Listen Together, equalizer/crossfade, Android Auto, widget, Last.fm, Cast, backup/restore, ...) | Not started |
| 7 | Release engineering | Not started |

### Component status

| Component | Status | Notes |
|---|---|---|
| Android project foundation | Implemented | Gradle, Hilt, CI |
| Multi-module architecture | Implemented | Seven included modules, graph rules enforced |
| Domain layer | Implemented (playback scope) | Track IDs, playback contracts, queue store; no library/browse contracts yet |
| Room database | Implemented, schema v1 | 25 tables, 3 views; queue tables added; v2 planned for the Home cache |
| Track-match storage | Implemented | Matching logic does not exist |
| YouTube Music search | Implemented | `YouTubeSearchSource` |
| YouTube stream resolution | Implemented | InnerTubeX extraction, client fallback, one refresh on 403/410, shared in-flight resolves |
| PoToken generation | Implemented, **unverified on a real device** | Confined WebView (ADR-0002); release gate open |
| Cipher config updates | Built, **staged and unused** | InnerTubeX v0.5.2 only reads configs from third-party hosts; extractor runs in disabled mode |
| Playback engine | Implemented | `DefaultPlaybackEngine`, pure Kotlin, JVM-tested |
| Media3 service | Implemented | `AriaPlaybackService`, `Media3PlayerOutput`, `EngineForwardingPlayer` |
| Queue | Implemented | Add, play next, remove, move, shuffle, repeat, 500-item cap, persistence |
| Endless radio | Implemented | Default ON; batch 25, cap 500, trigger below 5 remaining |
| Sleep timer, skip silence, loudness | Implemented | Settings are in memory only (no persistence yet) |
| Play history and resume | Implemented | History at 30 s, position saved every 15 s |
| Player screen | Done | Legacy `PlayerScreen.kt` removed in Task 16; the UI shell (search, mini player, Now Playing, queue) replaces it |
| Home, Library, Album/Artist/Playlist pages | Not implemented | Sub-project 3 |
| Navigation, mini player, queue screen, settings screen, onboarding | Not implemented | Sub-project 3 |
| Spotify provider and matching | Not implemented | Sub-projects 5 |
| Lyrics providers and UI | Not implemented | `LyricsProvider` interface only |
| Equalizer, DSP, crossfade | Not implemented | Sub-project 6 |
| Local music, downloads | Not implemented | Downloads/offline out of scope until later |
| Backup and restore | Not implemented | Sub-project 6 (not cuttable) |
| Android Auto | Not implemented | Sub-project 6 |
| CI and automated checks | Implemented | Three workflows, emulator jobs |

**Status rule:** a database entity, interface or design specification does not mean the corresponding user-facing feature is implemented.

## 1.2 Verification status

- The sandbox used for development cannot run Gradle. **GitHub Actions is the only compiler.** Every task is verified green on CI before the next begins.
- Playback is covered by JVM engine tests and by an emulator service test (API 26 and 34) that plays a local silent WAV. **Real YouTube playback has not been confirmed on a device.** Use `docs/manual-device-checklist.md`.
- The InnerTube browse parsers (home, album, artist, playlist) were ported from Meld but have **not** been verified against live responses in this repository.

## 1.3 Work in progress that is not committed

`.internal/plans/2026-11-08-aria-ui-shell.md` exists in the working tree (about 4,770 lines, Tasks 0–16 present) and is **untracked, unreviewed and incomplete**: it has no spec-coverage table or risks section yet, and has not been stress-tested. Treat it as a draft.

---

# 2. Actual Repository Structure

Seven Gradle modules are included in `settings.gradle.kts`: `:core`, `:domain`, `:data`, `:designsystem`, `:innertube`, `:playback`, `:app`.

```text
Aria-Music/
├── app/                      Hilt wiring, MainActivity, ui/
│   └── src/{main,androidTest}/kotlin/app/aria/music/
│       AriaApp.kt, MainActivity.kt,
│       ConnectivityNetworkMonitor.kt, di/AppModule.kt
│       androidTest: HiltTestRunner.kt, HiltWiringTest.kt
├── core/                     crash formatting, signed registry, cipher-config verifier
│   └── crash/, registry/, cipher/ (CipherConfigVerifier, RegexGuard)
├── data/                     Room database
│   └── AriaDatabase, Converters, PlayEventDao(+RepositoryImpl),
│       QueueDao, QueueStoreImpl, TrackMatchDao, entities/ (37 files)
│       schemas/ (v1 JSON), expected-schema.txt
├── designsystem/             GlassSurface, GlassEffect, GlassMode, Tokens, backdrop/
├── domain/                   Playback.kt, QueueStore.kt, Sources.kt, TrackId.kt
├── innertube/                YouTube Music client and adapters
│   └── adapter/, cipher/, extraction/, potoken/, pages/, models/, utils/
├── playback/                 engine and Media3 service
│   └── DefaultPlaybackEngine, QueueModel, AriaPlaybackService,
│       Media3PlayerOutput, EngineForwardingPlayer, PlaybackEngineProvider,
│       TrustPolicy, StreamUrlPolicy, PlayerErrorMapper
│       androidTest: PlaybackServiceTest, TestAriaApp, ServiceFakes, SilentWav
├── buildSrc/                 ModuleGraphRules
├── config/, gradle/, licenses/
├── scripts/                  CI checks and their pytest files
├── docs/                     decisions, providers, spikes, release gates, checklist
├── .github/workflows/        ci.yml, debug-apk.yml, smoke.yml
└── .internal/                specs, plans (gitignored working records: sdd/)
```

This is the actual high-level structure, not a proposed replacement.

---

# 3. Module-by-Module Technical Reference

## 3.1 `:app`

**Purpose:** entry point, dependency injection, UI.

| File | Responsibility |
|---|---|
| `AriaApp.kt` | Application; Hilt; implements `PlaybackEngineProvider`; creates the engine and the service starter |
| `MainActivity.kt` | Hosts `AriaRoot` (the UI shell) |
| `ConnectivityNetworkMonitor.kt` | Implements `NetworkMonitor` |
| `di/AppModule.kt` | Hilt bindings: database, sources, stores, engine inputs |

**Removed since the previous audit:** `PlaybackHost`, the 500 ms service-start delay, the manually created `PlayerController`, `SkeletonScreen`, `SharedPrefsResumeStore`. Do not reintroduce them.

## 3.2 `:domain`

Pure Kotlin. Files: `Sources.kt`, `TrackId.kt`, `Playback.kt`, `QueueStore.kt`.

- `Track(id: TrackId, title, artists: List<String>, durationMs: Long?, thumbnailUrl: String?)`.
- `TrackSource`: YouTube (bare 11-char video ID), Local (`local:`), Podcast (`pod:`). Spotify is not a `TrackSource`: Spotify tracks are never library songs (foundation spec).
- Contracts: `SearchSource`, `StreamSource` (with `markRejected`), `RadioSource`, `NetworkMonitor`, `PlayEventRepository`, `LyricsProvider`, `PlaybackEngine`, `PlayerListener`, `QueueStore`.
- Models: `PlaybackState`, `PlaybackStatus`, `QueueItem`, `RepeatMode`, `SleepTimer`, `PlaybackEvent`, `PlayerError`, `ResolvedStream` (with optional `loudnessDb`).

**Rule:** no provider response models and no Android classes here. **Missing (planned for sub-project 3):** browse contracts (home, album, artist, playlist), library contracts, `SettingsRepository`.

## 3.3 `:data`

Database `aria.db`, **schema version 1**, **25 tables** (21 ported from Echo, `podcast` from Meld, plus Aria's `track_match`, `queue_item`, `queue_state`) and **3 views**.

| File | Responsibility |
|---|---|
| `AriaDatabase.kt` | Registration; no destructive migration (script-enforced) |
| `PlayEventDao.kt`, `PlayEventRepositoryImpl.kt` | Play events and counts |
| `QueueDao.kt`, `QueueStoreImpl.kt` | Queue persistence (implements `QueueStore`) |
| `TrackMatchDao.kt` | Provider-to-provider matches; automatic never overwrites manual |

Tests: migration harness, schema content, play events, queue store, track match.

**Library semantics (ADR-0003):** Library Songs are songs with `inLibrary != null`; likes and library are local only; `libraryAddToken` and `libraryRemoveToken` stay unused.

**What is missing:** DAOs and repositories for songs, albums, artists, playlists, likes, search history, home cache, and settings. The tables exist; nothing reads or writes them yet. The Room schema JSON must be regenerated from CI whenever the schema changes (see 5.2).

## 3.4 `:core`

`crash/CrashReportFormatter`; `registry/` (`Registry`, `RegistryRepository`, `RegistryVerifier`, Ed25519 signed configuration with anti-rollback and kill-switch data); `cipher/` (`CipherConfigVerifier`, `RegexGuard`).

**Limitation:** the app does not fetch a registry. No production signing keys exist. The kill switch is therefore **not operable** (a release gate). Do not add a second verification system.

## 3.5 `:designsystem`

`GlassEffect`, `GlassMode`, `GlassSurface`, `Tokens`, `backdrop/` (adapted from Echo's vendored Kyant0 code). Tested by `GlassResolverTest` (JVM) and `GlassSurfaceTest` (emulator, API 26 and 34). Glass needs API 31+; API 26–30 uses a fallback. No screens, navigation, player controls or settings components exist yet.

## 3.6 `:innertube`

Built on InnerTubeX `v0.5.2` (`com.github.MetrolistGroup.innertubex:innertubex`) plus Aria's ported InnerTube client from Meld.

| Package | Contents |
|---|---|
| root | `InnerTube.kt`, `YouTube.kt` (search, album, artist, playlist, home, explore, library, ...), `YouTubeConstants.kt` |
| `adapter/` | `YouTubeSearchSource`, `YouTubeStreamSource`, `YouTubeRadioSource`, `Mapping` |
| `extraction/` | `AudioExtractor`, `InnerTubeXAudioExtractor`, `ExtractorSession`, `FailedClientMemory`, `ExtractedAudio`, `PoTokenSource` |
| `potoken/` | `PoTokenGenerator`, `PoTokenWebView`, `WebViewPoTokenSource`, `NavigationPolicy`, `PoTokenValidator`, `JavaScriptUtil`, result and exception types; asset `po_token.html` |
| `cipher/` | `AriaPlayerConfigRepository`, `CipherConfigStore`, `FileCipherConfigStore`, `CipherConfigUpdater` (staged, unused) |
| `pages/`, `models/`, `utils/` | Response pages and models ported from Meld |

**PoToken rules (ADR-0002):** hidden WebView, origin-restricted, one validated JavaScript bridge result, no file access, `blockNetworkLoads = true`, 15 s timeout, mutex, destroyed after use. `scripts/check-webview-safety.sh` enforces this in CI. BotGuard traffic occurs per newly resolved track (see `docs/data-flow.md`).

**Dependency note:** `:innertube` depends on `:domain` and `:core` (the audit of 2 November listed only `:domain`).

## 3.7 `:playback`

The engine is pure Kotlin and owns the queue; ExoPlayer mirrors `aria://track/<id>` media items; stream URLs are resolved at load time.

| File | Responsibility |
|---|---|
| `DefaultPlaybackEngine.kt` | Queue, status, retries, history, resume, radio, sleep timer; main-thread dispatcher |
| `QueueModel.kt` | Ordering, shuffle, repeat |
| `AriaPlaybackService.kt` | `MediaSessionService`; registers the session so a notification appears with no connected controller |
| `Media3PlayerOutput.kt` | ExoPlayer adapter (`PlayerOutput`); re-prepares after an error |
| `EngineForwardingPlayer.kt` | Routes session commands to the engine; withholds commands that bypass it |
| `PlaybackEngineProvider.kt` | Implemented by the Application; `allowLocalStreams` false in production |
| `TrustPolicy.kt`, `StreamUrlPolicy.kt` | https-only stream and artwork URL policy |
| `PlayerErrorMapper.kt` | Classifies player errors; `PermanentStreamException` |

**Behaviour:** 403/410 get one refresh; transient failures back off 1, 2, 4 s; offline waits; three consecutive failures stop; play history recorded at 30 s; position saved every 15 s; radio batches of 25, cap 500, trigger below 5 remaining; sleep timer fades over 5 s; loudness is attenuate-only (minimum 0.1); a stream resolve is bounded to 20 s.

**Known limitation:** all engine settings are in memory and reset on restart. The history threshold is a constant.

Tests: eight JVM test classes (queue, errors, history/resume, extras, cap and service starter, withheld commands, error mapper, URL and trust policy) and an emulator service test.

---

# 4. Module Dependency Graph

```text
:domain     -> (none)
:core       -> :domain
:data       -> :domain, :core
:innertube  -> :domain, :core
:playback   -> :domain
:designsystem -> (none)
:app        -> all six others
```

`buildSrc/.../ModuleGraphRules.kt` also reserves rules for modules that **do not exist**: `:metadata`, `:dsp-core`, `:audio-dsp`, `:usbaudio`, `:recognition`, `:spotify`, `:lyrics`. Do not assume they exist. Update the rules when introducing a module.

---

# 5. Build, CI and Verification

## 5.1 Configuration

| Component | Value |
|---|---|
| Android Gradle Plugin | 9.3.1 |
| Gradle | 9.7.0 |
| Kotlin | 2.4.10 |
| Compose | 1.12.0 |
| Material 3 | 1.5.0-alpha27 |
| Media3 | 1.10.1 |
| Room | 2.8.4 |
| Hilt | 2.60.1 |
| Ktor | 3.5.2 |
| KSP | 2.3.11 |
| Coroutines | 1.11.0 |
| Tink | 1.15.0 |
| InnerTubeX | v0.5.2 |
| Robolectric | 4.16.1 (sdk 35) |
| JDK toolchain | 21 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 |

Flavors: `foss` (recommended) and `gms` ("foss plus Google Cast"). ABI splits: arm64-v8a and universal.

## 5.2 Workflows

| Workflow | Trigger | Content |
|---|---|---|
| `ci.yml` | push, PR | Job `verify` (static checks, full Gradle build and tests, both flavors, Room schema consistency, licence report); job `glass-ui` (emulator API 26 and 34); job `playback-service` (emulator API 26 and 34) |
| `debug-apk.yml` | manual only | Debug APK, 1-day retention. **Treat as public** because the repo is public |
| `smoke.yml` | nightly | InnerTube playback smoke test; may fail from datacenter IPs |

Static checks: `scripts/check-provenance.py`, `check-backup-rules.sh`, `check-no-destructive-migration.sh`, `check-actions-pinned.sh` (Actions must be SHA-pinned), `check-webview-safety.sh`, `port-scan.sh`, plus module-graph tests.

**Reading CI results:** `env -u GH_TOKEN gh api repos/iamutsavrajoria/Aria-Music/actions/runs?event=push&per_page=1`, then the job's check-run annotations. A Room schema change prints the schema as gzip+base64 annotations; reconstruct it and commit `data/schemas/.../N.json` and update `data/expected-schema.txt`.

## 5.3 Lessons that govern new work

- Use `@androidx.annotation.OptIn(UnstableApi::class)` (not Kotlin's) for Media3 unstable APIs in main sources.
- Tests touching Android or Media3 classes need Robolectric (`Player.Commands` does). Use `kotlin("test-junit")` for Android library modules.
- Avoid a Kotlin `var` overriding a setter in fakes (JVM signature clash): use `@JvmField`.
- A failing `runTest` that leaks a ticker hangs CI. Playback tests use the `harness()` helper that releases the engine.
- `androidx.core` is not on every module's classpath (use `Uri.parse`).
- A `MediaSessionService` needs `addSession(session)` to post a notification when no controller is connected.
- Maximum 4 CI rounds per task, then escalate. Never weaken a test or security check to get green.

---

# 6. Documentation Map

| File | Purpose |
|---|---|
| `README.md`, `CONTRIBUTING.md`, `CREDITS.md`, `PRIVACY.md` | Project, rules, attribution, privacy |
| `docs/data-flow.md` | What is sent to which provider |
| `docs/providers/youtube-music.md`, `innertubex.md` | Provider records |
| `docs/release-gates.md` | Release blockers (authoritative) |
| `docs/manual-device-checklist.md` | Real-device checks CI cannot do |
| `docs/provenance.csv` | Authoritative code-origin ledger (19 rows) |
| `docs/decisions/` | ADR-0001 fresh shell; ADR-0002 playback trust boundaries; ADR-0003 local-only library |
| `docs/spikes/` | InnerTubeX spike; playback API spike |
| `.internal/specs/` | Foundation, playback core, UI shell designs |
| `.internal/plans/` | Foundation, playback core plans; UI shell plan (draft) |

---

# 7. Accepted Architectural Decisions

- **ADR-0001:** fresh shell, not a fork. Code is ported with provenance.
- **ADR-0002:** WebView confined and statically checked; cipher configs Ed25519-signed (staged, unused); debug APKs private-by-policy, 1-day retention, no public release before gates clear.
- **ADR-0003:** the library is local-only; no YouTube account sync; token columns reserved.
- **UI shell stress test (spec appendix):** parser fixtures are synthetic plus a maintainer-run capture script; Home cache holds parsed models in Room; singleton `PlayerController` with separate position flow; DataStore settings applied by the Application at startup; Coil 3 with an image-host allowlist; five delivery slices with a fixed cut order; strings only in `strings.xml`; SQL-side sorting, no Paging.

---

# 8. Source Code Provenance and Licensing

Aria is **GPL-3.0-only**.

| Path | Origin |
|---|---|
| `:domain`, `:core`, `:playback`, `:app`, `:data` (foundation, queue) | Original Aria |
| `data/.../entities` | Adapted from Echo Music (`b04d7507…`) |
| `PodcastEntity.kt` | Adapted from Meld (`fe9a6662…`) |
| `TrackMatchEntity.kt` | Original Aria |
| `designsystem/.../backdrop`, `GlassEffect.kt` | Adapted from Echo (vendored Kyant0 backdrop) |
| `:innertube` (client, pages, models) | Ported from Meld |
| `innertube/.../extraction`, `potoken`, `po_token.html` | Ported from Meld |
| `innertube/.../cipher`, `core/.../cipher` | Original Aria |

Every ported or adapted section needs: upstream repo and commit, licence, ledger row, SPDX header, credit entry. Open item: the LuanRT/BgUtils MIT text behind the PoToken page was not read first-hand (flagged in `CREDITS.md`).

---

# 9. Release Blockers

Authoritative list: `docs/release-gates.md`. Summary:

**Before the first public build:** final name and trademark check; final package ID; account 2FA, Codeberg mirror, signing-key backup; About screen with non-affiliation notice; no APK shared outside the maintainer while the repo is public; **operable kill switch**; production cipher-config keys.

**Before the first beta:** `gradle/verification-metadata.xml`; licence and provenance audit; InnerTubeX `-only`/`-or-later` confirmation; EPL-2.0 allowlist narrowing; Kyant0 NOTICE; privacy policy matches data flow; Room schema freeze; production registry keys. The UI shell adds: parsers verified against live responses.

**Before v1.0:** Play policy position; F-Droid/IzzyOnDroid requirements; Spotify registry key; Discord presence decision; **PoToken confirmed on a real device**.

Open housekeeping: Dependabot PRs #1 and #3.

---

# 10. Recommended Next Development Order

## Priority 1 — Finish the UI shell plan and build it

1. Finish and stress-test `.internal/plans/2026-11-08-aria-ui-shell.md` (add Tasks 17+, the spec-coverage table and risks).
2. Execute slice by slice: data layer and `PlayerController`; shell, mini player, Now Playing, Queue, Search; Home, Library and detail pages; Settings and onboarding; Apple-style skin. Cut order if time runs short: Apple skin, Home shelf variety, playlist reordering, onboarding polish.

## Priority 2 — Prove real playback (maintainer action)

Build the debug APK (manual workflow), run `docs/manual-device-checklist.md` on a real phone, record the result, and close the PoToken gate or document accepted unavailable-stream behaviour. Do not treat successful search or green CI as proof of reliable playback.

## Priority 3 — Lyrics (sub-project 4)

## Priority 4 — Spotify and track matching (sub-project 5)

Reuse `TrackMatchEntity` and `TrackMatchDao`; do not create a second match table; keep Spotify out of `:domain` models and out of the playback engine.

## Priority 5 — Extras (sub-project 6)

Backup and restore is not cuttable. Cut order for the rest: USB audio and DSP, Canvas, Echo Brain and Music DNA, song recognition.

## Priority 6 — Release engineering (sub-project 7)

Clear the release gates; store submissions.

---

# 11. Coding Rules for the Current Repository

1. Inspect the repository before changing any file; read the module's build file and the relevant spec and plan.
2. Check whether a model, DAO or entity already exists before adding one.
3. Do not change the Room schema without a tested migration; never use destructive migration.
4. No provider-specific code in `:domain`; no Android UI dependencies in pure Kotlin modules.
5. Keep the playback engine independent of Spotify and YouTube Music; no second playback engine.
6. Screens talk to playback only through the planned `PlayerController`; screens never import `:innertube` or `:data`.
7. No arbitrary delays to coordinate service startup.
8. Do not weaken the WebView confinement, URL policy or signature checks for convenience.
9. Update `ModuleGraphRules.kt` when introducing a module.
10. Update provenance when porting code; update provider records, `docs/data-flow.md` and `PRIVACY.md` when external data flow changes.
11. Every user-visible string goes in `strings.xml` (enforced from sub-project 3).
12. Add tests for new behaviour; CI must be green before the next task.
13. Do not report a feature as implemented until it is tested; do not mark a plan task as done on the strength of generated code.
14. Keep documentation consistent with the source. The source wins.

---

# 12. Development Progress Tracker

| Area | Current status | Next action |
|---|---|---|
| Foundation | Done | Maintain; dependency verification metadata |
| Module structure | Done | Maintain boundaries |
| Domain models | Playback scope done | Add browse, library, settings contracts (UI shell Task 1) |
| Room database | v1, 25 tables | v2 Home cache plus repositories (UI shell Tasks 3–4) |
| Design system | Glass foundation | Reusable UI, theme, shared states |
| YouTube search | Done | Maintain |
| YouTube playback | Implemented, unverified on device | Manual device checklist |
| Playback engine | Done | Persist settings; configurable history threshold |
| Queue | Done | Queue screen |
| UI | One temporary screen | UI shell |
| Spotify | Not started | Sub-project 5 |
| Track matching | Storage only | Sub-project 5 |
| Lyrics | Interface only | Sub-project 4 |
| Local music, downloads | Not started | Later |
| Audio DSP | Not started | Sub-project 6 |
| Backup and restore | Not started | Sub-project 6 |
| Release preparation | Gates open | Sub-project 7 |

---

# 13. Source of Truth and Update Policy

This document describes the codebase as inspected on 10 November 2026. **The source code always takes priority.**

Update it when: a module is added, removed or renamed; a major class is replaced; the database schema changes; a provider is introduced; a playback or architecture decision changes; a sub-project completes; a dependency is added or removed; a release blocker is resolved.

Do not: mark planned features as implemented; keep obsolete file paths; document code that no longer exists; treat internal plans as completed implementation.

**Suggested repository location:** `docs/CODEBASE_AUDIT.md` (this file).
