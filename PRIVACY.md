<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Privacy

Aria has no telemetry, analytics, advertising or crash-reporting service. Nothing is sent to the Aria project.

When you search or play music, the app contacts YouTube Music to do it. That request contains your search text or the video ID, and the identifiers the YouTube client library normally sends. Google receives these. See [docs/data-flow.md](docs/data-flow.md) for the full list, which is derived from the provider records in [docs/providers/](docs/providers/).

If the app crashes it writes a report to a file on your device, and nothing is sent anywhere. The in-app viewer for that report is not built yet, so for now the file is not shown in the app. Tokens, cookies, authorization headers and the query string of every URL are removed before the report is written.

Android backup and device transfer do not include Aria's data: the library database, playback position, saved sign-in tokens and crash reports are all excluded, so none of it is copied to Google's cloud backup or to a new phone. A user-controlled backup and restore is planned and will be opt-in.

Aria is not affiliated with Google or YouTube.
