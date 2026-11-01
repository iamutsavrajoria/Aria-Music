<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Release gates

Unchecked boxes are open. Source: the foundation design spec and plan; status as of 2026-11-01 (walking skeleton).

## Before the first public build (alphas included)
- [ ] Final app name chosen; trademark and store-name check done (GitHub, F-Droid, Play, trademark search). "Aria" is crowded; the name is a placeholder.
- [ ] Final package ID chosen, tied to a domain or account the maintainer controls; `aria.properties` updated. `app.aria.music` is a placeholder.
- [ ] Repo on an account with 2FA; Codeberg mirror configured; APK signing key backed up offline. (The repo is public at https://github.com/iamutsavrajoria/Aria-Music.)
- [ ] Non-affiliation notice present in README and the About screen. README has it; there is no About screen yet.

## Before the first beta
- [ ] Gradle dependency verification: generate and commit `gradle/verification-metadata.xml` (not yet generated; CI does not require it yet). Needs network access:
  `./gradlew --write-verification-metadata sha256 :app:assembleFossDebug :app:assembleGmsDebug test`
  then review the diff, commit, re-enable the `test -f gradle/verification-metadata.xml` check in `ci.yml`, and confirm the build passes under verification. No wildcard `<trusted-artifacts>` entries.
- [ ] License and provenance audit passes: every ported directory has a ledger row, upstream license read, SPDX headers present, font license texts included (or fonts replaced).
- [ ] InnerTubeX LICENSE text and a source header read, and the `-only` or `-or-later` variant recorded in `docs/spikes/2026-10-innertubex.md` and `docs/providers/innertubex.md`.
- [ ] Vendored native code (`vibrafp`) audited or excluded. (Not ported in the foundation.)
- [ ] Kyant0/backdrop upstream `NOTICE` file checked (copy to `licenses/NOTICE` if it exists). `licenses/Apache-2.0.txt` is present.
- [ ] Privacy policy matches `docs/data-flow.md` for every provider shipped.
- [ ] Room schema frozen; every change after this needs a tested migration.
- [ ] Registry public keys: only throwaway test keys exist (generated inside the verifier tests). No production keys are embedded and the app does not fetch a registry. Generate the two production keys, embed the public halves, and store the private halves outside the repo, before any registry is served.

## Before v1.0
- [ ] Google Play policy position confirmed (assumed unavailable until checked).
- [ ] IzzyOnDroid and F-Droid metadata and review requirements met.
- [ ] Spotify registry signing key stored only in CI secrets, separate from the APK key; two public keys embedded.
- [ ] Discord presence: auth flow read and classified, or feature left out (spec 7.2).
- [ ] PoToken generation for YouTube playback exists, or the unavailable-stream behaviour is accepted (sub-project 2).
