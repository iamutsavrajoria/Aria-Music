<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Data flow

This table is derived from the records in [docs/providers/](providers/). A provider without a record is not added to the app. Update this file and [PRIVACY.md](../PRIVACY.md) in the same change as any provider record.

Nothing is sent to the Aria project. The foundation has no telemetry, analytics, advertising or crash-reporting service.

| Provider | Data | When | Opt-in |
|---|---|---|---|
| YouTube Music (via InnerTubeX) | Search text, video IDs, and the device/client identifiers the client library sends | On search and on playback only | No (core feature) |
| YouTube Music, radio (same provider) | The video ID of the track playing, sent as a `next` request for related tracks | When endless radio is on, repeat is off and the queue is running out. The toggle is on by default, shown on the player screen and not saved between runs | Can be turned off on the player screen |
| YouTube BotGuard challenge (PoToken generator, same provider) | Visitor data and a BotGuard challenge request, sent from the device to `www.youtube.com` (Google); the token is minted by a hidden, confined WebView on the device. No Aria server is involved | On first playback and again when the token expires (the integrity token lives for hours; Aria keeps the last token pair only in memory) | No (needed to play streams that require a PoToken) |

Not sent anywhere: crash reports (written to `files/crash/` on the device only; the query string of every URL, tokens, cookies and authorization headers are removed first), play history and library data (stored locally in Room).

Android backup and device transfer: the app opts in to the Android backup mechanism but excludes everything private from both cloud backup and device transfer (`backup_rules.xml`, `data_extraction_rules.xml`): the encrypted token store (`secure/`, `secure.xml`), the Room database (`aria.db` and its `-wal`, `-shm`, `-journal` files), the resume preferences (`resume.xml`) and crash reports (`crash/`). So nothing the app stores is copied to Google Drive or to a new device. The planned user-controlled backup and restore of library and settings (spec, sub-project 6) will replace this blanket exclusion for the library; until then a new device starts empty. `scripts/check-backup-rules.sh` fails if any of these exclusions is missing from any section.

Not built yet: the signed-registry client exists as library code but the app does not fetch a registry, so no registry request is made.
