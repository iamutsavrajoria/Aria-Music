<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Provider: YouTube Music

- Tier: 2 (unofficial use of a commercial service's endpoints)
- Endpoint: `music.youtube.com` / `youtubei` endpoints, reached through InnerTubeX (see [innertubex.md](innertubex.md))
- Owner: Google (no relationship with Aria)
- Default: on (the app cannot work without it)
- Kill switch: registry key `killSwitches.youtube` (data only). Status: the registry schema and signature verifier exist in `:core`; nothing in the app fetches the registry or reads this key yet, so the switch is not operable today.
- Fallback: none. Planned behaviour is to show "YouTube Music is unavailable" and keep downloaded and local tracks playable; neither the message nor downloads exist in the walking skeleton.
- Data sent: search queries, video IDs, and the device/client identifiers the client library sends
- When: on search and on playback only; no background calls from the foundation
- Opt-in: not applicable (core feature). To be disclosed in first-run text and in [PRIVACY.md](../../PRIVACY.md); first-run text is not built yet, PRIVACY.md covers it for now.
- Known gap: stream resolution may require a PoToken, which neither the library nor the foundation generates. See [innertubex.md](innertubex.md).
