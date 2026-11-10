<!-- SPDX-License-Identifier: GPL-3.0-only -->
# ARIA — MASTER DEVELOPMENT DOCUMENT
### Product Blueprint · Architecture · Feature Research · Implementation Roadmap

**Project:** Aria  
**Platform:** Android (minSdk 26)  
**Repository:** https://github.com/iamutsavrajoria/Aria-Music  
**Document purpose:** Single reference for product direction, architecture, decisions, roadmap and testing.  
**Status:** Sub-projects 1 and 2 done; sub-project 3 designed and being planned.  
**Companion:** `docs/CODEBASE_AUDIT.md` describes what the code actually contains today. Where the two disagree, the source code wins, then the audit, then this document.

---

# 1. Project Vision

Aria is a modern, premium-feeling Android music player that combines music discovery, a personal library, reliable playback and personalised recommendations.

It is not another thin YouTube Music client. It combines the strongest ideas of two open-source players (Echo Music and Meld) under its own architecture, identity and privacy rules.

### Core concept

- **YouTube Music:** search, metadata and audio resolution.
- **Spotify (later):** discovery, metadata, playlists and listening data. Metadata only: never a second audio engine.
- **Local library:** likes, saved albums and artists, playlists, history, all stored on the device.
- **One playback engine:** reliable, resumable, Media3-backed.
- **Lyrics (later):** synchronised, multiple providers.
- **Personalisation:** recommendations from local listening history.
- **Interface:** polished, responsive, with a glass design language that falls back gracefully.

### Product principles

1. Playback reliability comes before visual effects.
2. Every major feature has a clearly separated implementation.
3. The app stays responsive on less powerful devices (glass has a fallback; heavy effects are optional).
4. Users can see where their data goes: no telemetry, no analytics, no ads, no crash-reporting service; `docs/data-flow.md` is the record.
5. A failing external service never breaks the whole app (per-shelf and per-provider failure isolation).
6. Avoid unnecessary dependencies and duplicated implementations.
7. Port selected code with provenance; do not merge whole projects.
8. Build incrementally; verify each step on CI before the next.
9. Security controls are never weakened for convenience.

---

# 2. Reference Projects

Research references, not projects to merge.

| Project | Studied commit | Role in Aria |
|---|---|---|
| Echo Music (`EchoMusicApp/Echo-Music`) | `b04d7507…` | Room entities, glass/backdrop UI, player skins, lyrics providers, feature ideas |
| Meld (`FrancescoGrazioso/Meld`) | `fe9a6662…` | InnerTube client, extractor and PoToken approach, Spotify integration and matching ideas |
| SimpMusic | — | Now Playing design, lyrics presentation, statistics |
| InterTune | — | Local and online interoperability, audio processing, backup |
| Metrolist / InnerTubeX | — | InnerTube library (pinned `v0.5.2`); library and playlist ideas |
| OuterTune | — | Local media and queue ideas (maintenance mode) |

### Rules for borrowing

1. Identify the feature; find the project that does it well.
2. Read the current source (not the README) and its licence.
3. Port only what is needed and adapt it to Aria's modules.
4. Record origin, commit and licence in `docs/provenance.csv`, `CREDITS.md` and SPDX headers.
5. Test it independently.

Aria is **GPL-3.0-only** (decided). Meld, Echo, InterTune and SimpMusic are GPL-3.0 sources. Echo's and Meld's logos, banners, screenshots and Echo's seed colour are **not** reused: Aria has its own icon, wordmark and palette.

---

# 3. Decisions (finalised)

| Decision | Position | Where recorded |
|---|---|---|
| Platform | Android, minSdk 26, targetSdk 36 | Foundation spec |
| Licence / distribution | GPL-3.0-only, open source | Foundation spec, `LICENSE` |
| App identity | **Aria is a placeholder.** Final name, trademark check and package ID are release gates | `docs/release-gates.md` |
| Approach | Fresh shell, selective ports | ADR-0001 |
| Primary online source | YouTube Music via InnerTubeX plus ported extractor | Playback core spec |
| PoToken | Confined WebView generator; real-device confirmation pending | ADR-0002 |
| Spotify | Metadata and discovery only; never library songs; match to YouTube | Foundation spec |
| Library | Local only; no YouTube account sync | ADR-0003 |
| Playback engine | One pure-Kotlin engine, thin Media3 service | Playback core spec |
| Architecture | Compose, Hilt, Room, Navigation Compose, per-screen ViewModels | UI shell spec |
| Distribution channels | `foss` flavor recommended; `gms` adds only Google Cast; F-Droid/IzzyOnDroid; Play unconfirmed | Foundation spec |
| Delivery | Seven sub-projects, each with its own spec, plan and CI-verified build | Foundation spec 9 |
| Lyrics providers | To be decided in sub-project 4 | — |
| Equalizer / DSP implementation | To be decided in sub-project 6 | — |
| Discord presence | Out of v1 unless an official mechanism is confirmed | Foundation spec 7.2 |
| Listen Together | Needs an Aria-run relay; decided in sub-project 6 | Foundation spec 7.2 |

---

# 4. Architecture

Music metadata, audio resolution and playback stay separate.

```text
                         ARIA
                           |
        +------------------+------------------+
        |                  |                  |
     UI (:app)        Domain (:domain)   Data (:data, :core)
        |                  |                  |
   screens, nav,      contracts,          Room, DataStore,
   ViewModels,        models,             registry, crash
   PlayerController   playback API
        |                  |                  |
        +------------------+------------------+
                           |
                    PROVIDER LAYER
        +------------------+------------------+
        |                  |                  |
    YouTube Music        Spotify          Local music
    (:innertube)        (:spotify,         (later)
                         later)
                           |
                    SOURCE RESOLVER
                  (StreamSource, extractor)
                           |
                    PLAYBACK ENGINE
                  (:playback, Media3 service)
```

### Current modules

`:domain` (pure Kotlin), `:core`, `:data`, `:designsystem`, `:innertube`, `:playback`, `:app`. Dependency rules are enforced by `ModuleGraphRules`; reserved future modules (`:metadata`, `:dsp-core`, `:audio-dsp`, `:usbaudio`, `:recognition`, `:spotify`, `:lyrics`) do not exist yet.

## 4.1 UI layer (`:app`, sub-project 3)

Screens: onboarding, Home, Search and results, Now Playing (Material and Apple-style skins), Queue, Library (songs, albums, artists, playlists), Album, Artist and Playlist pages, Settings. Later: Lyrics, Downloads, Statistics.

Rules: screens request data through domain interfaces only; playback goes through one `PlayerController`; every screen has loading, empty, error and offline states; all text lives in `strings.xml`; images load only through one allowlisted pipeline.

## 4.2 Domain layer (`:domain`)

Music models, playback contracts, queue store, source contracts. Library, browse and settings contracts arrive in sub-project 3. No provider response models, no Android UI classes.

## 4.3 Data layer (`:data`, `:core`)

Room (`aria.db`, schema v1, 25 tables), play events, queue persistence, track-match storage, signed-registry verification. Planned: library and playlist repositories, Home cache, DataStore settings. Never destructive migration.

## 4.4 Provider layer

- **YouTube Music (`:innertube`):** search, browse, stream extraction, radio, PoToken. Built.
- **Spotify (`:spotify`, sub-project 5):** authentication, metadata, playlists, liked songs, via the Meld approach. Not started.
- **Local music (later):** scanning, metadata, artwork.
- **Extension providers:** only if a concrete need appears.

No provider controls the playback system.

---

# 5. Data Model

Track identity is a `TrackId`: a bare YouTube video ID, `local:<id>` for device files, `pod:<id>` for podcasts. A track from one source never assumes the same ID elsewhere. Spotify tracks are never stored as library songs; a matched Spotify track resolves to a YouTube `TrackId` through `track_match`.

The current `Track` is intentionally small (`id`, `title`, `artists`, `durationMs`, `thumbnailUrl`). Richer models (album, ISRC, release date) are added when the screen or matcher that needs them is built, not speculatively.

Library semantics (ADR-0003): a song is in the library when `inLibrary != null`; a like sets it; plays alone stay in history; everything is local.

---

# 6. YouTube Music Integration

### Built

Search; stream resolution with client fallback; one refresh on HTTP 403/410; shared in-flight resolves; 20 s bounded resolve; PoToken from a hidden, origin-restricted WebView; radio (related tracks); permanent-failure classification.

### Engineering realities

Streams expire; content may be region-restricted; some tracks need a PoToken; the interfaces are unofficial and change quickly (the fastest-moving code in the app); datacenter IPs may be blocked (so the nightly smoke test can fail without a code regression); search results include covers, live versions and remixes.

### Rules

Return clear errors, never invalid playback data. Never treat successful search as proof of reliable playback. Parsers are verified against live responses before release (release gate).

### Kill switch

The signed-registry design includes `killSwitches.youtube`, but the app does not yet fetch a registry, so the switch is not operable. Operable kill switch is a release gate for any public build.

---

# 7. Spotify Integration (sub-project 5)

Spotify is a metadata and discovery provider. Meld's pattern: Spotify supplies metadata; audio resolves through YouTube Music.

Required: login, search, playlists, liked songs, top items, album and artist metadata, artwork, ISRC when available, playlist import. Unmatched Spotify tracks appear as clearly unplayable in Spotify lists.

Investigate: authentication reliability, API restrictions, rate limits, token refresh, expiry, caching, privacy. Do not assume an unofficial integration lasts; a registry-signed kill switch is planned (separate signing key from the APK key). Tokens are stored encrypted and excluded from Android backup.

---

# 8. Track Matching (sub-project 5)

One of the most important components.

**Storage exists:** `TrackMatchEntity` / `TrackMatchDao` (source provider and id, target provider and id, score, manual-override flag, cached time). Automatic matches never overwrite manual overrides. **Reuse this table; do not create a Spotify-only one.**

### Priority

1. Manual override.
2. Cached match.
3. ISRC.
4. Exact then normalised title and artist.
5. Fuzzy title and artist similarity.
6. Duration (a signal, not a requirement).
7. Version classification: studio, live, acoustic, remix, instrumental, karaoke, cover, nightcore, sped-up, slowed, remaster, music video.

Do not auto-accept low-confidence candidates: show alternatives or ask the user. Manual overrides are stored, replaceable, removable and validated before playback.

---

# 9. Playback Engine (built)

One engine. `DefaultPlaybackEngine` (pure Kotlin) owns the queue; ExoPlayer mirrors `aria://track/<id>` media items; stream URLs resolve at load time. The `MediaSessionService` is thin and routes session commands to the engine.

| Behaviour | Value |
|---|---|
| Retry | 403/410: one refresh; transient: 1, 2, 4 s backoff; offline: wait; 3 consecutive failures: stop |
| History | recorded at 30 s (configurable in sub-project 3, clamped 10–120 s) |
| Resume | position saved every 15 s; queue persisted |
| Endless radio | default on; batch 25, cap 500, refill below 5 remaining |
| Queue cap | 500 items |
| Sleep timer | fade over 5 s; end-of-track option |
| Loudness | attenuate-only, minimum 0.1 |
| Skip silence | supported |

Playback states exposed to the UI: Idle, Buffering, Playing, Paused, WaitingForNetwork, Error.

Planned later: crossfade and gapless review (sub-project 6), downloads and offline caching.

---

# 10. Audio Processing and Quality (sub-project 6)

Equalizer (bands, presets), parametric EQ and AutoEQ investigation, normalisation, crossfade, tempo and pitch, USB audio, spatial audio where supported.

Do not display claimed quality. Show the actual codec, bitrate and output only when verified from the resolved stream.

---

# 11. Lyrics (sub-project 4)

Independent of the playback provider. Candidates: LRCLIB, KuGou, providers found in the reference code, user lyrics. Required: plain, line-synced and word-synced lyrics, auto-scroll, highlight, sync adjustment, translation, offline cache, provider selection. The UI shell reserves an empty lyrics slot on Now Playing. The existing `LyricsProvider` interface and `LyricsEntity` are a starting point only.

---

# 12. Library and Queue

**Library (sub-project 3):** liked songs, saved albums and artists, playlists (create, rename, reorder transactionally), recently played, history. Lists are Room queries with SQL-side sorting and slim projections. Playing a list longer than 500 queues a 500-item window from the tapped item and says so.

**Queue (built):** add, play next, remove, reorder, clear, shuffle, repeat, persistence. Queue screen in sub-project 3. Save-queue-as-playlist and multiple queues are later.

**Rule:** a playlist stores Aria's own entries with source identifiers, so local and online tracks can mix.

---

# 13. Recommendations (later)

Start simple and explainable: frequent artists, related artists and tracks, remove recently played, diversify artists, rank, improve with listening history. Sources today: YouTube Music home and radio plus local history (Quick picks, Recently played). Meld's documented queue-scoring weights are a reference, not a requirement. No complex model before reliable listening data exists. Echo Brain / Music DNA are cut candidates (external APIs).

---

# 14. Offline and Caching

Cache and downloads are different things. Cache: temporary, clearable. Downloads: user-requested, managed, with status and recovery. Both are **out of scope** until after sub-project 3; only the Home shelf cache (parsed models, stale-while-revalidate, 7-day "may be outdated" marker) is planned now. Only store media where the source's terms permit.

---

# 15. User Interface and Experience

Navigation: Home, Search, Library, with a persistent mini player that expands to Now Playing.

**Home:** Quick picks (local history), YouTube Music shelves, Recently played. Pull to refresh; cached copy offline; each shelf fails independently.

**Now Playing:** artwork, title and artist, like, transport, seek, repeat, shuffle, sleep timer, queue, lyrics slot (empty until sub-project 4), two skins (Material and Apple-style) on one shared state.

Design principles: consistent spacing and typography; restrained animation; responsive layouts; loading, empty, error and offline states everywhere; light and dark; dynamic colour where appropriate; accessible controls (content descriptions, 48dp targets, font scaling); glass only through `GlassSurface` with its API 26–30 fallback; all text in `strings.xml`.

---

# 16. Roadmap

Aria's actual roadmap is the seven sub-projects of the foundation spec, each with its own spec, plan and CI-verified build.

| # | Sub-project | Content | Status |
|---|---|---|---|
| 1 | Foundation | Repo, modules, Room schema, design system, CI, licensing, security | **Done** |
| 2 | Playback core | Engine, queue, service, extractor, PoToken, radio, sleep timer, resume | **Done** (device confirmation pending) |
| 3 | UI shell | Navigation, Home, Search, Library, detail pages, Now Playing and Queue, Settings, onboarding, data layer | **Spec approved; plan in progress** |
| 4 | Lyrics | Providers, synced and word-level lyrics, cache, UI | Not started |
| 5 | Spotify | Auth, metadata, playlists, matching, manual overrides | Not started |
| 6 | Extras | Equalizer and crossfade, Android Auto, widget, Last.fm, Cast (`gms`), Listen Together, backup and restore, others per the cut order | Not started |
| 7 | Release engineering | Gates, store submissions, signing, docs | Not started |

### Release gates by stage

- **Public alpha** (after 2 and 3): final name, trademark check, package ID, About screen with non-affiliation notice, operable kill switch.
- **Beta** (after 5): dependency verification metadata, licence and provenance audit, schema freeze, production registry keys.
- **v1.0** (after 6 and 7): store policy positions, PoToken confirmed on real devices, Spotify registry key, Discord decision.

### Cut-or-slip rule

An extra in sub-project 6 that misses its definition of done by the declared date moves to 1.x. Proposed cut order: USB audio and DSP, Canvas, Echo Brain and Music DNA, song recognition. Backup and restore is **not** cuttable. In sub-project 3 the cut order is: Apple skin, Home shelf variety, playlist reordering, onboarding polish; security and accessibility items are never cut.

### Estimate

Roughly one fifth of the whole project was complete after sub-project 2. The real constraint is the working-session usage cap, not code volume. Each sub-project carries its own estimate in its plan.

---

# 17. Testing Requirements

CI is the only compiler in the development sandbox; a task is done only when its CI run is green.

**Existing coverage:** JVM engine tests (queue, errors, history, extras, cap), queue store and DAO tests with Robolectric, migration harness, module-graph tests, cipher and registry verifiers, WebView navigation policy and validator, emulator tests for glass (API 26, 34) and the playback service (API 26, 34), nightly smoke test.

**Required for each new feature:** expected behaviour, invalid input, network failure, empty and missing data, persistence, lifecycle, compatibility with existing modules.

### Sub-project 3 additions

ViewModel tests on fakes; repository tests on Room; parser mapping tests on synthetic fixtures plus a stale-fixture check against maintainer-captured real responses; Compose UI tests for shell, mini player and Now Playing; pseudolocale layout check; a CI script rejecting hard-coded UI strings.

### Manual checks CI cannot do

`docs/manual-device-checklist.md`: screen-off playback, headphone unplug, Bluetooth disconnect and reconnect, calls, audio focus, airplane mode, force-stop resume, process recreation, notification and lock-screen controls, sleep timer.

### Track-matching tests (sub-project 5)

Exact and normalised titles, punctuation and capitalisation, featured and multiple artists, remasters, live, remix, acoustic, covers, duration differences, missing or wrong ISRC, duplicate uploads, unavailable tracks, manual overrides.

### Performance monitoring

Startup, search latency, resolve time, playback start, memory, battery, query time, frame drops.

---

# 18. Licensing, APIs and Reliability

- Aria is GPL-3.0-only. Every ported file needs upstream repo, commit, licence, ledger row, SPDX header and credit.
- Open licence items: InnerTubeX `-only`/`-or-later` variant, EPL-2.0 allowlist narrowing, Kyant0 NOTICE, font licences, LuanRT/BgUtils MIT text.
- Unofficial YouTube and Spotify interfaces can change or break. Use provider abstractions, fixtures, kill switches and per-provider failure isolation.
- No Google, YouTube, Spotify, Apple, Qobuz or Tidal names or logos in the app name, icon or listing; non-affiliation notice in the README and About screen.

### Privacy

- Store credentials encrypted; never log tokens; remove query strings from logged URLs.
- No telemetry, analytics, advertising or crash service; crash reports stay on the device, sanitised.
- Keep listening history, likes and playlists local; backup excludes the database, secure storage, resume data and crash reports until a user-controlled backup exists.
- Image requests reveal the device's IP address to Google's image CDN (documented in `docs/data-flow.md` when the image pipeline lands).
- Explain what is sent where; give users controls to clear their data.

---

# 19. Coding Rules for Aria

1. Read the existing implementation, spec and plan before changing anything.
2. Do not replace working modules without a reason; do not duplicate functionality.
3. Prefer an existing dependency; verify any new coordinate and version before using it.
4. Keep provider code in provider modules; keep UI separate from business logic; use shared domain models.
5. Handle loading, success, empty, error and offline states.
6. Use coroutines properly; never block the main thread.
7. Persist data that must survive restarts; never hard-code credentials.
8. Room schema changes need a tested migration; never destructive migration.
9. Add tests for important logic; CI green before the next task; at most four CI rounds per task, then escalate.
10. Never weaken a test or a security control to get green.
11. Update documentation, provenance and the data-flow record when behaviour or providers change.
12. Never claim a feature works until it has been tested; no placeholder implementations that look functional.
13. Prefer small, verifiable changes over large untested rewrites.

---

# 20. Standard Instructions for AI-Assisted Coding

> You are working on the existing Aria Android music application (https://github.com/iamutsavrajoria/Aria-Music).
>
> Before changing anything, read `docs/CODEBASE_AUDIT.md`, the relevant spec and plan under `.internal/`, `docs/release-gates.md`, the ADRs in `docs/decisions/`, and the actual source of the modules you will touch. Search the codebase before assuming a feature is missing.
>
> Do not recreate architecture, add a second playback engine, duplicate entities or DAOs, put provider code in `:domain`, or weaken the WebView confinement, URL policy or signature checks.
>
> The development sandbox cannot run Gradle: GitHub Actions is the only compiler. Work in small tasks, push, read the CI result, and fix the code, not the expectations.
>
> Handle loading, success, empty, error and offline states; add tests; keep strings in `strings.xml`; follow provenance rules when porting code.
>
> Report exactly what was changed, what passed, what failed and what remains. Never call a feature complete because code was generated.

---

# 21. Current Development Notes

- Repository state: 97 commits on `main`; CI green at `ab2eaab` after the playback-core fix round.
- Sub-project 3 spec approved and stress-tested (nine decisions); implementation plan is an unreviewed draft (about 4,770 lines, Tasks 0–16 so far) in the working tree.
- Not yet verified on a real device: PoToken generation and actual YouTube playback.
- Open maintenance: Dependabot PRs #1 and #3; `gradle/verification-metadata.xml`.
- The earlier reference documents (the 2 November audit and the first master document) predate the playback core; they should be treated as superseded by this pair.
- The "MediaController communication" item in the old audit is not how Aria's accepted design works: the engine is process-level, and the service forwards session commands to it. Follow the accepted playback-core spec.
- Spotify resolver ideas discussed earlier (bigram similarity, duration tolerance, explicit filtering, penalties for live and video versions) are not implemented; check the source before re-implementing.

---

# 22. Open Decisions

| Decision | Current position |
|---|---|
| Final app name, trademark, package ID | Open (release gate) |
| Lyrics providers | Decide in sub-project 4 |
| Spotify authentication approach | Decide in sub-project 5 (read Meld's flow first; classify risk) |
| Equalizer / DSP implementation | Decide in sub-project 6 |
| Listen Together relay hosting | Decide in sub-project 6 |
| Discord presence | Out of v1 unless official |
| Play Store distribution | Assumed unavailable until checked |
| Cipher config hosting and signing keys | Before first public build |
| YouTube account sync | Deferred (reserved columns) |
| Paging 3 for very large libraries | Revisit if measured slow |

---

# 23. Final Development Strategy

Build around a stable core instead of reproducing every feature of every reference project.

1. Foundation: done.
2. Playback core: done; confirm on a real device.
3. UI shell: usable player with local library.
4. Lyrics.
5. Spotify metadata and reliable Spotify-to-YouTube matching.
6. Extras in the agreed cut order; backup and restore included.
7. Release engineering.

The central architectural rule: **YouTube Music, Spotify and local music are independent sources that feed one Aria library and one playback system.** Extend the existing architecture; do not build a second one beside it.

---

# 24. Research Links

- Aria: https://github.com/iamutsavrajoria/Aria-Music
- Echo Music: https://github.com/EchoMusicApp/Echo-Music
- Meld: https://github.com/FrancescoGrazioso/Meld
- SimpMusic: https://github.com/maxrave-dev/SimpMusic
- InterTune: https://github.com/ItzSkyeYT/InterTune
- Metrolist: https://github.com/MetrolistGroup/Metrolist
- OuterTune: https://github.com/OuterTune/OuterTune
- InnerTune: https://github.com/z-huang/innertune

Documentation to revisit: Android Media3, foreground services and media sessions, Room, Compose Navigation, DataStore, Coil, Kotlin coroutines and Flow.

---

**Update rule:** update this document when an architectural decision is finalised, a sub-project completes, a dependency is added or removed, or a known limitation changes.

**Aria development principle: Build a reliable music system first. Make it powerful second. Make it beautiful throughout.**
