<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Data flow

This table is derived from the records in [docs/providers/](providers/). A provider without a record is not added to the app. Update this file and [PRIVACY.md](../PRIVACY.md) in the same change as any provider record.

Nothing is sent to the Aria project. The foundation has no telemetry, analytics, advertising or crash-reporting service.

| Provider | Data | When | Opt-in |
|---|---|---|---|
| YouTube Music (via InnerTubeX) | Search text, video IDs, and the device/client identifiers the client library sends | On search and on playback only | No (core feature) |

Not sent anywhere: crash reports (written to `files/crash/` on the device only; the query string of every URL, tokens, cookies and authorization headers are removed first), play history and library data (stored locally in Room).

Android backup and device transfer: the app opts in to the Android backup mechanism but excludes everything private from both cloud backup and device transfer (`backup_rules.xml`, `data_extraction_rules.xml`): the encrypted token store (`secure/`, `secure.xml`), the Room database (`aria.db` and its `-wal`, `-shm`, `-journal` files), the resume preferences (`resume.xml`) and crash reports (`crash/`). So nothing the app stores is copied to Google Drive or to a new device. The planned user-controlled backup and restore of library and settings (spec, sub-project 6) will replace this blanket exclusion for the library; until then a new device starts empty. `scripts/check-backup-rules.sh` fails if any of these exclusions is missing from any section.

Not built yet: the signed-registry client exists as library code but the app does not fetch a registry, so no registry request is made.
