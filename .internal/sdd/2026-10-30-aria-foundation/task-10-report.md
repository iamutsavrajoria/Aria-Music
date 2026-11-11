# Task 10 report: :app walking skeleton

Commit: 873105b (local, not pushed). Status: DONE_WITH_CONCERNS (nothing compiled; Gradle cannot run here).

## Files
Created: app/build.gradle.kts, app/proguard-rules.pro, app/src/main/AndroidManifest.xml, app/src/gms/AndroidManifest.xml,
app/src/main/kotlin/app/aria/music/{AriaApp,MainActivity,SkeletonScreen,SharedPrefsResumeStore}.kt, di/AppModule.kt,
app/src/main/res/xml/{data_extraction_rules,backup_rules}.xml, core/.../crash/CrashReportFormatter.kt and its test,
scripts/check-backup-rules.sh (executable).
Modified: settings.gradle.kts (include(":app")), docs/provenance.csv (app row, original), .github/workflows/ci.yml (new step after check-no-destructive-migration).

## Verified locally
scripts/check-backup-rules.sh prints ok.

## Not run / unverified
Gradle assemble of both flavors, :core tests, checkModuleGraph, lint, the RenameProbe check (Step 8), the device check (Step 9).
Risks for CI: Hilt/KSP with AGP 9 built-in Kotlin; R8 on release (minify) may report missing classes beyond my -dontwarn list (AGP prints missing_rules.txt); lint on the app.

## Deviations from the brief
- Catalog aliases: libs.hilt (not hilt.android), libs.activity (not activity.compose); added libs.compose.runtime and libs.coroutines.core as app dependencies (app uses both directly).
- buildFeatures resValues = true added: AGP 8+ defaults it off and resValue() would fail otherwise.
- Properties load uses use { load(it) } instead of use(::load) (overload ambiguity).
- Release build type references new app/proguard-rules.pro (not in brief's file list) with dontwarn for java.lang.management and slf4j, to avoid R8 missing-class failures under build.
- AriaApp passes BuildConfig.FLAVOR (foss/gms) as the crash-report flavor instead of debug/release.
- SkeletonScreen uses mutableIntStateOf for the play count (avoids a Compose autoboxing lint warning) and imports kotlinx.coroutines.delay.
- check-backup-rules.sh: added SPDX header, cd to repo root, and dropped the ineffective comment-filter grep -v (the firebase grep now fails the check directly). Catalog has no firebase lines, so nothing to remove.
- No robolectric.properties added: :app has no Robolectric tests.
