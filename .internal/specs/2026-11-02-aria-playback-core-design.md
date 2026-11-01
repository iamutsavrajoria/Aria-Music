# Aria — Playback Core Design (Sub-project 2)

Date: 2026-11-02. Status: draft for review. Builds on the foundation (spec `2026-10-29-aria-foundation-design.md`, implemented and CI-green on `main`).

## 1. Goal and non-goals

**Goal.** A real player that works on a phone: search results play in the background with notification and lock-screen controls, survive screen-off and restarts, and recover from stream errors. This is the playback half of the alpha gate (sub-projects 2 and 3).

**In scope (decided with the maintainer):**
- Must-haves: queue (play now, play next, add to end, remove, move, clear), shuffle and repeat, resume after restart, play-history recording, notification and lock-screen controls, audio focus and headset buttons, retry then skip on errors.
- Extras: endless radio, sleep timer, skip silence, loudness normalization.
- Stream resolution upgrade: InnerTubeX extractor and WebView PoToken generator, ported from Meld and Echo.

**Out of scope (later sub-projects):** audio cache, offline downloads and export, Android Auto and media browsing, crossfade, equalizer, playback speed and pitch, Cast, lyrics, UI beyond the rewritten skeleton screen, user settings screens (the history threshold becomes a setting in sub-project 3).

## 2. Architecture

Module rules from the foundation stay: `:playback` depends only on `:domain`; `checkModuleGraph` enforces it.

| Module | Adds |
|---|---|
| `:domain` | `PlaybackEngine` (UI-facing interface with `StateFlow<PlaybackState>`), `QueueItem`, `PlaybackState`, `RepeatMode`, `PlaybackEvent`, ports `PlayerOutput`, `QueueStore`, `RadioSource`. No Android or Media3. |
| `:playback` | `DefaultPlaybackEngine` (pure Kotlin, all behaviour) and the thin Media3 adapter `AriaPlaybackService` (ExoPlayer, media session, notification, audio focus, headset keys) implementing `PlayerOutput`. |
| `:innertube` | Extractor-based `YouTubeStreamSource`, `YouTubeRadioSource`, WebView PoToken generator under `potoken/`, cipher player-config loader, redacting diagnostics logger. |
| `:data` | `QueueStore` implementation (new Room table; the schema is not frozen until the first beta, so adding it needs no migration). |
| `:app` | Hilt wiring, engine singleton, rewritten skeleton screen, debug-APK workflow. |

Reason for the split: Echo's `MusicService.kt` (5,594 lines, 219 imports) mixes queue logic, resolution, history and Android service code. Behaviour is taken from it by reading, not by copying the file, so the engine is testable on the JVM and the module rules hold.

**Engine and service wiring.** The engine is a process-local Hilt singleton and the single source of UI state. The service attaches its ExoPlayer to the engine as `PlayerOutput` when it starts; commands issued before then wait for the attach. This replaces the skeleton's `PlaybackHost` and 500 ms delay (both marked `TODO(sub-project 2)`). Notification, session and headset commands route to the same engine through the session. Stream URLs are resolved at load time with ExoPlayer's `ResolvingDataSource` so expired URLs refresh rather than fail. The session accepts only trusted controllers (the system, Aria itself) using Media3's trusted-controller check; Android Auto is added in a later sub-project.

## 3. Engine behaviour (pure Kotlin, JVM-tested)

- **Queue and navigation.** List plus current index. Shuffle keeps a separate permutation: turning it off restores order, turning it on keeps the current track in place. Previous restarts the track if more than 3 s were played. Repeat: off, all, one.
- **Stream URLs.** Resolve the current track's URL; resolve the next track's URL early. An expired URL (`expiresAtEpochMs`) is re-resolved before use. An HTTP 403 mid-play triggers one fresh resolve and retry.
- **Errors.** Retry a failing track up to 3 times with a 1 s delay (Echo's policy). A stream that remains unavailable is skipped and a `PlaybackEvent.Skipped` is emitted. After 3 consecutive skipped tracks the engine stops in an error state, so a dead network cannot skip through the whole queue.
- **History.** A play is recorded when leaving a track after at least 30 s of actual listening, through `PlayEventRepository`. Echo makes the threshold a user setting; Aria starts with a fixed 30 s.
- **Resume.** Queue, current index and position are saved on pause, track change and app stop through `QueueStore` and restored on start, paused at the saved position.
- **Endless radio.** When the last track starts and repeat is off, ask `RadioSource` for related songs and append them. A failure is silent and retried at the next track.
- **Sleep timer.** A duration or "end of current track"; brief fade-out then pause. It survives screen-off, not an app kill.
- **Skip silence and loudness.** Passed to the output as settings. Skip silence uses ExoPlayer's built-in processor. Loudness uses the loudness value in the stream response and applies a volume correction, with no correction when the value is missing.

## 4. Stream resolution and providers

Follows the foundation's provider policy (Tier 2: unofficial, disclosed, switchable, covered by the registry kill switch once the app fetches a registry).

- **Extractor adapter.** `YouTubeStreamSource` becomes an adapter over InnerTubeX's `InnerTubeExtractor` and cipher service: best audio format, loudness mapping, and a short per-video memory of failed clients so a retry uses a different one.
- **PoToken generator.** Port the WebView BotGuard generator from Meld (`PoTokenGenerator`, `PoTokenWebView` 494 lines, `JavaScriptUtil`, result and exception classes), wired in as the extractor's `TokenProvider`. Echo has the same lineage; each ported file gets a ledger row with its origin commit and an audited license.
- **Diagnostics.** A trimmed `Fix403`-style logger, redacted like the crash log, so a pasted log never contains stream URLs, tokens or IPs.
- **Cipher player-config hosting (provider-policy finding).** Meld's extractor downloads its player configs at runtime from `raw.githubusercontent.com/ZemerTeam/zemer-cipher/.../player_configs.json`, another project's repository. That conflicts with the foundation rule of no dependency on other projects' servers. Decision (maintainer-approved): ship a bundled default config as an app asset and fetch updates from a copy in Aria's own repository; the signed registry can later point to newer versions. The nightly smoke test flags when the copy needs refreshing. Documented in `docs/providers/youtube-music.md`.
- **Radio source.** `YouTubeRadioSource` uses YouTube's "next" feed (the same InnerTube call both apps use); recorded in the provider record and the data-flow document.
- **No Return YouTube Dislike or other third-party endpoint.** Any new endpoint needs its own provider record first.

## 5. Persistence and privacy

- `QueueStore` is a Room table in `aria.db`, which is already excluded from backup and device transfer. Nothing new is sent anywhere: the radio call and stream resolution talk to YouTube only, as already documented.
- The crash log and diagnostics logger redact URL queries, cookies and auth headers by default (foundation review fix).

## 6. Testing

- **Engine (JVM):** fakes for `PlayerOutput`, `StreamSource`, `QueueStore`, `RadioSource`, `PlayEventRepository`, with virtual time. Cases: queue operations, shuffle round trip, repeat modes, previous-restart rule, URL expiry, 403 retry, 3-retry then skip, 3-consecutive-skip stop, history threshold, resume round trip, radio top-up and its silent failure, sleep timer modes.
- **`QueueStore`:** Robolectric on SDK 35 (the `:data` setup).
- **Extractor adapter:** mapping tests against fake extractor results (format choice, loudness, failed-client memory).
- **Service (emulator, in the existing CI emulator job):** a bundled local audio file served through a fake `StreamSource`; asserts position advances, the notification is posted, pause and next work from the session, the untrusted-controller check rejects an unknown package.
- **Real YouTube:** the nightly smoke test is extended to resolve a few tracks through the real extractor (single open issue on failure). PoToken itself cannot be tested reliably in CI.
- **Manual device checklist** in `docs/`: play a search result with the screen off for five minutes, unplug headphones, receive a call, force-stop and relaunch (resume).

## 7. Definition of done

1. All CI jobs green on `main` (verify, glass-ui matrix, plus the service emulator test).
2. A workflow builds a debug APK and attaches it to a GitHub pre-release so the maintainer can install it.
3. Docs updated: provider record, data-flow, ledger rows for every ported file, release gates.
4. The skeleton's `PlaybackHost` and delay are gone; no `TODO(sub-project 2)` remains.
5. The PoToken release gate closes only after the maintainer confirms real playback on a device; until then it stays open.

## 8. Risks

- PoToken (BotGuard) can break whenever YouTube changes it. Mitigation: nightly smoke, Aria-hosted config, kill switch design from the foundation.
- YouTube may block CI runner IPs, so the nightly can fail with no code change (known; the issue text says so).
- Media3 1.10 API details are verified only by CI, as in the foundation.
- The extractor library is `0.x` and experimental; pin the exact version (done: 0.5.2) and review its changelog before upgrades.

## 9. Open items (not blocking)

- InnerTubeX `-only` or `-or-later` variant, Kyant0 NOTICE, EPL-2.0 audit, name and trademark: unchanged release gates.
- Dependency verification metadata: still needs the maintainer's workflow-permission change.
- History threshold as a user setting, speed and pitch, crossfade: later sub-projects.
