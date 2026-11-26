<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Data flow

This table is derived from the records in [docs/providers/](providers/). A provider without a record is not added to the app. Update this file and [PRIVACY.md](../PRIVACY.md) in the same change as any provider record.

Nothing is sent to the Aria project. The foundation has no telemetry, analytics, advertising or crash-reporting service.

| Provider | Data | When | Opt-in |
|---|---|---|---|
| YouTube Music (via InnerTubeX) | Search text, video IDs, browse IDs and home-feed requests, and the device/client identifiers the client library sends | On search, on browse (Home, Album, Artist and Playlist pages) and on playback only | No (core feature) |
| YouTube Music, radio (same provider) | The video ID of the track playing, sent as a `next` request for related tracks | When endless radio is on, repeat is off and the queue is running out. The toggle is on by default, shown on the player screen; its stored default (the settings store, applied to the engine at app start) is on, and the Settings screen writes it (the legacy player-screen toggle does not write to that store) | Can be turned off in Settings or on the player screen |
| YouTube Music, browse (same provider) | Browse IDs, the home feed request, search text as typed (suggestions) | When Home, an Album/Artist/Playlist page or Search is open | No (core feature) |
| YouTube BotGuard challenge (PoToken generator, same provider) | Visitor data and a BotGuard challenge request, sent from the device to `www.youtube.com` (Google); the token is minted by a hidden, confined WebView on the device. No Aria server is involved | Whenever a track is resolved for playback whose extraction asks for a PoToken, which includes the prefetch of the next track. The token cache holds one entry keyed on the video ID and visitor data, so every newly resolved track creates a hidden WebView and sends two POSTs (`Create`, then `GenerateIT`) to `www.youtube.com`; only a repeat resolve of the same track within the token's lifetime reuses the cached pair (kept in memory only). The BotGuard run adds to resolve time, so a gapless transition to a track that was not prefetched in time can be late by that time | No (needed to play streams that require a PoToken) |
| Google image CDN (artwork) | The image URL; the request reveals the device IP address to Google | Whenever a screen shows artwork | No (core feature) |

Artwork requests are restricted to the Google image CDN hosts by `ImageUrlPolicy` (in `:domain`): in-app images through Coil, where every redirect hop is checked too, and the notification and lock-screen artwork URL handed to Media3. Media3 itself loads that notification artwork and follows its own redirects, which Aria cannot gate; this is tracked as an open release gate.

Not sent anywhere: crash reports (written to `files/crash/` on the device only; the query string of every URL, tokens, cookies and authorization headers are removed first), play history and library data (stored locally in Room).

Android backup and device transfer: the app opts in to the Android backup mechanism but excludes everything private from both cloud backup and device transfer (`backup_rules.xml`, `data_extraction_rules.xml`): the encrypted token store (`secure/`, `secure.xml`), the Room database (`aria.db` and its `-wal`, `-shm`, `-journal` files), the resume preferences (`resume.xml`), the settings store (`datastore/`), the artwork disk cache (`cache/image_cache/`, which Android never backs up anyway) and crash reports (`crash/`). So nothing the app stores is copied to Google Drive or to a new device. The planned user-controlled backup and restore of library and settings (spec, sub-project 6) will replace this blanket exclusion for the library; until then a new device starts empty. `scripts/check-backup-rules.sh` fails if any of these exclusions is missing from any section.

Not built yet: the signed-registry client exists as library code but the app does not fetch a registry, so no registry request is made.
