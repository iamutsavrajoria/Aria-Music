<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Manual device checklist

Run this on a real device with a debug build (see [Debug APK](#debug-apk)) before the PoToken gate in [release-gates.md](release-gates.md) is closed. CI cannot cover these: the PoToken WebView and real YouTube playback do not run in unit tests or on the emulator, and the nightly smoke test runs without a PoToken.

Start each item from a signed-out app with a search result queued (search, then "Add" or tap a result so that something plays). Tick an item only when the expected result is seen.

## Playback

- [ ] **Play with the screen off for five minutes.** Start a track, turn the screen off, wait five minutes. Expected: audio keeps playing without gaps, and playback moves to the next queue item when a track ends.
- [ ] **Unplug headphones.** Play through wired headphones, unplug them. Expected: playback pauses and does not resume on its own.
- [ ] **Bluetooth disconnect and reconnect.** Play through a Bluetooth speaker or headset, switch it off, then switch it on again. Expected: playback pauses on disconnect (no audio from the phone speaker) and stays paused after reconnect until you press play.
- [ ] **Receive a call.** Play a track, receive a phone call, then end it. Expected: playback pauses during the call and returns afterwards if the call was short, or stays paused if the system did not hand focus back; no crash either way.
- [ ] **Audio focus loss and return from another app.** Play a track, then start audio in another app (for example a video). Expected: Aria pauses or lowers its volume. Stop the other app. Expected: Aria resumes if it was ducked, or stays paused if it was paused by a longer loss; pressing play works.
- [ ] **Airplane mode, then restore.** Play a track, turn on airplane mode. Expected: playback continues on already buffered audio, then stops with an error or a wait for the network, with no crash and no skipping through the whole queue. Turn airplane mode off. Expected: playback picks up again, or pressing play resumes it.

## Process and service

- [ ] **Force-stop and relaunch to resume.** Play a track for a short while, force-stop the app from system settings, open it again. Expected: the queue is back with the same current track and the position within about 15 seconds of where it stopped; playback does not start by itself.
- [ ] **Process recreation with the queue restored.** Queue three or more tracks, move to the second, put the app in the background, and end the process (for example `adb shell am kill <application id>`, or "Don't keep activities" plus a memory-heavy app). Open Aria again. Expected: same queue, same current item, shuffle and repeat settings kept.
- [ ] **Kill the service from the task manager and press play.** Play a track, swipe Aria away from the recents list or stop it from the running-apps list, then press play on the notification or in the app. Expected: the service starts again and the track plays; no crash.

## Notification and lock screen

- [ ] **Notification controls (play/pause/next/previous).** Play a track, pull down the notification shade. Expected: a media notification with the track title and artist; play/pause, next and previous each act on the queue.
- [ ] **Lock-screen controls.** Lock the device during playback. Expected: the lock screen shows the same controls and they work.

## Queue behaviour

- [ ] **Sleep timer.** Set "Sleep 15 min" (or "End of track") and let it run. Expected: playback fades and pauses when the timer ends; "Sleep off" cancels it.
- [ ] **Shuffle and repeat.** Queue five tracks. Turn shuffle on, then off. Expected: the order changes with shuffle on and the original order returns with shuffle off, with the current track unchanged. Cycle repeat through off, all and one. Expected: "all" wraps from the last track to the first, "one" repeats the current track.
- [ ] **Endless radio.** Queue one track with "Endless radio" on and repeat off, then play to the end. Expected: related tracks are appended and playback continues.

## Browse parsers (live)

- [ ] **Home.** Home shows YouTube Music shelves and at least one tile of each kind (song, album, artist, playlist) opens.
- [ ] **Detail pages.** An Album, an Artist and a Playlist page open from search and play.
- [ ] **Search suggestions.** Suggestions appear while typing.
- [ ] **Offline.** Airplane mode shows the offline state and the cached Home still shows.

## Debug APK

The `debug-apk` workflow builds `foss` and `gms` debug APKs and is run by hand (Actions tab, "Run workflow"). The artifact is `aria-debug-<commit sha>`, kept for one day.

While the repository is public, anyone signed in to GitHub can download that artifact, so treat the APK as public (it contains no secrets). Download it straight away, then delete the run: `gh run delete <run id>` or the Actions UI. Install the `foss` APK unless you need the `gms` flavor; install only on your own devices. A debug build is not a release and must not be shared (see [release-gates.md](release-gates.md)).
