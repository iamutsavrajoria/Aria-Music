# Task 8 report: :innertube port and adapters

Status: DONE_WITH_CONCERNS (nothing compiled; Gradle cannot run here). Commit: 1315c82 (local, not pushed).

## Files
- settings.gradle.kts: include(":innertube").
- innertube/build.gradle.kts: Meld's file; namespace app.aria.innertube, SPDX header, testOptions unitTests.isIncludeAndroidResources, api(project(":domain")). Kept core library desugaring and the useMavenLocalInnerTubeX switch.
- innertube/src/main/AndroidManifest.xml (Meld's empty manifest, needed for a library module).
- innertube/src/main/kotlin/app/aria/innertube/** : 86 ported main files, package renamed; 7 com.metrolist.innertubex references kept.
- innertube/src/test/kotlin/app/aria/innertube/** : Meld's 4 ported tests (UploadProgressInputStreamTest, PageHelperTest, SearchPageTest, MusicResponsiveHeaderRendererTest) plus adapter/MappingTest.kt exactly per brief.
- adapter/Mapping.kt, YouTubeSearchSource.kt, YouTubeStreamSource.kt exactly per brief (constructor, YouTube.player/search signatures, SearchResult.items, streamingData verified by grep against the port).
- docs/provenance.csv: one row for `innertube`, origin fe9a666..., GPL-3.0, adapted.
- SPDX GPL-3.0-only added to the 86 files that had no header; TimedLyricsResponse.kt keeps its Metrolist header.

## Checks run
- Rename sanity greps: no stray com.metrolist.innertube[^x]; only in comments/typealiases for innertubex.
- scripts/port-scan.sh innertube/src: `distinct foreign imports: 0 (limit 10)` (scanned src incl. tests).

## Not run / unverified
- ./gradlew :innertube:compileDebugKotlin, :innertube:testDebugUnitTest, lint (NewApi etc. on ported code and desugaring), module graph check. All left to CI.
- Ported tests included beyond the brief's wording ("any ported tests too"); they are plain JUnit so no robolectric.properties was added. If CI shows they need Android classes, add robolectric or returnDefaultValues.
- ModuleGraphRules allows :innertube -> :domain, :core; only :domain is used.

## Deviations / notes
- Added the (empty) AndroidManifest.xml and the ported tests; brief did not list them explicitly.
- Added an SPDX line to build.gradle.kts (Aria convention; Meld's had none).
- InnerTubeX license variant (-only vs -or-later) still unconfirmed: LICENSE on main is the unmodified GPLv3 text with no SPDX or project-specific header; no source header read. Compatible either way; record in docs/spikes if a source header is found.
- PoToken generation is out of scope (not in the library); YouTubeStreamSource resolves without a token, so formats needing one surface as StreamUnavailableException.
- No push, no bd, plan/spec untouched.
