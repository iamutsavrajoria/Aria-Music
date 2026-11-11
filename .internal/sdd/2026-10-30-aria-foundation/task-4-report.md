# Task 4 report: `:data` Room v1 foundation and migration harness

Status: DONE_WITH_CONCERNS (nothing could be run locally; Gradle unavailable)

## Files
- Created: data/build.gradle.kts; data/src/main/kotlin/app/aria/data/{AriaDatabase,Converters,TrackMatchDao}.kt; entities/TrackMatchEntity.kt; data/src/test/kotlin/app/aria/data/{TrackMatchDaoTest,MigrationHarnessTest}.kt; scripts/check-no-destructive-migration.sh
- Modified: settings.gradle.kts (include(":data")), gradle/libs.versions.toml, docs/provenance.csv (`data,,,,original,...`; nothing ported), .github/workflows/ci.yml

## Deviations
- Catalog: added `room-ktx`, `room-testing` (version.ref room 2.8.4) and `coroutines-test` with a new `coroutines = "1.11.0"` version (same value as the existing coroutinesGuava pin; androidx-test-core and robolectric already existed).
- Script: added SPDX header, and `--exclude-dir=build --exclude-dir=.internal` so plans/build output cannot trip it. Not in the plan: a CI step running it right after buildSrc tests.
- Schema config uses the plan's `ksp { arg("room.schemaLocation", ...) }` (no Room Gradle plugin in the catalog).
- Plan code otherwise verbatim plus SPDX headers on build.gradle.kts.

## Not run / unverified
- All Gradle tasks (`:data:testDebugUnitTest`, build, checkModuleGraph). Script ran locally: exit 0.
- `data/schemas/app.aria.data.AriaDatabase/1.json` NOT created. Controller must commit it after the first CI run generates it (KSP exportSchema).
- Risk: MigrationHarnessTest reads 1.json from test assets via `assets.srcDir(schemas)` with no explicit task dependency on KSP. On first CI the file may be missing/late at asset-merge time, so that test may fail until 1.json is committed; after that it should pass. If the race persists, wire `mergeDebugUnitTestAssets` (or equivalent) to depend on the KSP task.
- KSP 2.3.11 + AGP 9.3.1 built-in Kotlin compatibility for `alias(libs.plugins.kotlin.ksp)` in a library module is untested here; `MigrationTestHelper` 4-arg constructor assumed available in room-testing 2.8.4.

## Commit
See SHA below (local, not pushed).
