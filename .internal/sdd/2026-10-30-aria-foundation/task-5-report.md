# Task 5 report: lineage entities and PlayEventRepository

Status: DONE_WITH_CONCERNS. Commit (local, not pushed): see `git log -1` on main (c9262db...).

## What was done
- License audit: no Echo entity file has any header (all "no upstream header" -> SPDX GPL-3.0-only added). Meld PodcastEntity has "Licensed under GPL-3.0" header (kept, SPDX line added). The audit one-liner's `-i MIT` grep gives false hits ("mIt" inside `ArtistItem`/`SpeedDialItem` imports); manually verified none is MIT/Apache.
- Ported 33 Echo files + Meld PodcastEntity into data/.../entities (plus new RandomLetters.kt). TrackMatchEntity already existed.
- AriaDatabase: 23 tables + 3 views, version 1; `playEventDao()` added. PlayEventDao, PlayEventRepositoryImpl, PlayEventRepositoryTest, SchemaContentTest, data/expected-schema.txt, scripts/port-scan.sh (executable), 4 ledger rows.

## Deviations (important)
1. **Plan's scan regex missed Echo's `com.music.innertube`.** The plan's claim "dry run gave 0" is wrong: the plan regex only matched `echo.music|com.music.echo|com.metrolist.*`. I added `com\.music\.(echo|innertube)` to port-scan.sh. True count on the ported set was 7 distinct (YouTube + 6 models; in SongEntity, AlbumEntity, ArtistEntity, PlaylistEntity, SpeedDialItem) -> within the 1..10 band, resolved by removing usage; now 0. (Including Meld's ArtistPageCache it would have been 15, above the stop limit.)
2. **ArtistPageCache NOT ported, so 23 tables not 24.** It has no `@Entity`; it is kotlinx-serialization DTOs plus converters to/from `com.metrolist.innertube` models (8 imports, needs the serialization plugin). Registering it in `@Database` would fail compilation. Belongs with the :innertube port (Task 8). SchemaContentTest asserts 23 tables (`hasTwentyThreeTablesAndThreeViews`); the plan allows this adjustment with the reason in the commit message (done).
3. Entity edits (marked with "Aria change" comments): removed `@Immutable`+import everywhere; removed YouTube network calls from `SongEntity.toggleLike/toggleLibrary` (also dropped the `syncToYouTube` param), `AlbumEntity/ArtistEntity/PlaylistEntity.toggleLike` (now equal to `localToggleLike`); removed `SpeedDialItem.toYTItem/fromYTItem`; removed `PlaylistEntity.shareLink` (hard-coded Echo share domain, violates "no other project's server"); commons-lang3 `RandomStringUtils` replaced by internal `randomLetters()`. No new dependencies added to :data.
4. expected-schema.txt was derived from the `tableName`/`viewName` annotations in the entities (LC_ALL=C sorted), not from 1.json (which is stale), so it is an independent check. View names are snake_case (`sorted_song_artist_map`, `sorted_song_album_map`, `playlist_song_map_preview`).

## Could not run (no Gradle)
Compilation of everything; Room KSP processing (watch for Room complaints on the ported POJOs/relations); all tests.

## Controller must do
- `data/schemas/app.aria.data.AriaDatabase/1.json` is STALE (only track_match). Regenerate from CI (build produces it) and commit. Until then `SchemaContentTest` (3 tests) and `MigrationHarnessTest` will fail (the harness creates v1 from the old JSON and identity hash mismatches).
- After regeneration, cross-check against data/expected-schema.txt (23 tables, 3 views); `jq -r '.database | ((.entities[].tableName | "table:" + .), ((.views // [])[].viewName | "view:" + .))' 1.json | LC_ALL=C sort | diff - data/expected-schema.txt`.
- Whether the above stop-rule interpretation (deviation 1) is acceptable; reversible since nothing is pushed.

## Verified locally
`scripts/port-scan.sh data/src/main/kotlin/app/aria/data/entities` -> `distinct foreign imports: 0 (limit 10)`; also 0 on an empty dir. Ledger CSV parses (6 columns every row). 23 `@Entity` files + 3 `@DatabaseView` files match the registered lists.
