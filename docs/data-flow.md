<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Data flow

This table is derived from the records in [docs/providers/](providers/). A provider without a record is not added to the app. Update this file and [PRIVACY.md](../PRIVACY.md) in the same change as any provider record.

Nothing is sent to the Aria project. The foundation has no telemetry, analytics, advertising or crash-reporting service.

| Provider | Data | When | Opt-in |
|---|---|---|---|
| YouTube Music (via InnerTubeX) | Search text, video IDs, and the device/client identifiers the client library sends | On search and on playback only | No (core feature) |

Not sent anywhere: crash reports (written to the device only, with tokens, cookies and URL secrets removed), play history and library data (stored locally in Room).

Not built yet: the signed-registry client exists as library code but the app does not fetch a registry, so no registry request is made.
