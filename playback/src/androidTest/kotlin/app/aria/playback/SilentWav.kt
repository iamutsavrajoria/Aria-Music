// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes a mono 8 kHz 8-bit PCM WAV of silence; no audio asset needs to be committed. */
object SilentWav {
    fun write(dir: File, seconds: Int = 6): File {
        val rate = 8000; val data = ByteArray(rate * seconds) { 0x80.toByte() }
        val b = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVE".toByteArray()).put("fmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate).putShort(1).putShort(8)
            .put("data".toByteArray()).putInt(data.size).put(data)
        return File(dir, "silence.wav").also { it.writeBytes(b.array()) }
    }
}
