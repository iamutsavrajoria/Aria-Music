# Task 6 report: :core signed registry verification

Status: DONE_WITH_CONCERNS. Commit 903262f (local, not pushed).

## Files
- core/build.gradle.kts (modified: added kotlin-serialization plugin alias and dependencies)
- core/src/main/kotlin/app/aria/core/registry/{Registry,RegistryVerifier,RegistryRepository}.kt
- core/src/test/kotlin/app/aria/core/registry/{RegistryVerifierTest (13 tests),RegistryRepositoryTest (7 tests)}.kt
Source and tests copied verbatim from the plan. No security check changed. No Robolectric used, so no robolectric.properties.

## Deviations / concerns
1. Serialization dependency: the catalog has no kotlinx-serialization-json alias. Per the plan's fallback I used libs.ktor.serialization.json (ktor-serialization-kotlinx-json), which I believe exposes kotlinx-serialization-json as an api dependency. UNVERIFIED. If CI says unresolved Json/decodeFromString, add a catalog entry for org.jetbrains.kotlinx:kotlinx-serialization-json (version compatible with Kotlin 2.4.10; I could not look one up, no network). That would be cleaner than pulling ktor into :core.
2. Tink: kept libs.tink.android (plan) for main; tests use it through the implementation classpath on the JVM unit-test classpath. Not changed to non-android artifact. UNVERIFIED; fallback is testImplementation of com.google.crypto.tink:tink:1.15.0 (needs a new catalog alias).
3. The Kotlin serialization plugin is applied via alias(libs.plugins.kotlin.serialization) (already declared apply false in root).
4. Provenance: core row already exists as original; no new row added (code is original).

## Not run
Everything (no Gradle here): compile, 20 tests, checkModuleGraph (core still depends only on :domain). 
