### Task 5: Port the lineage entities and implement `PlayEventRepository`

The shared core tables come from Echo (and Meld for podcast and artist page cache). This task ports them with a recorded license audit and implements the walking skeleton's play-event repository on the ported `song` and `event` tables.

**Files:**
- Create: `scripts/port-scan.sh`, `data/src/main/kotlin/app/aria/data/entities/*` (ported, see Step 3), `data/src/main/kotlin/app/aria/data/PlayEventDao.kt`, `data/src/main/kotlin/app/aria/data/PlayEventRepositoryImpl.kt`, `data/src/test/kotlin/app/aria/data/PlayEventRepositoryTest.kt`, `data/src/test/kotlin/app/aria/data/SchemaContentTest.kt`, `data/expected-schema.txt`
- Modify: `data/src/main/kotlin/app/aria/data/AriaDatabase.kt`, `docs/provenance.csv`

**Interfaces:**
- Consumes: `PlayEventRepository`, `Track`, `TrackId` (Task 2); `AriaDatabase`, `Converters` (Task 4).
- Produces: `PlayEventRepositoryImpl(dao: PlayEventDao) : PlayEventRepository`; ported entities `SongEntity`, `Event` and the rest of the list in Step 3.

**Acceptance Criteria:**
- Every ported file's origin license is recorded in `docs/provenance.csv` before the port; files with no header in the source (e.g. Echo's `Event.kt`, which has none) get `SPDX-License-Identifier: GPL-3.0-only` and a ledger row saying the upstream header was absent.
- `record()` on an unknown track inserts the song row and one event row; a second call adds a second event; `playCount` returns 2.
- `AriaDatabase` is still version 1; schema JSON regenerated and committed.
- No Spotify or Qobuz match tables are ported (superseded by `track_match`).
- `scripts/port-scan.sh data/src/main/kotlin/app/aria/data/entities` reports 0 foreign imports (stop rule: more than 10 distinct foreign imports means stop and ask the maintainer).
- `AriaDatabase` registers the 24 ported and new tables **and** the three views (`SortedSongArtistMap`, `SortedSongAlbumMap`, `PlaylistSongMapPreview`); `SchemaContentTest` pins the exact table and view names to `data/expected-schema.txt`.
- `MetadataExtensions.kt` is deliberately **not** ported: it is the only file in Echo's entities folder that imports Echo's `:metadata` cleaner types (`MetadataCleaner`, `CleanedMetadata`). Revisit it when the metadata module is ported.

- [ ] **Step 1: Audit upstream licenses first**

```bash
E=/home/claude/echomusicapp/echo-music/core/src/main/kotlin/echo/music/iad1tya/db/entities
M=/home/claude/francescograzioso/meld/app/src/main/kotlin/com/metrolist/music/db/entities
for f in $E/*.kt; do printf "%-40s %s\n" "$(basename $f)" "$(head -6 $f | grep -o -i -E 'GPL-3.0|Apache License|MIT' | head -1 || true)"; done
for f in PodcastEntity ArtistPageCache; do printf "%-40s %s\n" "$f" "$(head -6 $M/$f.kt | grep -o -i -E 'GPL-3.0|Apache License|MIT' | head -1)"; done
```
Record the result per file. A file whose header names a license other than GPL-3.0 stops the port of that file until the maintainer decides. Files with no header are covered by the repository-level GPL-3.0 and the ledger notes "no upstream header".

- [ ] **Step 2: Write the ledger rows**

`docs/provenance.csv` (create if absent; header first line):
```csv
path,origin_repo,origin_commit,origin_license,kind,notes
domain,,,,original,TrackId and source interfaces written for Aria
data/src/main/kotlin/app/aria/data/entities,EchoMusicApp/Echo-Music,b04d7507b33c0671b3a23d2658b24a376add4cc7,GPL-3.0,adapted,Entities ported from core/db/entities; many files had no upstream header; package renamed; ids are TrackId strings
```
Add one row per additional origin (Meld `PodcastEntity`, `ArtistPageCache` at `fe9a666227ff3d6667075e65a4330518a724524b`).

- [ ] **Step 3: Port the entities**

Entities to port from Echo `core/src/main/kotlin/echo/music/iad1tya/db/entities/` (current names): `SongEntity`, `Song`, `AlbumEntity`, `Album`, `AlbumWithSongs`, `AlbumArtistMap`, `ArtistEntity`, `Artist`, `SongArtistMap`, `SongAlbumMap`, `SortedSongArtistMap`, `SortedSongAlbumMap`, `PlaylistEntity`, `Playlist`, `PlaylistSong`, `PlaylistSongMap`, `PlaylistSongMapPreview`, `LyricsEntity`, `FormatEntity`, `PlayCountEntity`, `Event`, `EventWithSong`, `RelatedSongMap`, `SearchHistory`, `SpeedDialItem`, `SetVideoIdEntity`, `RecognitionHistory`, `LocalItem`, `SongWithStats`, `BeatInfoEntity`, `RecommendationExclusionEntity`, `SongPlayStatsEntity`, `TasteProfileEntity`. From Meld `app/src/main/kotlin/com/metrolist/music/db/entities/`: `PodcastEntity`, `ArtistPageCache`. **Do not port** `SpotifyMatchEntity` or `QobuzMatchEntity` (replaced by `track_match`).

```bash
DEST=/home/claude/aria/data/src/main/kotlin/app/aria/data/entities
for n in SongEntity Song AlbumEntity Album AlbumWithSongs AlbumArtistMap ArtistEntity Artist SongArtistMap SongAlbumMap SortedSongArtistMap SortedSongAlbumMap PlaylistEntity Playlist PlaylistSong PlaylistSongMap PlaylistSongMapPreview LyricsEntity FormatEntity PlayCountEntity Event EventWithSong RelatedSongMap SearchHistory SpeedDialItem SetVideoIdEntity RecognitionHistory LocalItem SongWithStats BeatInfoEntity RecommendationExclusionEntity SongPlayStatsEntity TasteProfileEntity; do
  cp "$E/$n.kt" "$DEST/$n.kt"
done
cp $M/PodcastEntity.kt $M/ArtistPageCache.kt $DEST/
sed -i 's/^package echo\.music\.iad1tya\.db\.entities/package app.aria.data.entities/; s/^package com\.metrolist\.music\.db\.entities/package app.aria.data.entities/' $DEST/*.kt
sed -i 's/echo\.music\.iad1tya\.db\.entities/app.aria.data.entities/g; s/com\.metrolist\.music\.db\.entities/app.aria.data.entities/g' $DEST/*.kt
# SPDX header on every file that does not have a license header already
for f in $DEST/*.kt; do grep -q -E "SPDX-License-Identifier|Licensed under" "$f" || sed -i '1i // SPDX-License-Identifier: GPL-3.0-only' "$f"; done
grep -rn "androidx.compose" $DEST | head
```
Expected: the final grep may show `androidx.compose.runtime.Immutable` imports (Echo's `Event` uses `@Immutable`). Either add `implementation(libs.compose.runtime)` to `:data` or delete the annotation and import; choose the dependency-free option (delete both). Then fix any remaining unresolved references by reading the compiler errors; entities referring to types outside the ported set (for example `Artist` extension helpers) are ported with the same procedure or removed if only used by Echo's UI.

- [ ] **Step 3b: Scan for foreign imports (stop rule)**

Create `scripts/port-scan.sh` (used again in Tasks 7 and 8):
```bash
#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Lists imports in ported code that still point into the source projects, and fails above a threshold.
# usage: scripts/port-scan.sh <dir> [max-distinct=10]
set -euo pipefail
dir=${1:?usage: port-scan.sh <dir> [max-distinct]}
max=${2:-10}
foreign='^(echo\.music|com\.music\.echo|com\.metrolist\.(music|spotify|innertube\b))'
found=$(grep -rhoE '^import [A-Za-z0-9_.]+' "$dir" --include=*.kt | sed 's/^import //' | grep -E "$foreign" | sort -u || true)
count=$(printf '%s' "$found" | grep -c . || true)
[ -n "$found" ] && printf '%s\n' "$found"
echo "distinct foreign imports: $count (limit $max)"
if [ "$count" -gt "$max" ]; then echo "STOP: scope is larger than planned; report to the maintainer" >&2; exit 1; fi
```
Run: `chmod +x scripts/port-scan.sh && scripts/port-scan.sh data/src/main/kotlin/app/aria/data/entities`
Expected: `distinct foreign imports: 0 (limit 10)`. (Dry run on the planned file set against Echo at the pinned commit gave 0; the only file with foreign imports, `MetadataExtensions.kt`, is excluded.) If the count is between 1 and 10, resolve each import by porting the file it names (add a ledger row) or removing the usage. If it is above 10, **stop and report to the maintainer**.

- [ ] **Step 4: Register entities in `AriaDatabase`**

Replace the `@Database` line with the full entity list (ported names above plus `TrackMatchEntity`), keeping `version = 1`, and add DAOs as needed:
```kotlin
@Database(
    entities = [
        TrackMatchEntity::class, SongEntity::class, Event::class, AlbumEntity::class, ArtistEntity::class,
        PlaylistEntity::class, PlaylistSongMap::class, SongArtistMap::class, SongAlbumMap::class,
        AlbumArtistMap::class, LyricsEntity::class, FormatEntity::class, PlayCountEntity::class,
        RelatedSongMap::class, SearchHistory::class, SpeedDialItem::class, SetVideoIdEntity::class,
        RecognitionHistory::class, BeatInfoEntity::class, RecommendationExclusionEntity::class,
        SongPlayStatsEntity::class, TasteProfileEntity::class, PodcastEntity::class, ArtistPageCache::class,
    ],
    views = [SortedSongArtistMap::class, SortedSongAlbumMap::class, PlaylistSongMapPreview::class],
    version = 1,
    exportSchema = true,
)
```
Add `abstract fun playEventDao(): PlayEventDao`. The three views are registered explicitly because Room does not complain about a view that is simply left out; queries that need them would only fail later.

- [ ] **Step 4b: Pin the schema contents**

`data/src/test/kotlin/app/aria/data/SchemaContentTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Catches silent omissions: a table or view left out of @Database does not fail compilation. */
@RunWith(RobolectricTestRunner::class)
class SchemaContentTest {
    private fun names(): Set<String> {
        val db = JSONObject(File("schemas/app.aria.data.AriaDatabase/1.json").readText()).getJSONObject("database")
        val entities = db.getJSONArray("entities")
        val tables = (0 until entities.length()).map { "table:" + entities.getJSONObject(it).getString("tableName") }
        val viewArray = db.optJSONArray("views")
        val views = if (viewArray == null) emptyList() else
            (0 until viewArray.length()).map { "view:" + viewArray.getJSONObject(it).getString("viewName") }
        return (tables + views).toSet()
    }

    @Test fun tablesAndViewsMatchTheGoldenFile() {
        val expected = File("expected-schema.txt").readLines().filter { it.isNotBlank() }.toSet()
        assertEquals(expected, names())
    }

    @Test fun hasTwentyFourTablesAndThreeViews() {
        val n = names()
        assertEquals(24, n.count { it.startsWith("table:") })
        assertEquals(3, n.count { it.startsWith("view:") })
    }

    @Test fun usesTheGenericMatchTableOnly() {
        val n = names()
        assertTrue("table:track_match" in n)
        assertFalse(n.any { it.contains("spotify", ignoreCase = true) || it.contains("qobuz", ignoreCase = true) })
    }
}
```
Run: `./gradlew :data:testDebugUnitTest --tests '*SchemaContentTest'`
Expected: FAIL, because `data/expected-schema.txt` does not exist yet (the build has just regenerated `schemas/.../1.json`).

Generate the golden file from the exported schema, then **read it against the port list** (21 Echo tables, `PodcastEntity`, `ArtistPageCache`, `track_match`, plus the 3 views); do not accept it unread:
```bash
jq -r '.database | ((.entities[].tableName | "table:" + .), ((.views // [])[].viewName | "view:" + .))' \
  data/schemas/app.aria.data.AriaDatabase/1.json | sort > data/expected-schema.txt
cat data/expected-schema.txt
```
If the counts are not 24 and 3, check `grep -l "^@Entity" data/src/main/kotlin/app/aria/data/entities/*.kt` before changing the test; adjust the numbers only if an entity is legitimately absent from the port, and record why in the commit message. Re-run the test: PASS, 3 tests.

- [ ] **Step 5: Write the failing repository test**

`data/src/test/kotlin/app/aria/data/PlayEventRepositoryTest.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aria.domain.Track
import app.aria.domain.TrackId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayEventRepositoryTest {
    private lateinit var db: AriaDatabase
    private lateinit var repo: PlayEventRepositoryImpl
    private val track = Track(TrackId.youtube("dQw4w9WgXcQ"), "Song", listOf("Artist"), 213_000L, null)

    @Before fun open() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AriaDatabase::class.java).allowMainThreadQueries().build()
        repo = PlayEventRepositoryImpl(db.playEventDao())
    }

    @After fun close() = db.close()

    @Test fun unknownTrackCountsZero() = runBlocking {
        assertEquals(0, repo.playCount(track.id))
    }

    @Test fun recordInsertsSongAndEvents() = runBlocking {
        repo.record(track, playedAtEpochMs = 1_000L, playTimeMs = 30_000L)
        repo.record(track, playedAtEpochMs = 2_000L, playTimeMs = 45_000L)
        assertEquals(2, repo.playCount(track.id))
    }
}
```

- [ ] **Step 6: Run to verify failure**

Run: `./gradlew :data:testDebugUnitTest --tests '*PlayEventRepositoryTest'`
Expected: FAIL, unresolved `PlayEventRepositoryImpl` / `playEventDao`.

- [ ] **Step 7: Implement DAO and repository**

`data/src/main/kotlin/app/aria/data/PlayEventDao.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.aria.data.entities.Event
import app.aria.data.entities.SongEntity

@Dao
abstract class PlayEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSong(song: SongEntity)

    @Insert
    protected abstract suspend fun insertEvent(event: Event)

    @Query("SELECT COUNT(*) FROM event WHERE songId = :songId")
    abstract suspend fun countEvents(songId: String): Int

    @Transaction
    open suspend fun record(song: SongEntity, event: Event) {
        insertSong(song)
        insertEvent(event)
    }
}
```

`data/src/main/kotlin/app/aria/data/PlayEventRepositoryImpl.kt`:
```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.data.entities.Event
import app.aria.data.entities.SongEntity
import app.aria.domain.PlayEventRepository
import app.aria.domain.Track
import app.aria.domain.TrackId
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class PlayEventRepositoryImpl(private val dao: PlayEventDao) : PlayEventRepository {
    override suspend fun record(track: Track, playedAtEpochMs: Long, playTimeMs: Long) {
        val song = SongEntity(
            id = track.id.raw,
            title = track.title,
            duration = track.durationMs?.let { (it / 1000).toInt() } ?: -1,
            thumbnailUrl = track.thumbnailUrl,
        )
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(playedAtEpochMs), ZoneOffset.UTC)
        dao.record(song, Event(songId = track.id.raw, timestamp = at, playTime = playTimeMs))
    }

    override suspend fun playCount(id: TrackId): Int = dao.countEvents(id.raw)
}
```

- [ ] **Step 8: Run to verify pass, check ledger, commit**

Run: `./gradlew :data:testDebugUnitTest`
Expected: PASS (all data tests). `git status` shows an updated `data/schemas/.../1.json`.

```bash
git add -A && git commit -m "feat(data): port lineage entities and add PlayEventRepository (provenance recorded)"
```

---

