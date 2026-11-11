# Task 2 report: Queue model and engine core

Status: DONE_WITH_CONCERNS

## Implemented
- playback/src/main/kotlin/app/aria/playback/QueueModel.kt and DefaultPlaybackEngine.kt, verbatim from the brief (five Task 5 stubs left as specified).
- Tests: QueueModelTest.kt, EngineTestKit.kt, EngineQueueTest.kt, verbatim from the brief apart from the fix below.
- playback/build.gradle.kts: added testImplementation(kotlin("test")) (per brief step 2).

## Fix to the brief's test kit
FakeOutput declared `var playWhenReady`, `volume`, `repeatOne`, `skipSilence` as public properties while also overriding setPlayWhenReady/setVolume/setRepeatOne/setSkipSilence. Each property would generate the same JVM setter and fail compilation with "Platform declaration clash". The four properties are now @JvmField (no generated accessors); usage in tests is unchanged. The Harness/FakeOutput index (move) math checked out by hand against all tests.

## Tests written, not run locally
Gradle cannot run in the sandbox. I traced every QueueModelTest and EngineQueueTest case by hand against the code and found no mismatch, but nothing has been executed; CI is the first compile and run.

## Files changed
playback/build.gradle.kts; new: QueueModel.kt, DefaultPlaybackEngine.kt, QueueModelTest.kt, EngineTestKit.kt, EngineQueueTest.kt (all under playback/src).

## Concerns
- kotlin("test") in an Android library module: the JUnit4 variant should be inferred from libs.junit on the test classpath; if CI fails to resolve kotlin.test.Test, add kotlin("test-junit") explicitly.
- `workDispatcher` is unused in this task (warning only; used by later tasks).
