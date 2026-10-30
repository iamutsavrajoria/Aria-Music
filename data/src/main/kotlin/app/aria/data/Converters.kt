// SPDX-License-Identifier: GPL-3.0-only
package app.aria.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** LocalDateTime is stored as UTC epoch millis. (Aria's schema is new, so UTC is chosen explicitly.) */
class Converters {
    @TypeConverter fun fromEpoch(value: Long?): LocalDateTime? =
        value?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) }

    @TypeConverter fun toEpoch(value: LocalDateTime?): Long? =
        value?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
}
