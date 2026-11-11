### Task 12: Documentation, provider records, data-flow and release gates

**Files:**
- Create: `docs/providers/youtube-music.md`, `docs/providers/innertubex.md`, `docs/data-flow.md`, `docs/release-gates.md`, `PRIVACY.md`, `CONTRIBUTING.md`
- Modify: `README.md`, `CREDITS.md`, `gradle/libs.versions.toml` (trim unused entries)

**Interfaces:**
- Consumes: Task 0 findings.

**Acceptance Criteria:**
- Every provider the foundation talks to has a record with endpoint, tier, owner, kill switch, fallback, data sent, when sent, and opt-in status. A provider with no record is not added to the app.
- `docs/data-flow.md` lists exactly the data the foundation sends, derived from the provider records, and `PRIVACY.md` states plainly: no telemetry, and which third parties receive what.
- `docs/release-gates.md` lists, as unchecked boxes, the gates the spec says must pass before specific builds.
- `CREDITS.md` has a specific "what was taken / copied or adapted" line for every ledger row.

- [ ] **Step 1: Write the YouTube provider record**

`docs/providers/youtube-music.md`:
```markdown
# Provider: YouTube Music

- Tier: 2 (unofficial use of a commercial service's endpoints)
- Endpoint: `music.youtube.com` / `youtubei` endpoints via InnerTubeX
- Owner: Google (no relationship with Aria)
- Default: on (the app cannot work without it)
- Kill switch: registry key `killSwitches.youtube` (data only)
- Fallback: none; the app shows "YouTube Music is unavailable" and keeps downloaded and local tracks playable
- Data sent: search queries, video IDs, device/client identifiers the client library sends
- When: on search and on playback only; no background calls from the foundation
- Opt-in: not applicable (core feature); disclosed in the first-run text and PRIVACY.md
```

- [ ] **Step 2: Write the InnerTubeX record from the Task 0 findings**

`docs/providers/innertubex.md`: copy the license name, store-policy finding and PoToken finding from `docs/spikes/2026-10-innertubex.md`, with the same fields as above (it is a library, so "Data sent: none of its own; it is the transport for the YouTube Music record").

- [ ] **Step 3: Write data-flow and privacy text**

`docs/data-flow.md`: a table with one row per provider record (columns: provider, data, when, opt-in), currently one row for YouTube Music.

`PRIVACY.md`:
```markdown
# Privacy

Aria has no telemetry, analytics, advertising or crash-reporting service. Nothing is sent to the Aria project.

When you search or play music, the app contacts YouTube Music to do it. That request contains your search text or the video ID, and the identifiers the YouTube client library normally sends. See `docs/data-flow.md` for the full list, which is generated from the provider records.

If the app crashes it writes a report to your device. It is shown only on your device, and you decide whether to copy it into a GitHub issue. Tokens, cookies and URL secrets are removed before it is shown.

Aria is not affiliated with Google or YouTube.
```
Add a sentence per provider here whenever a provider record is added.

- [ ] **Step 4: Release gates**

`docs/release-gates.md`:
```markdown
# Release gates

## Before the first public build (alphas included)
- [ ] Final app name chosen; trademark and store-name check done (GitHub, F-Droid, Play, trademark search). "Aria" is crowded.
- [ ] Final package ID chosen, tied to a domain or account the maintainer controls; `aria.properties` updated.
- [ ] Repo on an account with 2FA; Codeberg mirror configured; APK signing key backed up offline.
- [ ] Non-affiliation notice present in README and the About screen.

## Before the first beta
- [ ] License and provenance audit passes: every ported directory has a ledger row, upstream license read, SPDX headers present, font license texts included (or fonts replaced).
- [ ] Vendored native code (`vibrafp`) audited or excluded.
- [ ] Kyant0/backdrop upstream `NOTICE` file checked (copy to `licenses/NOTICE` if it exists); `licenses/Apache-2.0.txt` present.
- [ ] Privacy policy matches `docs/data-flow.md` for every provider shipped.
- [ ] Room schema frozen; every change after this needs a tested migration.

## Before v1.0
- [ ] Google Play policy position confirmed (assumed unavailable until checked).
- [ ] IzzyOnDroid and F-Droid metadata and review requirements met.
- [ ] Spotify registry signing key stored only in CI secrets, separate from the APK key; two public keys embedded.
- [ ] Discord presence: auth flow read and classified, or feature left out (spec 7.2).
```

- [ ] **Step 5: CONTRIBUTING, CREDITS lines, catalog trim**

`CONTRIBUTING.md`: states the rules a contributor must follow (GPL-3.0-only header on every new file; new provider needs a record in `docs/providers/` first; ported code needs a ledger row and an upstream license check before the PR; module dependency rules enforced by `checkModuleGraph`; no telemetry; Actions stay SHA-pinned; after any dependency bump regenerate `gradle/verification-metadata.xml` with `./gradlew --write-verification-metadata sha256 help` and review the diff).

Update `CREDITS.md` so each ledger row has a specific line (for example: "`innertube`: Meld's module, copied with package rename, wraps InnerTubeX; parsing code remains from Meld/Metrolist").

Trim the catalog: for each alias in `gradle/libs.versions.toml`, `grep -rn "libs\.<alias with dots>" --include=*.kts .`; delete aliases with no match and every Firebase, Cast and Qobuz-related entry not used by a current module. Run `./gradlew help` after to confirm the catalog still parses.

- [ ] **Step 6: Final verification and commit**

Run:
```bash
./gradlew test checkModuleGraph :app:assembleFossDebug :app:assembleGmsDebug
python3 scripts/check-provenance.py . && scripts/check-backup-rules.sh && scripts/check-no-destructive-migration.sh
```
Expected: all succeed.

```bash
git add -A && git commit -m "docs: provider records, data-flow, privacy, release gates, contributing"
```

---

## Spec Coverage

| Spec requirement | Task |
|---|---|
| Module layout (section 3), dependency rule | 1, 2, 3 (rule enforced), 8, 9 |
| Room v1, no legacy migrations, schema exported, migration harness, no destructive fallback | 4 (+ guard script), 5 |
| `TrackId` and Spotify-tracks-are-not-songs | 2; Spotify side in sub-project 5 (see Deferred) |
| Generic `track_match` table | 4 |
| Shared core entities from Echo/Meld (not Spotify/Qobuz tables) | 5 |
| Secrets never in Room | 4, 5 (no token entity ported); token storage in sub-project 5 |
| `GlassSurface`, glass fallback, Auto/On/Off, battery saver, tokens | 7 |
| Two player skins, accessibility rules | Deferred to sub-project 3 |
| `StreamSource` seam for future lossless | 2, 8 |
| Walking skeleton end to end | 8, 9, 10 |
| Provider policy, vetting records, data-sent field | 12 (records), 7.1 rules enforced by contributing rule |
| Signed registry: schema validation, Ed25519, two keys, max age, last-known-good | 6 |
| Registry-delivered YouTube client config (data only) | 6 (`youtubeClients`), consumed in sub-project 2 |
| Encrypted tokens excluded from backup | Backup rules in 10; encrypted storage in sub-project 5 |
| Login WebView limits, TOTP via signed registry | Deferred to sub-project 5 (registry carries `totpSecret`, Task 6) |
| No Firebase/analytics; crash log with redaction | 10, 11 (grep check in `check-backup-rules.sh`) |
| Cleartext disabled | 10 |
| Flavors `foss`/`gms`, ABI splits | 10 |
| CI: build both flavors, lint, tests, schema check, license scan | 11 (lint: add `./gradlew lint` to `ci.yml` once the first lint baseline is generated) |
| Nightly playback smoke test | 11 |
| Own registry host, no dependence on Meld's | 6 (client), hosting in sub-project 5 |
| Provenance ledger + CI check, SPDX headers, audit before port | 1, 5, 7, 8, 11 |
| `CREDITS.md`, non-affiliation notice | 1, 12 |
| Name/package ID from one location | 1, 10 |
| Release gates (name, package ID, license audit, Play, store) | 12 |
| InnerTubeX license/store/PoToken checks | 0 |
| Privacy policy and data-flow table | 12 |
| Fonts license texts; `vibrafp` audit | 12 (gates); executed when those assets are ported |

### Deliberate deferrals (surfaced, not silent)

These spec items are **not** built in this plan, by design, because they belong to later sub-projects. Each needs a task in that sub-project's plan.

1. Spotify token encryption, login WebView lockdown, TOTP flow, hash registry hosting (sub-project 5).
2. Material and Apple-style player skins; accessibility pass on the real UI (sub-project 3).
3. Backup and restore feature, Chromecast, Listen Together relay, Discord decision, USB audio/DSP, recognition, Canvas, widget, Android Auto, Last.fm (sub-project 6).
4. Shrinking Meld's inherited response-parsing code (tracked follow-up, not a v1 promise).
5. `./gradlew lint` baseline and CI step (add after the first baseline exists; flagged in the table above rather than silently dropped).
6. Fonts: none are ported in this plan, so their license texts are a gate for the sub-project that adds them.

## Stress Test Results

Run 2026-10-30, Mode A (edits applied inline). Eight branches, each answered "Agree"; all eight changed the plan.

| # | Branch | Finding | Change |
|---|--------|---------|--------|
| 1 | Verification and delivery | Nothing can be compiled or pushed from the planning sandbox | Prerequisite (GitHub repo, push access) and a Definition of done of green CI per task |
| 2 | Module and build structure | `settings.gradle.kts` listing absent modules fails under Gradle 9; `build-logic` convention plugins cannot be verified under AGP 9 | Include each module in its own task; explicit per-module files plus `buildSrc` |
| 3 | Registry verifier | Future-dated payloads, rollback and cross-protocol signature reuse were possible | Future-skew rejection, anti-rollback in `RegistryRepository.accept`, `aria-registry-v1` signing context |
| 4 | Entity port | Glob missed nested backdrop dirs; `@Database` lacked views; port scan needed a limit | `find`-based rename, views and `SchemaContentTest`, `scripts/port-scan.sh` with a limit of 10 |
| 5 | Glass provenance | Backdrop code is Apache-2.0 (Kyant0), not GPL-3.0; the old step would have stopped or mislabelled it | Vendor with Apache-2.0 attribution: license text, per-file modified notice, `CREDITS.md`, two ledger rows, NOTICE check as a beta gate |
| 6 | Service start | `startForegroundService` crashes if playback never starts; no notification permission on Android 13+ | `startService`, first-play `POST_NOTIFICATIONS` request, `PlaybackHost` marked `TODO(sub-project 2)` |
| 7 | CI correctness | Schema check ignored untracked files; smoke test could skip silently; duplicate nightly issues; `buildSrc` tests not run | `git status --porcelain` check, skipped-test failure, one labelled issue, `-p buildSrc test`, timeouts and concurrency |
| 8 | Supply chain | Actions on mutable tags; wrapper unvalidated; no dependency pinning (InnerTubeX comes from JitPack) | Step 5b: SHA-pinned Actions with enforcing script, wrapper validation, Dependabot, committed Gradle dependency verification |

Remaining risks, all needing network or the maintainer: Task 0 spike results (InnerTubeX license and store policy, PoToken), upstream Kyant0 NOTICE file, "Aria" name and trademark clearance, and the SHA and checksum generation in Task 11 Step 5b.
