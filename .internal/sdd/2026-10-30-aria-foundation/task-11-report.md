# Task 11 report

Commit: 0582c7a (local, not pushed). Status: DONE_WITH_CONCERNS.

## Files
- Modified: .github/workflows/ci.yml, build.gradle.kts (license plugin + licenseReport), innertube/build.gradle.kts (aria.smoke system property, testImplementation coroutines-core), .gitignore (pycache)
- Created: .github/workflows/smoke.yml, .github/dependabot.yml, config/allowed-licenses.json, scripts/check-provenance.py, scripts/test_check_provenance.py, scripts/check-actions-pinned.sh, innertube/.../smoke/PlaybackSmokeTest.kt

## Verified locally
- pytest: red first (4 failed before script existed), then 5 passed.
- check-provenance.py . exits 0 against the real ledger (all 7 modules already had rows; no ledger changes).
- check-actions-pinned.sh: exit 1 on actions/checkout@v4 in a scratch workflow, exit 0 with SHA + comment and ./local; exit 0 on the real workflows.
- check-backup-rules.sh, check-no-destructive-migration.sh pass; workflow YAML parses.

## NOT verified (Gradle cannot run here)
- PlaybackSmokeTest compile and skip behaviour; the smoke test was never run (YouTube network).
- license plugin: version, config, and whether checkLicense passes. Confidence moderate (~65%). Chose 3.1.4 (newest release, 2026-06-10; changelog says tested against Gradle 9.4/9.5, not 9.7/AGP 9.3; 2.9 predates Gradle 9). Its README says Gradle 9 needs --no-parallel (CI passes it) and configuration cache is unsupported. Default scans `runtimeClasspath`, which Android modules lack, so I set configurations to release/fossRelease/gmsRelease runtime classpaths (my assumption: names absent from a project are skipped). Likely CI follow-ups: unmatched license names in config/allowed-licenses.json (decide per dependency; do not widen blindly), or resolution of flavored :app classpaths. I added EPL-2.0, LGPL-3.0 and "Public Domain" to the plan's list because the acceptance criteria name them.
- glass-ui emulator job (API 26/34, :designsystem:connectedDebugAndroidTest) is UNVERIFIED; wrapped in scripts/ci-gradle.sh and fail-fast false.
- Action SHAs: resolved with `git ls-remote --tags` (gh api and WebFetch to api.github.com are blocked by session policy; git reads of public repos work). Annotated tag for gradle/actions v4.4.4 dereferenced (^{}); others are lightweight tags. Pins: checkout v4.4.0 11d5960a..., setup-java v4.9.1 cf277c60..., gradle/actions (setup-gradle and wrapper-validation) v4.4.4 748248dd..., android-emulator-runner v2.38.0 (peeled) a421e438.... I stayed on the existing major versions (v4/v2) rather than newer majors (checkout v7, setup-java v6, gradle/actions v6 exist); Dependabot will propose bumps.

## Controller must do: Gradle dependency verification (Step 5b.4)
gradle/verification-metadata.xml was NOT created and the `test -f` check is NOT in ci.yml (a TODO comment marks the spot). Generate it from a run with network and Gradle: `./gradlew --write-verification-metadata sha256 :app:assembleFossDebug :app:assembleGmsDebug test` (also run :app:lintDebug, buildSrc tests, checkLicense, and androidTest assembly so all configurations are covered), review the diff (no wildcard trusted-artifacts), commit it, then add `test -f gradle/verification-metadata.xml` to Static checks. Note: once committed, the license plugin and glass-ui job may need extra entries. Record the regeneration procedure in CONTRIBUTING.md (Task 12).

## Deviations
- `./gradlew` replaced by scripts/ci-gradle.sh everywhere; kept existing steps; `build test` kept as one step (lint included) with a separate flavor assembly step.
- checkout uses persist-credentials false in all jobs (also smoke.yml; its gh step uses github.token via env, unaffected).
- License plugin 3.1.4 and explicit `configurations` (above). checkLicense is registered only by the plugin.
- Added testImplementation(libs.coroutines.core) in :innertube for runBlocking in the smoke test (existing alias; safety against transitive classpath).
- Added __pycache__/.pytest_cache to .gitignore.
- Nightly smoke failure steps follow the plan exactly (skipped detection, single open issue).
