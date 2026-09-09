# StageCore-TabletPlayer

Android tablet player and rehearsal remote apps for StageCore-controlled local media playback, cue execution, live/overlay layers, and legacy OSC compatibility.

## Current priority

The first product target is the tablet app:

```text
StageCore Player
```

It is the Android endpoint worn or mounted on stage. The phone rehearsal remote remains planned, but it should not block the tablet player MVP.

## Tablet runtime model

StageCore owns the full authoritative show cue list. The tablet receives a filtered project-scoped manifest that contains only the tablet-relevant cue list.

A tablet local cue sequence can differ from the StageCore cue sequence:

```text
StageCore cue sequence 10 -> Tablet cue sequence 1
StageCore cue sequence 14 -> Tablet cue sequence 2
```

Execution must use stable IDs and project/snapshot/manifest scope, not the visible sequence numbers alone.

## Local media folder

The app resolves logical media keys to local files under:

```text
/sdcard/TheatreVideos/
```

Starter files:

```text
/sdcard/TheatreVideos/main_01.mp4
/sdcard/TheatreVideos/overlay_01.mp4
/sdcard/TheatreVideos/tablet_manifest.json
```

Official naming:

```text
main_01.mp4 ... main_06.mp4
overlay_01.mp4 ... overlay_99.mp4
```

Live video is represented by a same-network URL in the manifest, such as an IP camera, ESP32-CAM, Mac stream, or phone stream.

## Runtime layers

The tablet player is designed around:

- Main video layer
- Overlay video layer with dissolve in/out
- Live video layer
- Blackout layer

Main media can continue under overlay/live layers.

## Legacy OSC compatibility

Before the official StageCore device pairing/WebSocket path is finished, the app keeps a rehearsal/debug OSC path on UDP port `9000`.

Useful OSC commands:

```text
/theatre/player/identify
/theatre/player/main/prepare 1
/theatre/player/main/play 1
/theatre/player/main/pause
/theatre/player/main/stop
/theatre/player/overlay/play 1
/theatre/player/overlay/hide 1000
/theatre/player/live/url "http://192.168.3.80:81/stream"
/theatre/player/live/show
/theatre/player/live/hide
/theatre/player/cue/prepare 1
/theatre/player/cue/go 1
/theatre/player/blackout
/theatre/player/blackout/clear
```

## StageCore boundary

`StageCoreClient` is the boundary for future official integration:

- `stagecore.device/1`
- `TABLET_PLAYER`
- stable device identity
- `device.hello`
- capabilities
- command lifecycle
- observations
- reconnect without replay

Legacy OSC is compatibility only, not the final authority path.

## Android build

```bash
./gradlew :apps:tablet-player:assembleDebug
```

GitHub Actions builds the debug APK and uploads it as the `stagecore-player-debug-apk` artifact.
