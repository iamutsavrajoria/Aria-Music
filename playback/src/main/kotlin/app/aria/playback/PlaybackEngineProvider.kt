// SPDX-License-Identifier: GPL-3.0-only
package app.aria.playback

/** Implemented by the Application. No DI framework in :playback: the service asks the Application for the engine. */
interface PlaybackEngineProvider {
    val playbackEngine: DefaultPlaybackEngine
    /** Only the emulator test app returns true (it serves a local WAV). Production never plays local URLs. */
    val allowLocalStreams: Boolean get() = false
}
