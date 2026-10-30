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
| v1 scope | Everything is the target (all seven sub-projects), with staged public gates and a cut-or-slip rule (section 9.1) so v1 has a date |
| Name | **Aria** (placeholder package ID `app.aria.music`) |
| Qobuz | Third-party resolver backends (Monokenny, Jumo, Squid) are **not** included and not ported. The lossless architecture is kept behind an interface (section 5.4). |
| Spotify | Included. It uses an unofficial API with rotating hashes, so it ships with a fallback and honest wording. |
| License | GPL-3.0 |
| Distribution | GitHub Releases (primary), IzzyOnDroid and F-Droid as targets, mirror on Codeberg. Google Play is treated as unavailable (verify before relying on it either way). |
| Telemetry | None. No Firebase, no analytics, no Crashlytics. Crashes are captured in an in-app crash log with a "copy report" action for GitHub issues. |
| First public build gate | Final name, trademark check and package ID are required before the first public build, including alphas. |

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
- `:innertube` (YouTube Music): built on the maintained InnerTubeX library (`MetrolistGroup/innertubex`, version pinned in the catalog), which handles networking and client fallback. **Correction found while planning:** InnerTubeX does not replace the response parsing. Meld's `:innertube` is a compatibility layer over it that still holds about 9,700 lines (a 3,500-line `YouTube.kt` facade, 56 model files, 22 page parsers), and Echo carries about 10,100 lines of its own. So Aria starts from Meld's module, which already delegates transport to InnerTubeX, and exposes only `:domain` interfaces (`SearchSource`, `StreamSource`) to the rest of the app. It is thinner than a private fork, not thin: parsing stays ours to maintain, and shrinking it is a tracked follow-up, not a v1 promise.
- `:spotify`, `:lyrics` (LRCLib, KuGou, BetterLyrics, SimpMusic, YouLy+).

App:
- `:app`: navigation, DI wiring, screens as packages. Feature modules are split out later once screens settle.

Boundary rule: `:playback` and the UI depend only on `:domain` interfaces. They never know whether a track came from YouTube Music, Spotify matching or a local file.

## 4. Data model (`:data`, Room)

- Fresh schema at **version 1**. No legacy migrations (Echo is at v48, Meld at v41).
- `exportSchema = true`, schema JSON committed from day one, a test for every future migration.
- **Track identity:** a `TrackId` value class in `:domain` (source plus value) is the only way code builds or reads track IDs. Stored encoding is a single string primary key: **bare for YouTube** (compatible with Meld and Echo data, which keeps a future importer cheap) and **prefixed for every other source** (`local:`, `pod:`, and so on). No composite keys, because they would complicate every Room junction table. Meld and Echo both rely on an implicit "not local means YouTube" convention; Aria makes it explicit and contained in this one type, so a new source never needs code elsewhere to guess.
- **Spotify tracks are not songs.** They are never stored as library songs. A matched Spotify track resolves to a YouTube `TrackId` through `track_match`; an unmatched one appears in Spotify lists as unplayable with a clear state (sub-project 5 defines the exact behavior).
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
- **Build:** Gradle version catalog on Meld's newer versions (Kotlin 2.4.10, Compose 1.12.0, Media3 1.10.1, Room 2.8.x, Ktor 3.x, Coil 3.x), explicit per-module build files and a `buildSrc` module for the module-graph rule; shared convention plugins come after the foundation builds green under AGP 9. `compileSdk 37`, `targetSdk 36`, `minSdk 26`.
- **Flavors:** `foss` (the recommended download) and `gms` ("foss plus Google Cast", the only thing the `gms` flavor adds); ABI splits arm64 and universal. No Firebase, no analytics, no crash-reporting service in either flavor. No ads. Updates are notify-only. Echo's Play Services Location dependency is not carried over unless sub-project 6 shows a real need.
- **Takedown hygiene:** no Google, YouTube, Spotify, Apple, Qobuz or Tidal names or logos in the app name, icon or listing; a non-affiliation notice in the README and About screen; repo on an account you control with 2FA; mirror on Codeberg; signing key backed up offline.
- **CI (GitHub Actions):** build both flavors, lint, unit tests, Room schema check, dependency-license scan. Signing keys in secrets. Aria hosts **its own signed registry** (section 7) and never depends on Meld's.
- **Nightly playback smoke test (required, replaces crash analytics as the early-warning system):** resolves a stream for a few fixed public tracks and opens an issue automatically on failure. GitHub's datacenter IPs may be blocked by YouTube; if so, the same test runs on a schedule from the maintainer's machine instead.
- **Registry-delivered YouTube client config:** the signed registry may carry allowlisted client configuration values (client names and versions) so player-config changes ship without an app release. It carries data only, never executable code.
- **Testing:** pure-Kotlin logic in `:domain` gets unit tests; Room migrations get tests; player and glass fallback get Compose UI tests; manual device matrix at API 26, 30, 31 and latest.

## 7. Security

General: cleartext traffic disabled; dependency-license scan in CI.

**Spotify trust chain** (both source apps share these weaknesses; Aria fixes them, additively):
- **Tokens:** the `sp_dc` session cookie is a full-account web session. It is stored only in Keystore-backed encrypted storage, excluded from Android backup and device transfer (`allowBackup`/data-extraction rules cover the token store), and cleared on logout. Release builds plant no verbose log tree, and nothing logs tokens or cookies.
- **Registry (hashes, TOTP secret, provider kill switches):** one signed JSON on Aria's own HTTPS host. The app validates a strict schema (64-hex hash pattern, allowlisted operation names, size cap, maximum age) and verifies an **Ed25519 signature** against embedded public keys (two, so the signing key can rotate). Anything unsigned or invalid keeps the last known-good copy. The registry signing key is separate from the APK signing key and stored only in CI secrets.
- **TOTP secret:** the app never contacts third-party gists (Meld and Echo both fetch it at runtime from an individual's public gist). Aria's CI pulls the community source, a human reviews any diff, and the result is republished inside the signed registry, with a last-known-good copy embedded in the app as fallback.
- **Login WebView:** origin allowlist (`accounts.spotify.com`, `open.spotify.com` only), no JavaScript bridge, storage cleared after the cookie is captured.
- **Disclosure:** in-app text states that Spotify login uses an unofficial method and that a session cookie is stored on the device.
- **Standing duty:** maintaining the signing key and the CI diff review is ongoing work, not a one-time task.

### 7.1 Provider policy

Every external source Aria talks to is classified before it ships. This replaces the one-off Qobuz exclusion with a rule applied to all providers.

- **Tier 1, open or community APIs meant for public use** (LRCLib, Better Lyrics, SimpMusic's public API): on by default.
- **Tier 2, unofficial use of a commercial service's endpoints for metadata or free content** (YouTube Music, Spotify metadata, KuGou lyrics, Apple and Tidal canvas art, Shazam recognition): allowed, disclosed in the app, individually switchable, and remotely disable-able through the registry. User-started features (recognition) are on. Sources that need a scraped token (Apple and Tidal art) are **off by default**. YouTube Music is on, since the app cannot work without it.
- **Tier 3, excluded:** anything whose purpose is delivering paid content without a subscription (Qobuz resolvers, paywalled streams or downloads). This is the one case where the purpose is obtaining content the rights holder charges for without paying.
- **Infrastructure rule:** no dependency on another project's private server (for example `canvas.echomusic.fun`, `artwork-archivetune.koiiverse.cloud`, or a free-tier host such as `vivi-yt-music-server.onrender.com`) unless permission is obtained or we self-host. Otherwise the feature is replaced or dropped.
- **Vetting record:** each provider gets `docs/providers/<name>.md` listing endpoint, tier, owner, kill switch, fallback, and **data sent, when it is sent, and whether it is opt-in**. A provider without a record does not ship.
- **Cost:** some Echo features may not be available at launch because they depend on servers we cannot use.

### 7.2 Privacy and data flow

Dropping Firebase removes Aria's own telemetry, but the app still sends listening data to third parties, and a public app must say so accurately.

- **Data-flow table and policy:** `docs/data-flow.md` is generated from the provider vetting records (what data leaves the device, when, to whom, opt-in or not). The privacy policy is written from scratch from that table and states plainly: no telemetry, and exactly which third parties receive what. Echo's policy is not reused (it describes Firebase analytics and IP collection that Aria does not have), and Meld has none.
- **Defaults:** automatic third-party calls are limited to what a core feature needs. Anything tied to an account (Spotify, Last.fm, ListenBrainz) is an opt-in login. Song recognition is user-started only, asks for the microphone at the moment of use, and never stores audio. Lyrics lookups send track title, artist and duration to the chosen providers, and providers can be chosen or switched off in settings.
- **Discord presence is out of v1 unless an official, supported mechanism is confirmed.** Meld implements its own Discord Gateway client and a client-fingerprint ("super properties") class, which from the file names looks like the user-token approach that Kizzy popularized. That would mean storing an account-wide Discord token and an app that can get users' accounts restricted under Discord's terms. The auth flow has not been read in detail; it is verified in sub-project 6, and the feature is revisited only if the route is official or the maintainer explicitly accepts shipping it behind a clear warning.
- **Listen Together needs an Aria-run relay:** both source apps connect to relay servers run by other people (`metroserverx.meowery.eu`, a Hugging Face Space) and fetch the server list from the `main` branch of Echo's GitHub repo, which breaks the infrastructure rule in 7.1. Aria runs its own small open-source relay with the server URL configurable in settings and the server list delivered from the signed registry. If no relay is ready by the v1 date, Listen Together moves to 1.x under cut-or-slip. The relay's cost and upkeep are a standing duty.

## 8. Licensing and credits

Repo is GPL-3.0. Headers alone cannot carry provenance: in the audit, Meld has headers on about 350 of roughly 590 Kotlin files, while Echo has them on only 28 of 836 (plus two Apache-2.0 headers), and Echo's credits say "inspiration" or "reference" without saying what was copied. So:

- **Provenance ledger:** `docs/provenance.csv` has one row per ported directory: source repo, commit SHA ported from, upstream license (SPDX), contents, and copied, adapted or inspired. CI fails if a ported directory has no ledger row.
- **Audit before porting:** the upstream license is read as part of each port. If it is incompatible or unknown, the code is not ported; it is rewritten from its behavior or replaced.
- **Headers:** original headers are kept where they exist. `SPDX-License-Identifier: GPL-3.0-only` is added to every ported file without one and to all new files. Both source apps say "GPL-3.0" without "or later", so Aria does not claim "or later".
- **Fonts:** each bundled font ships with its license text (OFL fonts require it), or is replaced by one with a verified license. Echo has none in-repo today.
- **Native code:** the vendored fingerprinting code (`vibrafp`, with a `third_party` folder and no license file found) gets a full audit before any port; if its provenance stays unclear, it is excluded.
- **Gate:** a license and provenance audit must pass before the first beta. `CREDITS.md` maps each module to its origin project (Metrolist, Meld, Echo and the lyrics and canvas projects they credit). Echo asks that code ported from other projects be credited specifically (what was taken, and whether it was copied or only inspired). Aria follows the same standard: `CREDITS.md` lists, per module, the origin project, what was taken, and whether code was copied or adapted. The README links to it.

## 9. Sub-project order

1. Foundation (this document)
2. Playback core
3. UI shell
4. Lyrics
5. Spotify
6. Extras: Listen Together (needs an Aria-run relay, 7.2), Discord presence (out of v1 unless an official mechanism is confirmed, 7.2), song recognition, equalizer and crossfade, Canvas, USB audio and DSP, widget, Android Auto, Last.fm, Chromecast (`gms` flavor only), and **backup and restore** of library and settings (added during stress-testing: with no destructive migrations and tokens excluded from Android backup, users need their own way to keep their data)
7. Release engineering

Each gets its own spec, plan and build.

### 9.1 Release gates and scope rules

1. **Staged public gates:** public **alpha** after sub-projects 2 and 3 (a usable player), **beta** after sub-project 5 (Spotify and lyrics), **v1.0** after 6 and 7. Every public build, alpha included, requires the final name, trademark check and package ID (section 2).
2. **Cut-or-slip:** any extra in sub-project 6 that does not meet its definition of done by the declared v1 date moves to 1.x. It does not delay v1. The proposed cut order, first to go: USB audio and DSP (native code), Canvas (unofficial sources and other projects' servers), Echo Brain and Music DNA (external APIs that need provider vetting), then song recognition (native fingerprinting plus an unofficial endpoint). The order is re-confirmed at the start of each sub-project. Backup and restore is **not** cuttable.
3. **Maintenance first:** from the first public build, a bug that breaks playback or Spotify login blocks all feature work until fixed.
4. **No schedule is promised yet.** A duration estimate needs the maintainer's weekly hours, and each sub-project's plan will carry its own estimate.

## 10. Definition of done for this sub-project

- Repo builds `foss` and `gms` debug variants from a clean checkout on CI.
- All modules in section 3 exist, compile, and respect the dependency rule (Gradle's module graph enforces it, and a CI check fails if `:playback` depends on a provider module or on `:data`).
- **Walking skeleton works end to end** (time-boxed, a few hundred lines): search one track through `:innertube`, resolve a stream through `StreamSource`, play it from a minimal Media3 foreground service in `:playback` with a queue and resume position, record a play event through a `:domain` repository interface implemented in `:data`, and show it on one `:app` screen using `GlassSurface`. This proves every module boundary with real code, not empty shells.
- Room v1 schema exported and committed; one migration test harness in place.
- `GlassSurface` renders on API 31+ and falls back on API 26–30, covered by UI tests.
- `CREDITS.md`, `LICENSE`, the provenance ledger with its CI check, and the SPDX header policy in place. Each module ported in the foundation (including the walking skeleton) has its ledger row and upstream license audit.
- No placeholder name leaks: the app label, package ID and brand strings come from one config location.

## 11. Open items (not blocking this sub-project)

- **Name clearance:** "Aria" is crowded (Opera's Aria, aria2, others). Check GitHub, F-Droid and the Play Store before release; renaming stays cheap because the name lives in one place.
- **Package ID:** `app.aria.music` is a placeholder; it should tie to a domain or account you control and must be final before the **first public build, including alphas**, because changing it later forces every user to reinstall.
- **InnerTubeX checks (sub-project 2):** confirm its license is GPL-compatible; confirm IzzyOnDroid and F-Droid accept a JitPack artifact (fallback: a pinned git submodule built in-tree); determine whether it covers PoToken generation or whether Echo's WebView-based generator must be ported, and treat PoToken as a named risk until answered. None of these has been verified yet.
- **Importer** for Meld and Echo backups (feasible because YouTube-keyed primary keys match): later, optional.
- **Store submissions** (IzzyOnDroid and F-Droid metadata, review requirements): sub-project 7. The channels themselves are decided (section 2).
- **Discord auth flow:** read in detail and classified in sub-project 6 (7.2).
- **Listen Together relay:** hosting, cost and who operates it are decided in sub-project 6; this is the one place Aria itself would run a server beyond the registry host.
- **Improvement backlog** (to be prioritized per sub-project): unify the two Spotify implementations (Meld's home source plus Echo's import); multi-source fallback for Spotify hashes; per-provider lyrics health and fallback chain; backup and restore; baseline profiles for startup performance; localization setup (Crowdin, as Meld uses); Android Auto parity.

## 12. Risks

- **Spotify's internal API** can change without notice and breaks periodically. Mitigation: hash registry with fallback, clear in-app wording, Spotify as an optional source.
- **YouTube Music access** relies on unofficial endpoints and is the fastest-moving code in the app (about 30 changelog lines in Meld mention YouTube or playback fixes). Mitigation: `:innertube` built on the maintained InnerTubeX library behind `:domain` interfaces, a pinned version, the nightly smoke test, and registry-delivered client config. Residual risk: dependence on another team's responsiveness, plus roughly 9,700 lines of response-parsing code (inherited from Meld) that InnerTubeX does not cover and that Aria maintains.
- **MusicService decomposition (main risk of sub-project 2):** Echo is less modular than its 22 modules suggest. Its `:playback` module is about 3,000 lines, while the real engine, `MusicService.kt` (5,594 lines, 219 imports), plus the player connection and download service live in `:app`, and `:playback` depends on `:core`, which bundles Room entities and re-exports `:innertube` and `:domain`. Aria's rule that `:playback` depends only on `:domain` interfaces therefore requires decomposing that service, not just moving files. The walking skeleton (section 10) proves the interfaces first.
- **Scope:** v1 includes everything as a target. The originals are team efforts (Meld's changelog alone credits 41 contributors; Echo documents a weekly release routine). Mitigation: the release gates and cut-or-slip rule in section 9.1 and a per-sub-project definition of done.
- **Native code:** Echo carries C++ (a fingerprinting library in `:app` and the USB audio driver, NDK 28, four ABIs); Meld has none. Native code adds build complexity, per-ABI testing and a memory-safety surface. It is why USB audio and DSP and recognition sit early in the cut order.

## Stress Test Results: Aria foundation design

Date: 2026-10-29. Nine branches interrogated (seven mapped up front, two added by the one-time self-review). All nine recommendations were accepted.

### Resolved Decisions
1. **Foundation proof:** a time-boxed walking skeleton is part of the definition of done, and decomposing Echo's 5.6k-line `MusicService` is named as the main risk of sub-project 2. Echo is less modular than its module count suggests.
2. **Track identity:** a `TrackId` value class in `:domain`; bare string for YouTube, prefixed for every other source; Spotify tracks are never library songs.
3. **Source policy:** three tiers plus a "no other project's private server" rule and a vetting record per provider (section 7.1). The Qobuz exclusion is now one case of a general rule.
4. **Distribution:** GitHub Releases with IzzyOnDroid and F-Droid as targets, Play treated as unavailable; no Firebase; `gms` means "foss plus Cast"; takedown hygiene; the first public build (alphas included) requires the final name and package ID.
5. **Spotify security:** encrypted tokens excluded from backup; a validated, Ed25519-signed registry carrying hashes, the TOTP secret and kill switches; no third-party gist fetches; a locked-down login WebView.
6. **YouTube upkeep:** `:innertube` built on InnerTubeX (starting from Meld's module) instead of Echo's private fork, a nightly playback smoke test, and data-only client config in the registry. *Corrected during planning:* the layer is thinner than a fork but not thin, because parsing stays in-tree (section 3).
7. **Scope:** staged public gates, cut-or-slip with a proposed cut order, and a maintenance-first rule.
8. **Provenance (added by self-review):** a provenance ledger with a CI check, audit-before-port, `GPL-3.0-only` SPDX headers, font license texts, and a license audit gate before beta.
9. **Privacy (added by self-review):** a data-flow table and from-scratch policy, opt-in accounts, Discord presence out of v1 unless an official mechanism exists, and an Aria-run relay for Listen Together or deferral to 1.x.

### Changes Made
- Sections 2, 3, 4, 6, 7 (new 7.1 and 7.2), 8, 9 (new 9.1), 10, 11 and 12 were edited, and this results section was added.
- Added to scope: backup and restore (sub-project 6, not cuttable) and Chromecast (`gms` only).
- Removed from the plan: Firebase, a vendored `innertube` fork, runtime fetches from third-party gists, the Qobuz resolvers, and Discord presence for v1 pending verification.

### Deferred / Parking Lot
- Verify in sub-project 2: InnerTubeX license, whether IzzyOnDroid and F-Droid accept a JitPack artifact, and whether PoToken generation is covered.
- Verify in sub-project 6: the Discord auth flow; Listen Together relay hosting and cost; `vibrafp` provenance.
- Verify before the first public build: "Aria" name clearance and trademark check; Google Play policy position (assumed unavailable, not confirmed).
- Verify per font: license of each bundled font (including the two Google Sans variants).
- Needed from the maintainer: weekly hours, so each sub-project plan can carry an honest estimate.
- Not yet decided: the improvement backlog (section 11), the importer from Meld and Echo, and the exact cut order per sub-project.

### Confidence Assessment
- Overall: **Medium.** The structure is sound, and the changes above close the gaps I could find.
- Areas of concern: dependence on unofficial services that can break or be withdrawn (YouTube, Spotify, several lyrics and art sources); a standing upkeep burden for one maintainer (signing key, registry diff review, nightly smoke test, relay); several items above that are claims I have not been able to verify from here.
