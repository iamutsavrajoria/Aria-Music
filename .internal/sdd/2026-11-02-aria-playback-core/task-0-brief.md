### Task 0: Research spike (no production code)

Stress-test branch 4. Three unknowns sit mid-plan: the cipher-config license, third-party API shapes, and Media3 signatures. Settle them first so a blocker appears before code is written and Tasks 7 to 11 stop guessing. Needs WebFetch (raw GitHub is blocked from the shell); read pages, do not retype from memory.

**Files:**
- Create: `docs/spikes/2026-11-playback-apis.md`
- Modify: `docs/spikes/2026-10-innertubex.md` (record the `-only`/`-or-later` answer in item 1), `docs/release-gates.md` (tick the InnerTubeX line only if the variant is confirmed)

**Acceptance Criteria:**
- `docs/spikes/2026-11-playback-apis.md` contains, each with its source URL and the commit or tag read:
  1. The LICENSE of `ZemerTeam/zemer-cipher` (quote the license name and the relevant lines), and whether InnerTubeX v0.5.2 already embeds a default player config (file and class where it lives). Conclusion: one of "use embedded default", "bundle ZemerTeam file (license compatible)", "BLOCKED: ask the maintainer".
  2. The InnerTubeX v0.5.2 declarations Tasks 9 and 10 use, copied as code: `PlayerConfigRepository` (every member), `RemotePlayerConfigStore` and `YouTubeCipherService` constructors, `YtConfigParserImpl` constructor and `withEmbeddedConfigFallback`, `InnerTubeExtractor` constructor and `extract(...)` parameters, `ExtractedStream` fields, `StreamResolveException` and its kind enum, `TokenProvider`, `TokenProviderCapabilities`, `PoTokenResult`, `PoTokenProviderKind`, `generateClientPlaybackNonce`, and whether the extractor accepts a null or absent token provider.
  3. The Media3 1.10.1 declarations Tasks 7 and 8 use: `MediaSession.Callback.onConnect`, `ControllerInfo.isTrusted`, `MediaSession.isMediaNotificationController`, `MediaSession.ConnectionResult.reject()/AcceptedResultBuilder`, `ResolvingDataSource.Factory` and `Resolver`, `DefaultMediaSourceFactory(DataSource.Factory)`, `ForwardingPlayer` members to override, `MediaMetadata.Builder` setters used, and which of these need `@OptIn(UnstableApi::class)`.
  4. The five Meld `potoken/` files at commit `fe9a666227ff3d6667075e65a4330518a724524b`: path, line count, header text, and the hosts the JavaScript contacts (input for `NavigationPolicy`).
  5. The Meld license text relevant to those files and to `InnerTubeXPlayer.kt`.
- Any "BLOCKED" conclusion is reported to the controller immediately, before Task 1 starts.

- [ ] **Step 1:** Read each source with WebFetch (GitHub file pages or rendered source; for Media3 use `developer.android.com/reference/androidx/media3/...` and the `androidx/media` GitHub tag `1.10.1`). Copy declarations verbatim into the spike doc.
- [ ] **Step 2:** Write the conclusions section (one paragraph per item above plus a "Plan changes needed" list; if a declaration differs from what Tasks 7 to 11 show, list the exact edits).
- [ ] **Step 3:** Commit `docs(spike): playback API and license findings`. No CI wait needed beyond the docs checks (`verify` must stay green).

---

