<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Credits

Aria builds on the work of other open-source projects. Per module: what was taken and whether it was copied, adapted, or only inspired. The authoritative per-directory record is `docs/provenance.csv`.

| Origin | What | How |
|---|---|---|
| [Meld](https://github.com/FrancescoGrazioso/Meld) (GPL-3.0) | `:innertube` module | Copied and adapted: Meld's module (86 Kotlin files now: 82 main plus 4 tests, about 9,800 lines) with package renamed to `app.aria.innertube`. It wraps InnerTubeX (`com.metrolist.innertubex`, unchanged); response parsing remains in-tree from Meld/Metrolist. Build file adds `:domain` and test resources; the `adapter/` package (3 files), `MappingTest` and `PlaybackSmokeTest` are written for Aria. Changed after import: the `returnyoutubedislikeapi.com` call and its response model were removed from `getMediaInfo` (an undisclosed third-party endpoint with no provider record); `MediaInfo` no longer has `viewCount`/`like`/`dislike` |
| Meld (GPL-3.0) | `data/.../entities/PodcastEntity.kt` | Adapted: Room entity with upstream header kept, SPDX line added, package renamed, Compose `Immutable` removed |
| [Echo Music](https://github.com/EchoMusicApp/Echo-Music) (GPL-3.0) | `data/.../entities` (33 of Echo's 34 files) | Adapted: Room entities with package renamed, Compose `Immutable` removed, YouTube network calls dropped from `toggleLike`/`toggleLibrary`, `SpeedDialItem` YTItem converters and `PlaylistEntity.shareLink` removed, commons-lang3 replaced by Aria's `RandomLetters.kt`. `MetadataExtensions.kt` deliberately not ported |
| Echo Music (GPL-3.0) | `designsystem/.../GlassEffect.kt` | Adapted: Echo's `liquidGlass` wrapper (Echo took it from the vivimusic project; the `vivimusic Project (C) 2026 Licensed under GPL-3.0` header is kept, with an SPDX line added below it), package renamed, `LocalAppBackdrop` defaults to `emptyBackdrop()` instead of throwing |
| [Kyant0/backdrop](https://github.com/Kyant0/backdrop) v2.0.0 (Apache-2.0) | Backdrop blur, lens and highlight effects (30 files in `designsystem/.../backdrop`) | Adapted: vendored by Echo Music and taken from there with Echo's changes (resolution scale added, shapes support removed); package renamed. Original copyright headers kept; license text in `licenses/Apache-2.0.txt`. Upstream `NOTICE` file not yet checked (gate before first beta) |
| [Metrolist](https://github.com/MetrolistGroup/Metrolist) | Common ancestor of Meld and Echo; also publishes InnerTubeX (GPL-3.0), used as a dependency | Not copied directly; credited because Meld/Echo code and InnerTubeX derive from it |
| Original to Aria | `:domain`, `:core`, `:playback`, `:app`, most of `:data` and `:designsystem` | Written for Aria; no upstream code. See `docs/provenance.csv` rows of kind `original` |

The authoritative per-directory record is `docs/provenance.csv`.
