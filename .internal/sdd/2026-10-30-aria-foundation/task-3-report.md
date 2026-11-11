# Task 3 report: module-graph rule check

Status: DONE_WITH_CONCERNS (nothing could be run locally). Commit: 69167a9 (local, not pushed).

Files: buildSrc/build.gradle.kts, buildSrc/src/main/kotlin/ModuleGraphRules.kt,
buildSrc/src/test/kotlin/ModuleGraphRulesTest.kt, build.gradle.kts (checkModuleGraph appended),
domain/src/test/.../TrackIdTest.kt (carry-over 1: "dQw4w9WgXc!" rejected), docs/provenance.csv (carry-over 2: core row, kind original).

Deviations: plan code used verbatim except buildSrc adds `tasks.withType<Test> { useJUnit() }` (explicit JUnit 4).
kotlin-dsl needs no version (uses Gradle's embedded Kotlin), so there is no catalog/Kotlin pin to align.
buildSrc has its own repositories (mavenCentral), unaffected by the root FAIL_ON_PROJECT_REPOS setting.

Not run / unverified: `./gradlew -p buildSrc test`, `./gradlew checkModuleGraph`, `./gradlew build test`, the new TrackId test.
Things for CI to confirm: jvmToolchain(21) resolves in buildSrc (same as root modules); embedded-Kotlin buildSrc coexisting with
root classpath kotlin-gradle-plugin 2.4.10 (possible warning, unlikely failure); ProjectDependency.path exists in Gradle 9.7.
Current tree: core and domain have no project() dependencies, so checkModuleGraph should pass.
Note: checkModuleGraph reads subprojects/configurations in doLast (not configuration-cache safe); no config cache is enabled today.
