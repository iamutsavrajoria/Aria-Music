# Task 1 report: repository scaffold and single-source config

Status: DONE_WITH_CONCERNS. Commit: f9cb646f4dd6a76d515f5470c65f710bc31cdaa1 (local, not pushed).

## Built
Wrapper (Gradle 9.7.0, jar + scripts from Meld), version catalog (Meld's plus `tink = "1.15.0"` and `tink-android`), aria.properties, settings.gradle.kts, root build.gradle.kts, .gitignore, LICENSE (Meld's GPL-3.0 text), README (non-affiliation with Google, YouTube, Spotify), CREDITS, minimal ci.yml (tag-pinned actions), and core/build.gradle.kts (Android library template, namespace app.aria.core).

## Files created
.github/workflows/ci.yml, .gitignore, CREDITS.md, LICENSE, README.md, aria.properties, build.gradle.kts, core/build.gradle.kts, gradle/libs.versions.toml, gradle/wrapper/gradle-wrapper.jar, gradle/wrapper/gradle-wrapper.properties, gradlew, gradlew.bat, settings.gradle.kts.
SPDX GPL-3.0-only header added to every file where the format allows (not LICENSE, the wrapper jar, gradlew scripts or wrapper properties, which are verbatim copies).

## Not verified (needs a real Gradle run)
- `./gradlew help` and `./gradlew build test`: nothing was run; no network to Maven/Google/JitPack/services.gradle.org.
- That the root buildscript classpath plus versionless `id("com.android.library")` resolves under AGP 9.3.1 / Gradle 9.7.0 (mirrors Meld's root, untested here).
- Wrapper SHA-256: `84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae` for gradle-9.7.0-bin.zip. It came from a single WebFetch of gradle.org/release-checksums (answered by a summarizing model); gradle.org was not reachable by curl and a web search did not corroborate it. If CI fails with a checksum mismatch, this is the first suspect; compare against the page by eye.
- gradle-wrapper.jar not independently checksum-verified (copied from Meld).

## Deviations
1. settings.gradle.kts includes only `:core`, not `:domain` and `:core` as in the plan snippet: `domain/` does not exist yet (Task 2 owns it and adds its include). The brief's quality bar requires includes only for existing directories. Note: Step 8 says finish Task 2 Steps 1-5 before the first push, so the push order is the controller's call.
2. Root build.gradle.kts adds a `buildscript { classpath(libs.gradle); classpath(kotlin("gradle-plugin", ...)) }` block (repositories google, mavenCentral), copied in spirit from Meld's root. The plan's root file omitted it, but without it versionless `com.android.library` / `org.jetbrains.kotlin.jvm` in module files would not resolve. Meld's aliyun/jitpack buildscript repos and clean/Compose-report tasks were left out.
3. gradle-wrapper.properties: appended `distributionSha256Sum` (see above). distributionUrl already was 9.7.0. Meld's gradle-daemon-jvm.properties was not copied.
4. core/build.gradle.kts created now (Step 8 asks for an empty core module) using the plan's Step 5 template.
5. Docs link `docs/provenance.csv` in README/CREDITS does not exist yet (later task).
