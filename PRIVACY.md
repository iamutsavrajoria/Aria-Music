<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Privacy

Aria has no telemetry, analytics, advertising or crash-reporting service. Nothing is sent to the Aria project.

When you search or play music, the app contacts YouTube Music to do it. That request contains your search text or the video ID, and the identifiers the YouTube client library normally sends. Google receives these. While a queue is playing and the endless radio toggle is on (it is on by default; you can turn it off on the player screen), the app also asks YouTube Music for tracks related to the current one, which sends that video ID. To play some streams the app also asks Google (`www.youtube.com`) for a BotGuard attestation token, which sends your YouTube visitor data and a challenge request; the token is computed on your device in a hidden, locked-down web view and no Aria server is involved. See [docs/data-flow.md](docs/data-flow.md) for the full list, which is derived from the provider records in [docs/providers/](docs/providers/).

The text you type in the search box is sent to YouTube Music for suggestions as you type, and Home and the album, artist and playlist pages request your region's feed from YouTube Music.

Artwork (album covers, thumbnails) is loaded directly from Google's image servers, which see your IP address. Images are cached on your device (100 MB cap, not included in Android backup).

If the app crashes it writes a report to a file on your device, and nothing is sent anywhere. The in-app viewer for that report is not built yet, so for now the file is not shown in the app. Tokens, cookies, authorization headers and the query string of every URL are removed before the report is written.

Android backup and device transfer do not include Aria's data: the library database, playback position, saved sign-in tokens and crash reports are all excluded, so none of it is copied to Google's cloud backup or to a new phone. A user-controlled backup and restore is planned and will be opt-in.

Aria is not affiliated with Google or YouTube.
