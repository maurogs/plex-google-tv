# PlexLite

A lightweight native Plex client for Google TV / Android TV (Kotlin + Jetpack Compose for TV + Media3/ExoPlayer).

## How it differs from the official app

- **Native UI** (Compose, built with R8), no web view.
- **Real direct play**: it tells the server about every video codec the device decodes in hardware
  (detected at startup) and every audio format ExoPlayer + FFmpeg can decode
  (AAC, AC3, E-AC3, DTS, TrueHD, FLAC, Opus…), plus SRT/ASS/PGS/VobSub subtitles. The server stops
  transcoding for no reason.
- **Fast startup**: no upfront speed test; playback starts as soon as ~1.5 s is buffered and keeps loading
  up to 2 minutes ahead.
- **Automatic recovery**: if direct play fails mid-playback, it switches to HLS transcoding from the same
  position.
- **Server connection**: all direct connections are probed in parallel and the best one wins; the Plex relay
  (~2 Mbps) is only a last resort, and when used it forces a low quality.
- Skip intro / credits (OK button), auto-play next episode, resume, progress synced with Plex,
  external subtitles (.srt) in direct play, audio and subtitle track selection.

## Build

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleRelease
```

Output APK: `app/build/outputs/apk/release/app-release.apk` (signed with the debug key, for sideloading).

Always install the `release` build on the TV: Compose `debug` builds are noticeably slower.

## Install on the TV

1. On the TV: **Settings → System → About → OS build**, press it 7 times to enable developer options.
2. **Settings → System → Developer options**: enable **USB debugging** and **Wireless debugging**.
3. Inside *Wireless debugging* → *Pair device with pairing code*: you'll see an IP:port and a code.

```sh
ADB=/opt/homebrew/share/android-commandlinetools/platform-tools/adb
$ADB pair TV_IP:PAIRING_PORT        # enter the code
$ADB connect TV_IP:DEBUG_PORT       # the port shown on the main Wireless debugging screen
$ADB install -r app/build/outputs/apk/release/app-release.apk
```

The app shows up as **PlexLite** in the apps row.

To check logs if something goes wrong: `$ADB logcat | grep -iE 'plexlite|exoplayer|media3'`.
