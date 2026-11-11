# Task 12 report

Status: DONE_WITH_CONCERNS. Commit 76bd4d1 (local, not pushed).

## Files
Created: docs/providers/youtube-music.md, docs/providers/innertubex.md, docs/data-flow.md, docs/release-gates.md, PRIVACY.md, CONTRIBUTING.md.
Modified: README.md (status, repo URL, doc links), CREDITS.md (one specific line per ledger row), gradle/libs.versions.toml (trim).
Markdown files carry an SPDX HTML comment.

## Catalog trim
Removed 26 library aliases with no `libs.<alias>` match in any .kts/.kt (guava, coroutines-guava, browser, hilt-navigation, datastore, compose-animation, compose-reorderable, viewmodel-compose, lifecycle-process, materialKolor, appcompat, coil, coil-network-okhttp, ucrop, shimmer, palette, media3-cast, mediarouter, cast-framework, ktor-client-cio, ktor-client-mock, json, kuromoji-ipadic, tinypinyin, protobuf-javalite, protobuf-kotlin-lite), the `protobuf` plugin, and 19 version entries only they used. Verified with a script that every remaining `libs.*` reference resolves to a catalog entry and the TOML parses.

## Unverified
- Gradle could not run in this sandbox (wrapper download blocked by the proxy, 403). `./gradlew help`, `test`, `checkModuleGraph` and both assemble tasks were NOT run; CI must confirm the catalog trim. Static checks that did run and passed: check-provenance.py, check-backup-rules.sh, check-no-destructive-migration.sh, check-actions-pinned.sh, pytest scripts (5 passed).
- InnerTubeX `-only`/`-or-later` variant still unconfirmed (recorded as such, added as a beta gate).
- The brief's gate text for catalog entries not used by "a current module" was applied by grep only.

## Deviations
- Provider records: the brief's template claims a working kill switch, "YouTube Music is unavailable" fallback, local/downloaded playback and first-run disclosure. None are built (registry is not fetched or read by the app; no such message, downloads, first-run text, About screen). Records state these as planned/not built instead of implying they exist.
- Registry keys: only throwaway keypairs generated inside verifier tests exist; no production keys are embedded. Added a beta gate saying so rather than the plan's "two public keys embedded" implication.
- release-gates.md adds beta gates for verification-metadata.xml (with the Step 5b command, noting CI's `test -f` check is still a TODO in ci.yml), InnerTubeX license variant, and a v1.0 gate for PoToken. Package ID and name noted as placeholders.
- CONTRIBUTING says SPDX header is exempt for files that cannot carry comments (e.g. JSON); the brief only says "every new file". Also lists the Room no-destructive-migration rule and checkLicense rule.
- data-flow.md is hand-derived from the records (no generator exists), despite PRIVACY text in the brief saying "generated"; PRIVACY.md says "derived".
- Did not run `./gradlew` verification step 6 for the above reason.
