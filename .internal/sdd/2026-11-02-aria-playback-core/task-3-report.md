# Task 3 report

Status: DONE

Implemented (DefaultPlaybackEngine.kt, exactly per brief): streamFor with URL cache (60 s expiry margin), 20 s bounded resolve
(IOException on timeout), shared in-flight resolve via ConcurrentHashMap<TrackId, Deferred>, prefetch of current+next on track change,
classified onError (Permanent skip; Http 403/410 one refresh then transient; Io backoff 1/2/4 s then skip; offline WaitingForNetwork
and retry on reconnect), 3-consecutive-failure stop (Error status + Stopped event), reset on ready-while-playing and in play() error branch.
Companion constants added.

Tests written (not run; sandbox cannot run Gradle): EngineErrorsTest.kt with all 10 brief tests, verbatim plus the three imports.

Files changed: playback/src/main/kotlin/app/aria/playback/DefaultPlaybackEngine.kt; playback/src/test/kotlin/app/aria/playback/EngineErrorsTest.kt.

Concerns: none known; only mentally compiled. No test expectations altered.
