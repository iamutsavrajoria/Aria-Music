# Task 7 report: :designsystem tokens and GlassSurface

Status: DONE_WITH_CONCERNS (nothing compiled; CI is the first compiler)

## Files
- designsystem/build.gradle.kts (plan's, plus: testInstrumentationRunner, `libs.compose.ui.util`, `debugImplementation(libs.compose.ui.test.manifest)`)
- designsystem/src/main/kotlin/app/aria/designsystem/{GlassMode,Tokens,GlassSurface,GlassEffect}.kt
- designsystem/src/main/kotlin/app/aria/designsystem/backdrop/** (30 files, ported)
- designsystem/src/test/.../GlassResolverTest.kt (5 tests), designsystem/src/androidTest/.../GlassSurfaceTest.kt
- licenses/Apache-2.0.txt (from /usr/share/common-licenses; head shows "Apache License / Version 2.0, January 2004")
- gradle/libs.versions.toml: added compose-ui-test-junit4, compose-ui-test-manifest (version.ref compose)
- settings.gradle.kts: include(":designsystem")
- docs/provenance.csv, CREDITS.md

## Port checks run
- Audit: 31 files; 0 lacking Vendored header; 30 "Apache License 2.0" + 1 "GPL-3.0". As expected.
- After port: no file lacks "Modified for Aria" among vendored files; port-scan: `distinct foreign imports: 0 (limit 10)`.
- Only non-AndroidX/Compose imports: androidx.annotation.* and org.intellij.lang.annotations.Language (not added compileOnly; relies on kotlin-stdlib's transitive jetbrains annotations).
- Echo commit b04d7507b33c0671b3a23d2658b24a376add4cc7 confirmed.
- NOTICE not checked (no network); recorded in the backdrop ledger row.

## Deviations
1. GlassSurface: `liquidGlass` takes `CornerBasedShape`, plan passes `Shape`. GlassSurface uses glass only when `shape is CornerBasedShape` (explicit cast), else the fallback Surface. Default MaterialTheme.shapes.large is corner-based.
2. GlassEffect.kt: `LocalAppBackdrop` default changed from `error("No AppBackdrop provided")` to `emptyBackdrop()`; otherwise GlassSurface(ON) in GlassSurfaceTest on API 34 (no backdrop provider) would throw. Also removed a KDoc link to Echo's MainActivity. Both noted in ledger.
3. Added `compose-ui-util` dependency (ported Lens/etc. use androidx.compose.ui.util fastCoerce*/lerp), ui-test-manifest (createComposeRule needs the host activity), instrumentation runner.
4. Ledger has 3 rows, not 2: an extra `designsystem` original row for the hand-written files, consistent with earlier modules.
5. No robolectric.properties (no Robolectric tests in this module).

## Not run / unverified
- Everything Gradle: compile of 31 ported files against Compose 1.12.0 / Material3 1.5.0-alpha27 (ported from Echo on Compose 1.10.2). Risk spots: androidx.compose.ui.node.* APIs (requireGraphicsContext, observeReads, invalidateDraw, ObserverModifierNode) in DrawBackdropModifier/LayerRecorder; GraphicsLayer/RenderEffect usage in internal/; @Language and androidx.annotation resolution; MaterialTheme.shapes in M3 alpha.
- GlassResolverTest, assembleDebug, checkModuleGraph (":designsystem" -> emptySet rule; module depends on no project).
- GlassSurfaceTest (androidTest not built by `build`).

## Commit
See git log (local, not pushed); SHA appended below.
SHA: a9bc6133c713dfea1a1e8b01cd336476687c157f
