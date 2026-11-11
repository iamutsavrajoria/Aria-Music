### Task 14: Debug APK workflow, smoke extension, docs and final checks

Spec section 7 (definition of done) and section 6 (smoke, manual checklist).

**Files:**
- Create: `.github/workflows/debug-apk.yml`, `docs/manual-device-checklist.md`
- Modify: `.github/workflows/ci.yml` (foreign-host grep), `innertube/src/test/kotlin/app/aria/innertube/smoke/PlaybackSmokeTest.kt`, `docs/providers/youtube-music.md`, `docs/providers/innertubex.md`, `docs/data-flow.md`, `docs/release-gates.md`, `PRIVACY.md`

**Acceptance Criteria:**
- `debug-apk` runs **only on manual dispatch** (stress-test branch 1: the repo is public, so Actions artifacts are downloadable by anyone signed in to GitHub, not just collaborators). It builds `foss` and `gms` debug APKs, uploads them as an artifact named with the commit SHA with `retention-days: 1`, and publishes **no** release. The maintainer downloads the APK at once and deletes the run's artifact (`gh run delete <id>` or the Actions UI); Task 14's final report says so.
- The nightly smoke test still runs as exactly one test method (`tests="1"` check in `smoke.yml` stays valid) and additionally checks that the radio source returns tracks for a fixed seed and that each resolved URL is `https` with an expiry.
- CI fails if any tracked source or resource file other than docs mentions `ZemerTeam` or `raw.githubusercontent.com` (no dependency on another project's server).
- Docs match behaviour: provider record (extractor, PoToken generator, cipher config disabled and staged, radio call), data-flow rows, `PRIVACY.md`, release gates (cipher keys; PoToken gate "closes after the maintainer confirms playback on a device"; debug APKs only), and a manual device checklist (play with screen off for five minutes, unplug headphones, receive a call, force-stop and relaunch to resume, airplane mode then restore, sleep timer, shuffle and repeat, kill service from the task manager and press play, Bluetooth disconnect and reconnect, audio focus loss and return from another app, process recreation with the queue restored).
- `grep -rn "TODO(sub-project 2)\|PlaybackHost\|PlayerController" --include=*.kt --include=*.md --include=*.kts . ` (excluding `.internal/`) returns nothing.

- [ ] **Step 1: `.github/workflows/debug-apk.yml`:**

```yaml
# SPDX-License-Identifier: GPL-3.0-only
name: debug-apk
on:
  workflow_dispatch:   # manual only: artifacts on a public repo are world-downloadable (branch 1)
permissions: { contents: read }
jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4.4.0
        with: { persist-credentials: false }
      - uses: actions/setup-java@cf277c60eb25467037889841efdb72551f06f6c3 # v4.9.1
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@748248ddd2a24f49513d8f472f81c3a07d4d50e1 # v4.4.4
      - run: scripts/ci-gradle.sh :app:assembleFossDebug :app:assembleGmsDebug
      - uses: actions/upload-artifact@<SHA from Task 6> # <tag>
        with:
          name: aria-debug-${{ github.sha }}
          path: app/build/outputs/apk/**/debug/*.apk
          retention-days: 1
          if-no-files-found: error
```

This is **not** a private artifact: on a public repo anyone signed in to GitHub can download it. It is short-lived (1 day), manual-only, and deliberately not a release; delete it after downloading. Public builds stay blocked by `docs/release-gates.md`.

- [ ] **Step 2: Smoke test.** Extend the single existing test method: after the resolve loop, `val radio = YouTubeRadioSource().related(seedTrack, 5)` with `seedTrack` built from the first id (assert `isSuccess` and non-empty), and per resolved stream assert `url.startsWith("https://")` and `expiresAtEpochMs != null`. Do not add a second `@Test`.

- [ ] **Step 3: Foreign-host guard** in `ci.yml` "Static checks":

```bash
          if git grep -nE 'ZemerTeam|raw\.githubusercontent\.com' -- . ':!docs' ':!.internal' ':!*.csv' ':!*.md' ':!.github'; then echo "Dependence on another project's server found"; exit 1; fi
```

- [ ] **Step 4: Docs.** Write `docs/manual-device-checklist.md` (checklist above, each item with expected result). Update the provider record, data-flow, `PRIVACY.md` and release gates as listed. In `docs/release-gates.md` also add under "Before the first public build": `- [ ] Any APK (even a debug build) shared outside the maintainer is a public build: make the repo private first or clear every gate above. Debug-APK artifacts on this public repo are downloadable by anyone signed in to GitHub.` Also correct ADR-0002 item 3 and spec section 7 item 2 ("private Actions artifacts" becomes "short-lived manual artifacts, publicly downloadable while the repo is public"). In `docs/release-gates.md` replace the v1.0 line about PoToken with: `- [ ] PoToken generation exists (implemented in sub-project 2); closes after the maintainer confirms real playback on a device with a debug build.`

- [ ] **Step 5: Final verification.**
  1. Push; every CI job (verify, glass-ui, playback-service) green on the final commit; dispatch `debug-apk` once, confirm both flavors uploaded, hand the maintainer the run link, and remind them to delete the artifact after downloading.
  2. Run the grep from the acceptance criteria.
  3. Controller dispatches a final whole-branch review (fresh reviewer, spec + this plan as inputs), fixes findings, and re-checks CI.
  4. Report to the maintainer: which APK to install, the manual checklist, and that the PoToken gate remains open until they confirm playback.
- [ ] **Step 6: Commit** `ci,docs: debug APK artifacts, smoke extension, docs and checklist`.

---

## Self-Review

**Spec coverage** (every requirement maps to a task):

| Spec item | Task |
|---|---|
| Queue ops, shuffle, repeat, previous rule (3) | 2 |
| Stream URLs, expiry, prefetch, 403 refresh (3) | 3 |
| Error classes, backoff, offline wait, 3-skip stop (3, branch 6) | 3 |
| History once at 30 s (3, branch 7) | 4 |
| Resume, structure-on-change, 15 s position (3, branch 7) | 4, 6 |
| Endless radio, batch 25, cap 500 (3, branch 7) | 5 |
| Sleep timer, skip silence, attenuate-only loudness (3, branch 7) | 5 |
| Ownership and queue mirroring (2, branch 1) | 2, 8 |
| `PlaybackEngineProvider`, no Hilt in `:playback`, trusted controllers (2, branch 2) | 7, 8, 12 |
| `QueueStore` Room table (4) | 6 |
| Extractor adapter, loudness mapping, failed-client memory (4) | 10 |
| Radio source, no third-party endpoints (4) | 10, 14 |
| Cipher config signed and verified, staged, disabled; no bundled default (4, branch 4) | 9 |
| WebView confinement + static check (4, branch 3) | 11 |
| Diagnostics logger (4) | Not a separate task: no new logging of stream URLs, tokens or IPs is added, and `check-webview-safety`/Timber use is limited to messages without URLs. **Surfaced cut:** a `Fix403`-style diagnostics logger is deferred to when a user-facing log export exists (sub-project 3). |
| Service emulator tests (6, branch 2) | 13 |
| Real extractor smoke (6) | 14 |
| Manual device checklist (6) | 14 |
| Debug APK artifacts, no release (7, branch 5) | 14 |
| Docs, ledger rows, release gates (7) | 9, 10, 11, 14 |
| Skeleton `PlaybackHost` and TODOs gone (7) | 8, 12, 14 |
| PoToken gate stays open (7) | 14 |

**Deviations to surface to the maintainer:** (1) the "untrusted controller is rejected" check is a pure `TrustPolicy` unit test, not an emulator test, because a foreign-package controller cannot be created from an instrumentation test; (2) skip silence, loudness and endless-radio toggles are in memory only until sub-project 3 adds settings; (3) the diagnostics logger is deferred (table above); (4) no bundled cipher config: InnerTubeX v0.5.2 only reads configs from `MetrolistGroup/faraday` URLs, so the maintainer chose disabled mode (signed config staged, unused).

**Placeholder scan:** the only deliberate forward references are the five `/* Task 5 */` method bodies in Task 2 (replaced and grep-checked in Task 5) and the **(verify)** marks on third-party API names, which CI resolves. No other TBD/TODO text.

**Type consistency:** `PlayerOutput`, `PlayerListener`, `PlayerError`, `QueueStore`, `SavedQueue(items, originalOrder, shuffle, repeat, nextQueueId)`, `SavedPosition(index, positionMs)`, `ResolvedStream.loudnessDb`, `StreamSource.markRejected`, `RadioSource.related(seed, limit)`, `NetworkMonitor.online`, `DefaultPlaybackEngine(streams, store, radio, history, network, dispatcher, clock, random)` (with `attach`, `detach`, `release`, `restore`, `streamFor`) are used with identical names and signatures in Tasks 1 to 13. The engine property is `repeatMode` internally and `repeat` on `PlaybackState`.

## Stress Test Results: playback core plan

### Resolved Decisions
- Branch 1 (debug APK exposure): manual-dispatch only, 1-day retention, maintainer downloads and deletes the run's artifact.
- Branch 2 (stream URL scheme): `StreamUrlPolicy` allows https only (file only in the emulator test app).
- Branch 3 (hung resolves): 20 s bounded resolve, shared in-flight resolve per track, PoToken generator timeout and mutex.
- Branch 4 (unknowns): Task 0 research spike settles cipher-config licence, InnerTubeX and Media3 signatures before code.
- Branch 5 (broken `:app` between tasks): Task 7 split into pure policies (7) and Media3 plus minimal real `MainActivity` (8); no shim.
- Branch 6 (disclosed cuts): diagnostics logger deferred, in-memory toggles, `TrustPolicy` as pure unit test, cipher default may block on licence. All accepted.
- Branch 7 (CI as only compiler): read sources first, 4-round cap per task, never weaken controls.

### Changes Made
- Task 0 added; Task 7 split; Tasks 8 to 13 renumbered to 9 to 14 (15 tasks, 0 to 14); coverage table and references updated.
- Task 9 gains the `innertube/build.gradle.kts` `:core` dependency; manual checklist gains Bluetooth reconnect, audio focus loss and process recreation.
- Global Constraints gain the CI iteration policy.

### Deferred / Parking Lot
- Diagnostics logger and persistent settings (sub-project 3); production cipher/registry keys; `verification-metadata.xml`; Dependabot PRs #1 and #3.

### Confidence Assessment
- Overall: Medium-high. Concern: uncompiled third-party API usage (mitigated by Task 0 and the CI policy); PoToken real-device playback stays an open release gate.
