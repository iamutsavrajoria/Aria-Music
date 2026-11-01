<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Privacy

Aria has no telemetry, analytics, advertising or crash-reporting service. Nothing is sent to the Aria project.

When you search or play music, the app contacts YouTube Music to do it. That request contains your search text or the video ID, and the identifiers the YouTube client library normally sends. Google receives these. See [docs/data-flow.md](docs/data-flow.md) for the full list, which is derived from the provider records in [docs/providers/](docs/providers/).

If the app crashes it writes a report to your device. It is shown only on your device, and you decide whether to copy it into a GitHub issue. Tokens, cookies and URL secrets are removed before it is shown.

Aria is not affiliated with Google or YouTube.
