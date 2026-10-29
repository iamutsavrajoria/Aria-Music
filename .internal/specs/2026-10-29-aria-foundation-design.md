# Aria — Foundation Design (Sub-project 1)

Date: 2026-10-29
Status: Draft for review
Owner: Utsav

## 1. Goal

Aria is a new Android music app for **public release**. It combines the best of two GPL-3.0 projects, both descended from Metrolist:

- **Meld** (`FrancescoGrazioso/Meld`, studied at `fe9a666227ff3d6667075e65a4330518a724524b`): Spotify as home, search and queue source, a custom recommendation engine, a Spotify→YouTube match cache with manual override, Spotify-only mode, a remote GraphQL hash registry.
- **Echo Music** (`EchoMusicApp/Echo-Music`, studied at `b04d7507b33c0671b3a23d2658b24a376add4cc7`): 22-module architecture, Liquid Glass UI, Material and Apple-style players, six or more lyrics providers with word-by-word sync and AI translation, Echo Find, Echo Brain, Canvas animations, USB audio and DSP, Music DNA, Spotify import.

This document covers **only the foundation**: repo, modules, data model, design system, build, CI, testing, security and licensing. It is the first of seven sub-projects (section 9).

## 2. Decisions made

| Decision | Choice |
|---|---|
| Audience | Public release |
| Approach | Fresh shell: new repo, ported and adapted modules, no git merge (the repos share no history) |
| v1 scope | Everything (all seven sub-projects), delivered through internal alpha milestones |
| Name | **Aria** (placeholder package ID `app.aria.music`) |
| Qobuz | Third-party resolver backends (Monokenny, Jumo, Squid) are **not** included and not ported. The lossless architecture is kept behind an interface (section 5.4). |
| Spotify | Included. It uses an unofficial API with rotating hashes, so it ships with a fallback and honest wording. |
| License | GPL-3.0 |

## 3. Module layout

Pure Kotlin (no Android):
- `:domain`: models and interfaces (track, album, queue, `StreamSource`, `LyricsProvider`).
- `:metadata`: tagging.
- `:dsp-core`: DSP maths.

Android libraries:
- `:core`: utilities, settings, networking.
- `:data`: Room database and repositories.
- `:designsystem`: theme tokens, glass, shared components.
- `:playback`: Media3 service, queue, crossfade, downloads.
- `:audio-dsp`, `:usbaudio`, `:recognition` (Echo Find).

Source modules, one per provider:
- `:innertube` (YouTube Music), `:spotify`, `:lyrics` (LRCLib, KuGou, BetterLyrics, SimpMusic, YouLy+).

App:
- `:app`: navigation, DI wiring, screens as packages. Feature modules are split out later once screens settle.

Boundary rule: `:playback` and the UI depend only on `:domain` interfaces. They never know whether a track came from YouTube Music, Spotify matching or a local file.

## 4. Data model (`:data`, Room)

- Fresh schema at **version 1**. No legacy migrations (Echo is at v48, Meld at v41).
- `exportSchema = true`, schema JSON committed from day one, a test for every future migration.
- **Shared core** (identical lineage in both apps), YouTube video IDs as primary keys: song, album, artist, playlist, junction maps, lyrics, format, play count, event, related-song map, search history, speed dial, set-video-id, recognition history.
- **From Echo:** beat info, recommendation exclusion, song play stats, taste profile.
- **From Meld:** podcast, artist page cache.
- **`track_match`** (one generic table replacing Meld's `spotify_match` and `qobuz_match`): source provider and ID, target provider and ID, score, manual-override flag, cached-at, reverse-lookup index.
- Secrets (Spotify tokens, cookies) are **never** in Room. They live in Keystore-backed encrypted storage.
- Release builds never use destructive migration fallback.

## 5. Design system (`:designsystem`)

### 5.1 Base
Material 3. Dynamic color on Android 12+, seed-color palette (via materialKolor) on older devices.

### 5.2 Glass
Port Echo's backdrop and `liquidGlass` code behind one `GlassSurface` component. Glass needs Android 12+ (`RenderEffect`); Echo's `isGlassSupported()` gate and reduced-resolution backdrop (down to 33%) come along. The fallback (same shape, translucent surface) is a first-class design. Setting: Auto / On / Off. Auto turns glass off under battery saver.

### 5.3 Tokens and players
Named tokens (surface, glass, scrim, ...) replace literals like `surfaceVariant.copy(alpha = 0.3f)`. Two player skins (Material, Apple-style) read one shared player state.

### 5.4 Source abstraction for lossless
`StreamSource` in `:domain` lets a lossless source plug in later (for example local FLAC or an official API key) without changes to `:playback`. Matching reuses `track_match` and ISRC where available.

### 5.5 Accessibility (requirement, not polish)
Contrast scrims for text over artwork, reduced-motion support, 48dp touch targets, font scaling, TalkBack labels.

### 5.6 Identity
Only code is ported. Echo's and Meld's logos, banners, screenshots and Echo's seed color (`0xFFED5564`) are not reused. Aria gets its own icon, wordmark and palette. Bundled fonts get a license check before shipping.

## 6. Build, DI, CI, testing

- **DI:** Hilt 2.60.1. Each source module exposes a Hilt module providing `:domain` interfaces.
- **Build:** Gradle version catalog on Meld's newer versions (Kotlin 2.4.10, Compose 1.12.0, Media3 1.10.1, Room 2.8.x, Ktor 3.x, Coil 3.x), convention plugins in `build-logic`. `compileSdk 37`, `targetSdk 36`, `minSdk 26`.
- **Flavors:** `foss` and `gms`; ABI splits arm64 and universal. Analytics and crash reporting are `gms`-only and opt-in. No ads. Updates are notify-only.
- **CI (GitHub Actions):** build both flavors, lint, unit tests, Room schema check, dependency-license scan. Signing keys in secrets. Aria hosts **its own** Spotify hash registry on its own GitHub Pages and never depends on Meld's.
- **Testing:** pure-Kotlin logic in `:domain` gets unit tests; Room migrations get tests; player and glass fallback get Compose UI tests; manual device matrix at API 26, 30, 31 and latest.

## 7. Security

Keystore-backed encrypted token storage; cleartext traffic disabled; no tokens in logs; Spotify login isolated in its own WebView; dependency-license scan in CI.

## 8. Licensing and credits

Repo is GPL-3.0. Every ported file keeps its original copyright header. `CREDITS.md` maps each module to its origin project (Metrolist, Meld, Echo and the lyrics and canvas projects they credit). Echo asks that code ported from other projects be credited specifically (what was taken, and whether it was copied or only inspired). Aria follows the same standard: `CREDITS.md` lists, per module, the origin project, what was taken, and whether code was copied or adapted. The README links to it.

## 9. Sub-project order

1. Foundation (this document)
2. Playback core
3. UI shell
4. Lyrics
5. Spotify
6. Extras: Listen Together, Discord presence, song recognition, equalizer and crossfade, Canvas, USB audio and DSP, widget, Android Auto, Last.fm
7. Release engineering

Each gets its own spec, plan and build. v1 ships after all seven; internal alphas land after sub-projects 2, 3 and 5.

## 10. Definition of done for this sub-project

- Repo builds `foss` and `gms` debug variants from a clean checkout on CI.
- All modules in section 3 exist, compile, and respect the dependency rule (a CI check fails if `:playback` imports a provider module).
- Room v1 schema exported and committed; one migration test harness in place.
- `GlassSurface` renders on API 31+ and falls back on API 26–30, covered by UI tests.
- `CREDITS.md`, `LICENSE` and per-file headers policy in place.
- No placeholder name leaks: the app label, package ID and brand strings come from one config location.

## 11. Open items (not blocking this sub-project)

- **Name clearance:** "Aria" is crowded (Opera's Aria, aria2, others). Check GitHub, F-Droid and the Play Store before release; renaming stays cheap because the name lives in one place.
- **Package ID:** `app.aria.music` is a placeholder; it should tie to a domain or account you control and must be final before the first public release.
- **Importer** for Meld and Echo backups (feasible because core primary keys match): later, optional.
- **Update channel** (GitHub releases, F-Droid, Play): decided in sub-project 7.
- **Improvement backlog** (to be prioritized per sub-project): unify the two Spotify implementations (Meld's home source plus Echo's import); multi-source fallback for Spotify hashes; per-provider lyrics health and fallback chain; backup and restore; baseline profiles for startup performance; localization setup (Crowdin, as Meld uses); Android Auto parity.

## 12. Risks

- **Spotify's internal API** can change without notice and breaks periodically. Mitigation: hash registry with fallback, clear in-app wording, Spotify as an optional source.
- **YouTube Music access** relies on unofficial endpoints. Mitigation: isolate in `:innertube` behind `:domain` interfaces so it can be replaced or updated alone.
- **Scope:** v1 includes everything. Mitigation: internal alpha milestones and a per-sub-project definition of done.
