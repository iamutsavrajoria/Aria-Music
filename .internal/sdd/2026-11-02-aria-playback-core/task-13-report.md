# Task 13 report

Status: DONE_WITH_CONCERNS (nothing compiled or run; no Gradle/emulator in sandbox).

Implemented: catalog entries (androidx-test-runner/rules/ext-junit, hilt-testing); playback and app androidTest wiring (runner, deps, kspAndroidTest); playback androidTest AriaTestRunner, TestAriaApp (allowLocalStreams = true, real DefaultPlaybackEngine), SilentWav, ServiceFakes, PlaybackServiceTest; app androidTest HiltTestRunner, HiltWiringTest; CI job `playback-service` (copy of glass-ui, matrix API 26/34, runs :playback:connectedDebugAndroidTest :app:connectedFossDebugAndroidTest).

Tests written, not run: PlaybackServiceTest (2 tests), HiltWiringTest (1). YAML parses; check-actions-pinned.sh passes.

Files: .github/workflows/ci.yml, gradle/libs.versions.toml, playback/build.gradle.kts, app/build.gradle.kts, playback/src/androidTest/{AndroidManifest.xml, kotlin/app/aria/playback/*.kt}, app/src/androidTest/kotlin/app/aria/music/*.kt.

Deviations: added playback/src/androidTest/AndroidManifest.xml declaring POST_NOTIFICATIONS (test APK only), because GrantPermissionRule cannot grant an undeclared permission on API 33+ and :playback's manifest does not declare it. Production manifests untouched. Only added shared versionRefs (androidxTestRunner used for runner and rules). Explicit imports instead of the brief's wildcard/`org.junit.*`.

Concerns: (1) ABI split in :app may break connectedFossDebugAndroidTest; fallback per brief is `isEnable = !project.hasProperty("noAbiSplits")` plus -PnoAbiSplits in this job; not applied preemptively. (2) kspAndroidTest with product flavors should cover all androidTest variants, unverified. (3) Notification assertion on API 34 depends on the foreground notification being posted; if flaky, check logcat. (4) Gradle dependency verification metadata, if later committed, needs the new androidTest artifacts. (5) Untrusted-controller rule is covered only by TrustPolicyTest (brief-recorded deviation).
