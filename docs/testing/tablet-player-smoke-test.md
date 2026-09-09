# StageCore Player — Tablet Smoke Test

## Goal

Verify the tablet app can run a project-scoped tablet show locally before the final StageCore WebSocket pairing path is complete.

## Test media folder

On the Android tablet, use:

```text
/sdcard/TheatreVideos/
```

Required starter files:

```text
/sdcard/TheatreVideos/main_01.mp4
/sdcard/TheatreVideos/overlay_01.mp4
/sdcard/TheatreVideos/tablet_manifest.json
```

The app also has a bundled sample manifest, so `tablet_manifest.json` is optional for the first launch.

## Manual launch test

1. Install and open `StageCore Player`.
2. Allow/manage storage access when Android asks for it.
3. Confirm the screen shows:
   - stable tablet device id
   - active manifest source
   - media folder path
   - Tablet Cue -> StageCore Cue mapping
4. Tap `Prepare cue 1`.
5. Tap `GO cue 1`.
6. Confirm `main_01.mp4` plays and loops.
7. Tap `GO cue 2 overlay`.
8. Confirm `overlay_01.mp4` fades above the main video, then hides while main continues.
9. Tap `GO cue 3 live`.
10. Confirm the live URL layer appears if the URL is reachable on the local network.
11. Tap `GO cue 4 blackout`.
12. Confirm blackout covers visible media without crashing the app.
13. Tap `Clear blackout`.

## Show mode

Tap `Show mode` to hide controls/status. Tap the top-left corner five times quickly to bring controls back.

## Legacy OSC test

Send OSC to the tablet IP on UDP port `9000`.

Useful commands:

```text
/theatre/player/identify
/theatre/player/main/prepare 1
/theatre/player/main/play 1
/theatre/player/overlay/play 1
/theatre/player/live/url "http://192.168.3.80:81/stream"
/theatre/player/live/show
/theatre/player/live/hide
/theatre/player/cue/prepare 1
/theatre/player/cue/go 1
/theatre/player/blackout
/theatre/player/blackout/clear
```

## Acceptance

- Missing media reports a visible failure instead of playing the wrong file.
- Tablet cue sequence can differ from StageCore cue sequence.
- The manifest is scoped by `stagecore_project_id`, `runtime_snapshot_id`, and `tablet_manifest_id`.
- Legacy OSC remains a rehearsal/debug path, not the final StageCore authority path.
