# Task 1 report: Domain contracts

Status: DONE

Implemented (verbatim from brief):
- domain/build.gradle.kts: added api(libs.coroutines.core) (alias exists in libs.versions.toml).
- Sources.kt: ResolvedStream.loudnessDb (default null), StreamSource.markRejected default no-op, RadioSource, NetworkMonitor.
- New Playback.kt and QueueStore.kt with all types/interfaces from the brief.
- New PlaybackContractsTest.kt (2 tests, as in the brief).

Tests: written, NOT run locally (no Gradle in sandbox). CI is the only verification.

Files: domain/build.gradle.kts, domain/src/main/kotlin/app/aria/domain/{Sources,Playback,QueueStore}.kt, domain/src/test/kotlin/app/aria/domain/PlaybackContractsTest.kt

Checks: no name clashes with existing code; existing StreamSource users (app AppModule, playback PlayerControllerTest anonymous object) unaffected by default method / default param. Skipped the TDD red step (cannot push/run).

Concerns: none. Commit 0142e1c.
