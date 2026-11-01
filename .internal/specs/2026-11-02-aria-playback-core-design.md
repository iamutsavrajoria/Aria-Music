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
| `:app` | Hilt wiring, engine singleton, rewritten skeleton screen, debug-APK artifact workflow (no public release). |

Reason for the split: Echo's `MusicService.kt` (5,594 lines, 219 imports) mixes queue logic, resolution, history and Android service code. Behaviour is taken from it by reading, not by copying the file, so the engine is testable on the JVM and the module rules hold.

**Ownership (stress-test branch 1).** The engine owns the queue's order and content; the player owns position and play/pause. The engine mirrors the whole queue into ExoPlayer as lightweight `MediaItem`s (URI `aria://track/<id>`, no stream resolved yet), so the timeline, notification and Bluetooth next/previous buttons and gapless transitions work natively. Engine-side edits (add, move, remove, shuffle) are pushed to the player as playlist edits; player events (transition, error, ended) flow back to the engine, which updates state and history. When the service restarts after an idle stop, it re-attaches and the engine rebuilds the player's playlist and seeks to the saved position.

**Engine and service wiring.** The engine is a process-local Hilt singleton and the single source of UI state. The service attaches its ExoPlayer to the engine as `PlayerOutput` when it starts; commands issued before then wait for the attach. This replaces the skeleton's `PlaybackHost` and 500 ms delay (both marked `TODO(sub-project 2)`). The service finds the engine through a `PlaybackEngineProvider` interface declared in `:playback` and implemented by the `Application` in `:app` (no Hilt in `:playback`, no global object like the skeleton's `PlaybackHost`). Notification, session and headset commands route to the same engine through the session. Stream URLs are resolved at load time with ExoPlayer's `ResolvingDataSource` so expired URLs refresh rather than fail. The session accepts only trusted controllers (the system, Aria itself) using Media3's trusted-controller check; Android Auto is added in a later sub-project.

## 3. Engine behaviour (pure Kotlin, JVM-tested)

- **Queue and navigation.** List plus current index. Shuffle keeps a separate permutation: turning it off restores order, turning it on keeps the current track in place. Previous restarts the track if more than 3 s were played. Repeat: off, all, one.
- **Stream URLs.** Resolve the current track's URL; resolve the next track's URL early. An expired URL (`expiresAtEpochMs`) is re-resolved before use. An HTTP 403 mid-play triggers one fresh resolve and retry.
- **Errors (stress-test branch 6).** Failures are classified. *Permanent* (`StreamUnavailableException`, extractor "video unavailable"): skip immediately without retry, with the reason in `PlaybackEvent.Skipped`; it counts toward the stop rule. *Stale URL* (HTTP 403/410 or an expired URL): one fresh resolve, preferring a different client; if that also fails it is treated as transient. *Transient* (IO errors, timeouts, no connectivity): up to 3 retries with backoff 1 s, 2 s, 4 s (Echo retries 3 times at 1 s). When the device is offline the engine does not skip: it pauses in a `WaitingForNetwork` state and resumes when connectivity returns (network callback), so a tunnel cannot consume the queue. Only permanent failures and retries exhausted on a connected device count toward the stop rule: after 3 consecutive skipped tracks the engine stops in an error state, so a systematic failure cannot skip through the whole queue.
- **History (stress-test branch 7).** A play is recorded once per track, at the 30 s mark of actual listening while the track is still playing (not on exit), through `PlayEventRepository`, so a later process kill does not lose it. Echo makes the threshold a user setting; Aria starts with a fixed 30 s.
- **Resume and persistence (branch 7).** The queue structure (items and order) is saved through `QueueStore` only when it changes, in one transaction; the current index and position are saved separately on pause, on track change and every 15 s while playing, so process death loses at most 15 s of position. On start the queue is restored, paused at the saved position.
- **Endless radio (branch 7).** When fewer than 5 tracks remain and repeat is off, ask `RadioSource` for related songs from the current track and append a batch of 25. The queue is capped at 500 items; at the cap, already-played tracks are dropped from the front. A failed fetch is silent and retried at the next track.
- **Sleep timer.** A duration or "end of current track"; brief fade-out then pause. It survives screen-off, not an app kill.
- **Skip silence and loudness (branch 7).** Passed to the output as settings. Skip silence uses ExoPlayer's built-in processor. Loudness normalization only attenuates: tracks louder than the reference are brought down using the loudness value in the stream response, quieter tracks are left alone, so it can never clip; no correction when the value is missing.

## 4. Stream resolution and providers

Follows the foundation's provider policy (Tier 2: unofficial, disclosed, switchable, covered by the registry kill switch once the app fetches a registry).

- **Extractor adapter.** `YouTubeStreamSource` becomes an adapter over InnerTubeX's `InnerTubeExtractor` and cipher service: best audio format, loudness mapping, and a short per-video memory of failed clients so a retry uses a different one.
- **PoToken generator.** Port the WebView BotGuard generator from Meld (`PoTokenGenerator`, `PoTokenWebView` 494 lines, `JavaScriptUtil`, result and exception classes), wired in as the extractor's `TokenProvider`. Echo has the same lineage; each ported file gets a ledger row with its origin commit and an audited license.
- **WebView confinement (stress-test branch 3, security).** The generator is a dedicated WebView that is never attached to a window or shown. File and content access are off, geolocation is off, storage is limited to what the challenge needs, and navigation is limited to the one origin and challenge host (everything else is cancelled). The JavaScript bridge exposes one narrow method that receives only the token string, with no app objects, and the result is validated for length and charset before use. The WebView is destroyed after each batch and shares no cookies with any signed-in session. A static CI check (`scripts/check-webview-safety.sh`, with a pytest) fails the build if `addJavascriptInterface` or `setAllowFileAccess(true)` appears outside the generator's allowlisted file. `docs/data-flow.md` states that this sends visitor data and a challenge request to Google and involves no Aria server.
- **Diagnostics.** A trimmed `Fix403`-style logger, redacted like the crash log, so a pasted log never contains stream URLs, tokens or IPs.
- **Cipher player-config hosting and trust (provider-policy finding; stress-test branch 4, security).** Meld's extractor downloads its player configs at runtime from `raw.githubusercontent.com/ZemerTeam/zemer-cipher/.../player_configs.json`, another project's repository. That conflicts with the foundation rule of no dependency on other projects' servers. Decision (maintainer-approved): ship a bundled default config as an app asset, and fetch updates only from a copy hosted by Aria. Because whoever can write that file would steer how the extractor interprets YouTube's player, **updates must be signed**: Ed25519 with the registry's verification design (signing context `aria-cipher-config-v1`, size cap, max age, future-skew rejection, anti-rollback), strict schema (unknown fields rejected), regex values length-capped and guarded against catastrophic backtracking, and last-known-good fallback to the bundled copy. **Until production keys exist (a standing release gate) the update path is disabled and only the bundled default is used**, so the alpha APK is safe by default. The cost, recorded in `docs/release-gates.md`: fast cipher hot-fixes between app releases need the production keys first. The nightly smoke test flags when the bundled config needs refreshing. Documented in `docs/providers/youtube-music.md`.
- **Radio source.** `YouTubeRadioSource` uses YouTube's "next" feed (the same InnerTube call both apps use); recorded in the provider record and the data-flow document.
- **No Return YouTube Dislike or other third-party endpoint.** Any new endpoint needs its own provider record first.

## 5. Persistence and privacy

- `QueueStore` is a Room table in `aria.db`, which is already excluded from backup and device transfer. Nothing new is sent anywhere: the radio call and stream resolution talk to YouTube only, as already documented.
- The crash log and diagnostics logger redact URL queries, cookies and auth headers by default (foundation review fix).

## 6. Testing

- **Engine (JVM):** fakes for `PlayerOutput`, `StreamSource`, `QueueStore`, `RadioSource`, `PlayEventRepository`, with virtual time. Cases: queue operations, shuffle round trip, repeat modes, previous-restart rule, URL expiry, 403 retry, error classification (permanent, stale URL, transient with backoff), offline wait and resume, 3-consecutive-skip stop, history written once at 30 s, resume round trip with the 15 s position saves, radio top-up with batch size and the 500-item cap, silent radio failure, sleep timer modes, loudness attenuate-only.
- **`QueueStore`:** Robolectric on SDK 35 (the `:data` setup).
- **Extractor adapter:** mapping tests against fake extractor results (format choice, loudness, failed-client memory).
- **Service (emulator, in the existing CI emulator job; stress-test branch 2):** lives in `:playback`'s `androidTest` with a custom `AndroidJUnitRunner` that swaps in a test `Application` implementing `PlaybackEngineProvider` with the real `DefaultPlaybackEngine` and fake source, store and radio. `:app` has a tiny androidTest only for the Hilt wiring (app starts, engine resolves). The service test uses a bundled local audio file served through a fake `StreamSource`; asserts position advances, the notification is posted, pause and next work from the session, the untrusted-controller check rejects an unknown package.
- **Real YouTube:** the nightly smoke test is extended to resolve a few tracks through the real extractor (single open issue on failure). PoToken itself cannot be tested reliably in CI.
- **Manual device checklist** in `docs/`: play a search result with the screen off for five minutes, unplug headphones, receive a call, force-stop and relaunch (resume).

## 7. Definition of done

1. All CI jobs green on `main` (verify, glass-ui matrix, plus the service emulator test).
2. A workflow builds the debug APKs (foss and gms) and uploads them as GitHub Actions artifacts (30 days, named with the commit SHA; visible only to people with repo access) so the maintainer can install them. It does **not** publish a GitHub release: the repo is public, and the foundation gates require the final name, trademark check, package ID and (for a build containing unofficial YouTube paths) a kill switch before any public build. Public pre-releases stay blocked behind `docs/release-gates.md`. (Stress-test branch 5.)
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

## 10. Stress Test Results

Run 2026-11-02, Mode A (edits applied inline). Seven branches, all agreed, all changed the spec. The mandatory security-and-risk branch is covered by branches 3, 4 and 5.

| # | Branch | Finding | Change |
|---|--------|---------|--------|
| 1 | Source of truth | Feeding ExoPlayer only current and next would hide notification and Bluetooth next/previous and risk state drift | Engine owns order and content; player owns position and play state; full queue mirrored into ExoPlayer as lightweight items |
| 2 | Service wiring | `:playback` has no Hilt, so "attach to the singleton" had no mechanism and the service test could not use fakes | `PlaybackEngineProvider` implemented by the Application; `:playback` androidTest with a test Application; tiny Hilt test in `:app` |
| 3 | WebView security | Remote BotGuard script in an unconfined WebView is an attack surface | Hidden, origin-restricted WebView, one narrow validated bridge, no file access, destroyed after use, static CI check, data-flow note |
| 4 | Cipher config trust | Repo-hosted config is writable by anyone with repo or token access and steers the extractor | Ed25519-signed, strictly validated updates; disabled until production keys exist; bundled default |
| 5 | Debug APK vs name gate | A public pre-release would break the foundation's own release gate | Debug APKs as private Actions artifacts only; public pre-releases stay blocked |
| 6 | Error handling | One retry policy for all failures wastes time on dead tracks and skips healthy ones offline | Classified errors, backoff, offline wait-and-resume |
| 7 | Limits and persistence | Unbounded radio, per-change queue rewrites, loudness boost clipping, history lost on kill | 500-item cap with batch 25, structure saved on change and position every 15 s, attenuate-only loudness, history at the 30 s mark |

Confidence: medium-high. Remaining concerns are external: PoToken and the extractor can break when YouTube changes, CI runner IPs may be blocked, and Media3 details are verified only by CI.
