# Task 14 report
Status: DONE (not pushed; sandbox cannot run Gradle, so the smoke-test Kotlin is mentally compiled only).

Implemented: debug-apk.yml (workflow_dispatch only, foss+gms debug, artifact aria-debug-<sha>, retention 1 day, no release, SHAs reused); ci.yml foreign-host grep; smoke test extended inside the single @Test (https + expiry per stream, radio related() for first id, success and non-empty); docs/manual-device-checklist.md (brief items plus Bluetooth, focus, process recreation, notification and lock-screen controls, each with expected result, plus debug-APK handling); provider records (extractor, cipher config disabled/staged, radio call); data-flow radio row; PRIVACY radio sentence; release-gates (public-build gate, PoToken gate reworded and left open, date).
ADR-0002 item 3 and spec section 7 item 2 were already corrected; fixed the remaining "private Actions artifacts" wording in the spec stress-test table.

Checks run: yaml.safe_load on both workflows; check-actions-pinned, check-backup-rules, check-no-destructive-migration, check-webview-safety, check-provenance all pass; foreign-host grep empty; TODO/PlaybackHost/PlayerController grep empty.

Deviations: smoke test resolves each id once and reuses results (no second resolve). Checklist expected results written from code read (headphone-noisy handling, radio only when repeat off, 5-min failed-client memory), not from device runs.

Concerns: smoke radio call needs live network and may be blocked on datacenter IPs like the resolve. Smoke-test expiry assertion is always true because the adapter defaults a missing expiry (5 min). Maintainer must download the debug-apk artifact immediately and delete the run (gh run delete <id>); Step 5 (dispatch, CI green, final review) is the controller's. PoToken gate stays open.
