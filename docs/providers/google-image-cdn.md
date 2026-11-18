<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Provider: Google image CDN (artwork)

- Tier: 2 (artwork URLs come from unofficial YouTube Music responses; the CDN itself is Google's)
- Endpoints: `i.ytimg.com`, `*.ytimg.com`, `*.googleusercontent.com`, `*.ggpht.com` (HTTPS on the default port only; no user info)
- Owner: Google (no relationship with Aria)
- Default: on (artwork is part of every browse and player screen)
- Data sent: the image URL, the device IP address and a standard HTTP user agent. The request reveals the device IP address to Google's image CDN.
- When: whenever a screen shows artwork (Home, Album, Artist, Playlist, Search, player)
- Allowlist: `ImageUrlPolicy` (in `:domain`, package `app.aria.domain`) is the only place that decides which image URLs load. It requires https (through `StreamUrlPolicy`), no custom port, no user info and a host on the list above, matched on a dot boundary. The Coil interceptor rejects everything else before any network call, every redirect hop is checked against the same policy before a connection is opened to it (the OkHttp client does not follow redirects itself), and `AriaImage` shows a neutral placeholder. Debug builds log the rejected host only (never the URL); release builds log nothing.
- Known gap: the notification and lock-screen artwork URL is policy-checked before it is handed to Media3, but Media3 loads it itself and follows its own redirects, which Aria cannot gate. Tracked as an open release gate (see [data-flow.md](../data-flow.md)).
- Cache: 100 MB disk cache in `cache/image_cache` (Android never backs up the cache directory; the backup rules also exclude it explicitly and `scripts/check-backup-rules.sh` enforces that). Decoded size is capped at 1 024 px per side and the memory cache is bounded.
- Kill switch: none beyond the YouTube kill switch (see [youtube-music.md](youtube-music.md)).
- Opt-in: not applicable (core feature). Disclosed in [PRIVACY.md](../../PRIVACY.md) and [data-flow.md](../data-flow.md).
