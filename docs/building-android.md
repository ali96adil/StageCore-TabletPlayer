# Building StageCore Player

Open the repository in Android Studio, or build from a shell with Gradle 8.10+ and Android SDK 35 installed.

```bash
gradle :apps:tablet-player:assembleDebug --no-daemon
```

Debug APK output:

```text
apps/tablet-player/build/outputs/apk/debug/tablet-player-debug.apk
```

## Local media folder

Copy local media to the tablet:

```text
/sdcard/TheatreVideos/main_01.mp4
/sdcard/TheatreVideos/overlay_01.mp4
```

The app does not accept absolute paths from StageCore. StageCore sends logical media keys through the manifest and the app resolves them locally.

## Legacy OSC smoke test

Send OSC to tablet IP on UDP port `9000`.

Useful compatibility addresses:

```text
/theatre/all/identify
/theatre/all/play 1
/theatre/all/overlay/play 1
/theatre/all/live/show
/theatre/all/live/hide
/theatre/all/blackout
/theatre/all/cue/prepare 1
/theatre/all/cue/go 1
```
