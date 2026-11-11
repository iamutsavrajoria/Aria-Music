### Task 6: `QueueStore` in `:data`

**Files:**
- Create: `data/src/main/kotlin/app/aria/data/entities/QueueEntities.kt`, `data/src/main/kotlin/app/aria/data/QueueDao.kt`, `data/src/main/kotlin/app/aria/data/QueueStoreImpl.kt`
- Modify: `data/src/main/kotlin/app/aria/data/AriaDatabase.kt`, `data/schemas/app.aria.data.AriaDatabase/1.json`, `.github/workflows/ci.yml`
- Test: `data/src/test/kotlin/app/aria/data/QueueStoreTest.kt`

**Interfaces:**
- Consumes: `QueueStore`, `SavedQueue`, `SavedPosition`, `RestoredQueue`, `QueueItem`, `Track`, `TrackId`.
- Produces: `class QueueStoreImpl(private val dao: QueueDao) : QueueStore`; `AriaDatabase.queueDao()`.

**Acceptance Criteria:**
- `saveStructure` replaces all rows in one transaction; `savePosition` touches only the position row; `load()` returns null when nothing was saved and otherwise the exact items, original order, shuffle, repeat and next id.
- The committed schema JSON contains the two new tables (CI step "Schema is committed and unchanged" passes).
- Database version stays 1 and there is no migration (schema is not frozen before beta).

- [ ] **Step 1: Failing test:**

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.aria.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueStoreTest {
    private lateinit var db: AriaDatabase
    private lateinit var store: QueueStoreImpl
    private fun item(id: Long, vid: String) =
        QueueItem(id, Track(TrackId.youtube(vid), "Title $id", listOf("A", "B"), 123_000L, "https://t/$id"))

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AriaDatabase::class.java)
            .allowMainThreadQueries().build()
        store = QueueStoreImpl(db.queueDao())
    }
    @After fun close() = db.close()

    @Test fun loadIsNullWhenNothingSaved() = runBlocking { assertNull(store.load()) }

    @Test fun roundTripKeepsEverything() = runBlocking {
        val items = listOf(item(3, "aaaaaaaaaa1"), item(1, "aaaaaaaaaa2"), item(2, "aaaaaaaaaa3"))
        val q = SavedQueue(items, listOf(1, 2, 3), shuffle = true, repeat = RepeatMode.ALL, nextQueueId = 4)
        store.saveStructure(q); store.savePosition(SavedPosition(1, 42_000))
        val r = store.load()!!
        assertEquals(q, r.queue); assertEquals(SavedPosition(1, 42_000), r.position)
    }

    @Test fun structureReplacesOldRows() = runBlocking {
        store.saveStructure(SavedQueue(listOf(item(1, "aaaaaaaaaa1"), item(2, "aaaaaaaaaa2")), listOf(1, 2), false, RepeatMode.OFF, 3))
        store.saveStructure(SavedQueue(listOf(item(2, "aaaaaaaaaa2")), listOf(2), false, RepeatMode.ONE, 3))
        store.savePosition(SavedPosition(0, 0))
        val r = store.load()!!
        assertEquals(listOf(2L), r.queue.items.map { it.queueId }); assertEquals(RepeatMode.ONE, r.queue.repeat)
    }

    @Test fun positionSaveDoesNotTouchStructure() = runBlocking {
        val q = SavedQueue(listOf(item(1, "aaaaaaaaaa1")), listOf(1), false, RepeatMode.OFF, 2)
        store.saveStructure(q); store.savePosition(SavedPosition(0, 1)); store.savePosition(SavedPosition(0, 2))
        assertEquals(q, store.load()!!.queue); assertEquals(2L, store.load()!!.position.positionMs)
    }
}
```

- [ ] **Step 2:** Push; CI fails (`queueDao` missing). Expected: FAIL.

- [ ] **Step 3: Implement.** `QueueEntities.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per queue entry. [position] is play order; [originalPosition] is insertion order. */
@Entity(tableName = "queue_item")
data class QueueItemEntity(
    @PrimaryKey val queueId: Long,
    val position: Int,
    val originalPosition: Int,
    val trackId: String,
    val title: String,
    /** Artists joined with U+001F (unit separator). */
    val artists: String,
    val durationMs: Long?,
    val thumbnailUrl: String?,
)

/** Single row, id always 0. */
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    val shuffle: Boolean,
    val repeat: String,
    val nextQueueId: Long,
    val currentIndex: Int,
    val positionMs: Long,
)
```

`QueueDao.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.*
import app.aria.data.entities.QueueItemEntity
import app.aria.data.entities.QueueStateEntity

@Dao
abstract class QueueDao {
    @Query("DELETE FROM queue_item") protected abstract suspend fun clearItems()
    @Insert protected abstract suspend fun insertItems(items: List<QueueItemEntity>)
    @Upsert protected abstract suspend fun upsertState(state: QueueStateEntity)
    @Query("SELECT * FROM queue_state WHERE id = 0") abstract suspend fun state(): QueueStateEntity?
    @Query("SELECT * FROM queue_item ORDER BY position") abstract suspend fun items(): List<QueueItemEntity>
    @Query("UPDATE queue_state SET currentIndex = :index, positionMs = :positionMs WHERE id = 0")
    protected abstract suspend fun updatePosition(index: Int, positionMs: Long): Int

    @Transaction
    open suspend fun replace(items: List<QueueItemEntity>, state: QueueStateEntity) {
        clearItems(); insertItems(items); upsertState(state)
    }

    /** Position may arrive before the first structure save; keep it so it is not lost. */
    @Transaction
    open suspend fun savePosition(index: Int, positionMs: Long, fallback: QueueStateEntity) {
        if (updatePosition(index, positionMs) == 0) upsertState(fallback)
    }
}
```

`QueueStoreImpl.kt`:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import app.aria.data.entities.QueueItemEntity
import app.aria.data.entities.QueueStateEntity
import app.aria.domain.*

class QueueStoreImpl(private val dao: QueueDao) : QueueStore {
    override suspend fun saveStructure(queue: SavedQueue) {
        val originalPos = queue.originalOrder.withIndex().associate { it.value to it.index }
        val rows = queue.items.mapIndexed { i, it ->
            QueueItemEntity(
                queueId = it.queueId, position = i, originalPosition = originalPos[it.queueId] ?: i,
                trackId = it.track.id.raw, title = it.track.title, artists = it.track.artists.joinToString(SEP),
                durationMs = it.track.durationMs, thumbnailUrl = it.track.thumbnailUrl,
            )
        }
        val old = dao.state()
        dao.replace(rows, QueueStateEntity(0, queue.shuffle, queue.repeat.name, queue.nextQueueId, old?.currentIndex ?: 0, old?.positionMs ?: 0L))
    }

    override suspend fun savePosition(position: SavedPosition) {
        dao.savePosition(position.index, position.positionMs, QueueStateEntity(0, false, RepeatMode.OFF.name, 1L, position.index, position.positionMs))
    }

    override suspend fun load(): RestoredQueue? {
        val state = dao.state() ?: return null
        val rows = dao.items()
        if (rows.isEmpty()) return null
        val items = rows.map {
            QueueItem(it.queueId, Track(TrackId.parse(it.trackId), it.title, if (it.artists.isEmpty()) emptyList() else it.artists.split(SEP), it.durationMs, it.thumbnailUrl))
        }
        val original = rows.sortedBy { it.originalPosition }.map { it.queueId }
        return RestoredQueue(
            SavedQueue(items, original, state.shuffle, RepeatMode.valueOf(state.repeat), state.nextQueueId),
            SavedPosition(state.currentIndex, state.positionMs),
        )
    }

    private companion object { const val SEP = "\u001F" }
}
```

`AriaDatabase.kt`: add `QueueItemEntity::class, QueueStateEntity::class` to `entities`, imports, and `abstract fun queueDao(): QueueDao`; update the KDoc table count (25 tables).

- [ ] **Step 4: Get the schema from CI.** Push. Expect the unit tests green but **"Schema is committed and unchanged" red** (the plugin regenerated `1.json`). Add a permanent diagnostic so the file can be fetched without gzip tricks: in `ci.yml` after the schema step add

```yaml
      - name: Upload schema when it changed
        if: failure()
        uses: actions/upload-artifact@<SHA> # <tag>
        with: { name: room-schema, path: data/schemas, retention-days: 3 }
```

Resolve `<SHA>` for a current `actions/upload-artifact` release with `env -u GH_TOKEN gh api repos/actions/upload-artifact/git/ref/tags/<tag> --jq .object.sha` (dereference annotated tags via `git/tags/<sha>`), keep the `# <tag>` comment (`scripts/check-actions-pinned.sh` requires a full SHA). Push, download the artifact (`env -u GH_TOKEN gh run download <run-id> -n room-schema -D /tmp/schema`), copy `app.aria.data.AriaDatabase/1.json` over `data/schemas/app.aria.data.AriaDatabase/1.json`, verify it contains `queue_item` and `queue_state`, commit. If artifact download is refused by the sandbox, fall back to the foundation method (gzip+base64 in annotations via a temporary script file, not inline YAML).
- [ ] **Step 5:** Push; all jobs green. Expected: PASS. **Commit** `feat(data): Room-backed QueueStore`.

---

