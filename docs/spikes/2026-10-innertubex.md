# Spike: InnerTubeX license, store policy, PoToken

Date: 2026-10-30. Task 0 of the foundation plan. Findings come from public pages read on that date; nothing was compiled.

## 1. License

- InnerTubeX (`MetrolistGroup/innertubex`) is **GPL-3.0**, shown on its [repository page](https://github.com/MetrolistGroup/innertubex) with a link to its LICENSE file. The page does not say whether the grant is `-only` or `-or-later`.
- Aria is `GPL-3.0-only`. Using a GPL-3.0 library is compatible with that. If the library turns out to be `-only`, it is also compatible; if `-or-later`, Aria's `-only` still holds.
- **Read 2026-11-03** (tag `v0.5.2`, commit `93b650b877530738cbc4d2183b4322b77bb9033e`, details in [2026-11-playback-apis.md](2026-11-playback-apis.md) item 1b): `LICENSE` is the unmodified FSF GPLv3 text; the README badge says "GPL-3.0"; the published POM says "GNU General Public License, version 3"; no source file has a license header or SPDX line, and no "or any later version" notice applies to the library. So the variant is **not declared**; the best-supported reading is `GPL-3.0-only`. Either variant is compatible with Aria's `GPL-3.0-only`. Left open: whether to accept "undeclared, version 3" as the recorded answer for the release gate.
- Pinning: the README says `0.x` releases are experimental and advises pinning exact versions and reading the CHANGELOG before upgrading. Meld pins `0.5.2`; Aria pins the same.

## 2. Store policy on a JitPack-hosted dependency

- **F-Droid** ([Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy)): library binaries must come from source compilation or a trusted Maven repository, and the policy lists JitPack.io among trusted repositories. It adds that being hosted there is not enough; the binary must also be freely licensed. InnerTubeX is GPL-3.0, so it qualifies on both counts.
- **IzzyOnDroid** ([App Inclusion Policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/)): the policy does not mention JitPack or any dependency repository. Its rules are no proprietary components and no tracking or advertising services, which InnerTubeX does not add.
- Conclusion: a JitPack dependency is acceptable for both stores. No git submodule is needed. The Gradle dependency verification added in Task 11 Step 5b pins the artifact checksum, which also covers JitPack's build-on-demand behaviour.
- Residual risk: F-Droid builds from source and rebuilds dependencies it can; a JitPack artifact that cannot be reproduced may draw a reviewer request. Record any such request in `docs/providers/innertubex.md`.

## 3. PoToken

- InnerTubeX does **not** generate PoTokens. Its README mentions only "token providers" as an injectable interface, and Meld's `InnerTubeXPlayer.kt` imports `com.metrolist.innertubex.extraction.TokenProvider` and `PoTokenResult`.
- Meld generates tokens in app code: `app/.../utils/potoken/` has `PoTokenGenerator.kt`, `PoTokenWebView.kt`, `JavaScriptUtil.kt`, `PoTokenResult.kt` and `PoTokenException.kt`, a WebView-based generator.
- Conclusion: Aria needs its own WebView PoToken generator (ported from Meld or Echo, license and ledger row required) wired in as the `TokenProvider`. This belongs to the playback sub-project; the foundation walking skeleton resolves streams without a token and the smoke test shows whether that is enough.

## Decisions

- Task 8 is **not blocked** on license: GPL-3.0 is compatible. It carries one pre-step: confirm the `-only` or `-or-later` variant from the LICENSE file (item 1).
- Task 11 pins InnerTubeX by checksum.
- PoToken generation is recorded as a known gap for sub-project 2.
