<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Contributing

Aria is a walking skeleton: the module layout, build and checks exist; most features do not. These rules are enforced by review and, where noted, by CI.

## Rules

1. **SPDX header.** Every new source file starts with `SPDX-License-Identifier: GPL-3.0-only` in the file type's comment syntax. Files that cannot carry a comment (for example JSON) are exempt.
2. **Provider record first.** A new network provider needs a record in `docs/providers/` before any code that talks to it: endpoint, tier, owner, kill switch, fallback, data sent, when sent, opt-in status. Add its row to `docs/data-flow.md` and a sentence to `PRIVACY.md` in the same change. No record, no provider.
3. **Ledger and license audit.** Ported or adapted code needs a row in `docs/provenance.csv` (origin repo, commit SHA, license, kind, notes) and a line in `CREDITS.md`, and the upstream license must be read before the PR. Every Gradle module needs a ledger row; `python3 scripts/check-provenance.py .` checks this. The `checkLicense` dependency scan must pass; do not widen `config/allowed-licenses.json` to silence it without a maintainer decision.
4. **Module graph.** Module dependencies follow `buildSrc/src/main/kotlin/ModuleGraphRules.kt`, enforced by `./gradlew checkModuleGraph`. A new module needs a rule there.
5. **No telemetry.** No analytics, advertising, Firebase or crash-reporting services. `scripts/check-backup-rules.sh` greps for some of these.
6. **Actions stay SHA-pinned.** Every `uses:` in `.github/workflows` is pinned to a full 40-hex commit SHA with the version as a trailing comment. `scripts/check-actions-pinned.sh` enforces it. Dependabot proposes bumps.
7. **Dependency verification.** After any dependency bump (Dependabot PRs cannot do this themselves), run `./gradlew --write-verification-metadata sha256 help`, review the diff to `gradle/verification-metadata.xml`, and commit it. Do not add wildcard `<trusted-artifacts>` entries. The file is not generated yet; see `docs/release-gates.md`.
8. **Room.** No destructive migration fallback (`scripts/check-no-destructive-migration.sh`); the exported schema is committed.

## Checks before a PR

```bash
./gradlew test checkModuleGraph :app:assembleFossDebug :app:assembleGmsDebug
python3 scripts/check-provenance.py . && scripts/check-backup-rules.sh && scripts/check-no-destructive-migration.sh && scripts/check-actions-pinned.sh
```

## How CI reports failures

Full GitHub Actions job logs are not readable without a token. `scripts/ci-gradle.sh <gradle args>` runs Gradle and, on failure, emits the tail of the output as an `::error` workflow annotation, which is visible on the run page and through the check-runs API. Run Gradle steps in workflows through this script.
