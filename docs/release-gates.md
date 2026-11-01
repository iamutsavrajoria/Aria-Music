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
- [ ] EPL-2.0 audit (open): `config/allowed-licenses.json` still allows `EPL-2.0` and `Eclipse Public License - v 2.0` as a blanket allow, which is broader than wanted. EPL-1.0 was removed (GPL-3.0-incompatible). Exactly one dependency triggered the EPL-2.0 entry: its license names were Apache License 2.0, Eclipse Public License v2.0, GPL v2, and GPL v2 + Classpath Exception (a multi-license; the Apache-2.0 option is usable). The jk1 plugin format does support a narrow entry (`moduleLicense` + `moduleName` `group:artifact` + `moduleVersion`, each an exact or whole-string regex match; verified in the plugin README), but the CI log did not print the artifact coordinate, so the exception could not be written. To close: run `checkLicense` once with the EPL-2.0 names removed, read the failing coordinate from the report, replace the blanket entries with one narrow entry for it (or confirm the Apache-2.0 option alone passes the check and drop them), and record the artifact here.
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
