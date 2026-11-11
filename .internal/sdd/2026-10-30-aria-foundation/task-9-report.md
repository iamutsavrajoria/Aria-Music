# Task 9 report: :playback minimal service and PlayerController

Status: DONE_WITH_CONCERNS (nothing compiled; CI is the first compiler)
Commit: 8592ba6 (local, not pushed)

## Files
- playback/build.gradle.kts, playback/src/main/AndroidManifest.xml
- playback/src/main/kotlin/app/aria/playback/{PlayerController,AriaPlaybackService,ResumeStore}.kt
- playback/src/test/kotlin/app/aria/playback/PlayerControllerTest.kt (plain JVM, 3 tests, no Robolectric so no robolectric.properties)
- settings.gradle.kts (include(":playback")), docs/provenance.csv (original row)

## Deviations
- Catalog aliases differ from the brief: used libs.media3, libs.media3.okhttp, libs.media3.session (verified in libs.versions.toml). coroutines.core, coroutines.test, junit exist as named.
- Added SPDX header to build.gradle.kts and the test (plan snippets omitted on some).
- Player and ResumeStore interfaces live in PlayerController.kt as in the plan; ResumeStore.kt holds an extra InMemoryResumeStore (the brief listed the file but gave no content). Persistent impl stays in :app (Task 10).
- Imported TrackId instead of fully-qualified names in the controller.
- PlaybackHost kept with TODO(sub-project 2).

## Not verified (could not run Gradle)
- Compilation, unit tests, lint, checkModuleGraph (:playback -> :domain only, allowed by ModuleGraphRules).
- Lint risk points: exported service with intent-filter (standard Media3 pattern), foregroundServiceType=mediaPlayback with FOREGROUND_SERVICE_MEDIA_PLAYBACK declared; possible UnsafeOptInUsageError or unused-dependency warnings are the likeliest surprises. media3-okhttp is declared but unused (per brief).
- Our Player interface shares a simple name with media3 Player; the service imports no media3 Player, so it resolves to ours.
