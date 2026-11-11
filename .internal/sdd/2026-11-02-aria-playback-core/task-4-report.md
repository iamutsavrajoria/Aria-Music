# Task 4 report: Engine history and resume

Status: DONE_WITH_CONCERNS (tests written, NOT run; sandbox cannot run Gradle)

## Implemented (DefaultPlaybackEngine.kt)
- Channel import; fields listenedMs/recorded/sinceSaveMs; conflated structureSaves/positionSaves channels with single-writer init collectors.
- onTick counts listening only while status is Playing; records once at 30 s; saves position every 15 s.
- recordPlay, savePosition, snapshot, suspend restore() exactly per brief.
- onCurrentChanged resets counters and saves position (non-empty queue); onStructureChanged sends snapshot.
- pause(), onPlayWhenReadyChanged(false) and detach() save position. Constants HISTORY_THRESHOLD_MS, POSITION_SAVE_MS.

## Tests written, not run
EngineHistoryResumeTest.kt: all 7 brief tests verbatim.

## Files changed
- playback/src/main/kotlin/app/aria/playback/DefaultPlaybackEngine.kt
- playback/src/test/kotlin/app/aria/playback/EngineHistoryResumeTest.kt

## Concerns
- Small deviation: savePosition() itself returns early (after resetting sinceSaveMs) when the queue is empty, so pause()/detach() on an empty queue never write index -1. Brief only guarded the onCurrentChanged call.
- Unverified by compilation; coroutine-test timing (tick at exactly 15 s / 30 s) reasoned by hand only.
