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

## Clean show-mode launch

1. Install and open `StageCore Player`.
2. The app should open to a clean black/show surface with no debug text covering the stage image.
3. Rotate the tablet and confirm portrait and landscape are both allowed when orientation is set to automatic.
4. Tap the top-left corner five times quickly to show the Arabic settings/control panel.
5. Tap `وضع العرض` or tap the top-left corner five times again to hide the settings/control panel.

## Arabic settings panel

Open the panel with five quick top-left taps and verify:

- `ID التابلت` can be edited and saved.
- `اسم الجهاز` can be edited and saved.
- `عنوان السيرفر` and `البورت` can be edited and saved.
- `اكتشاف تلقائي Bonjour` is available and the `بحث تلقائي` button searches for `_stagecore._tcp.` and `_stagecore-hub._tcp.` services.
- Brightness slider changes screen brightness and saves it.
- Video scale buttons support `Full / ملء`, `Fit / احتواء`, and `Crop / قص`.
- Orientation buttons support automatic, portrait, and landscape.
- `تجهيز مجلد الفيديوات` creates/checks `/sdcard/TheatreVideos/`.
- `فحص ملفات الفيديو` reports available `main_*.mp4`, `overlay_*.mp4`, manifest status, and missing manifest references.
- `فحص الصلاحيات` reports storage access state.
- The interface text is Arabic-first while code/API names remain English.

## StageCore settings-control foundation

The app must advertise that StageCore will be able to control production-critical tablet settings remotely after final pairing is implemented.

Verify the debug/hello output includes controllable settings and capabilities for:

```text
tablet.settings.read
tablet.settings.apply
tablet.settings.reset
tablet.settings.device_id.set
tablet.settings.device_name.set
tablet.settings.server.set
tablet.settings.auto_discover.set
tablet.settings.brightness.set
tablet.settings.video_scale.set
tablet.settings.orientation.set
tablet.settings.show_mode.set
tablet.settings.show_lock.set
tablet.permissions.check
tablet.media.scan
tablet.media.prepare_folder
```

StageCore should later use these capabilities to push settings, request a file scan, prepare `/sdcard/TheatreVideos/`, and reject show start when files are missing.

## Manual launch test

1. Open the hidden settings/control panel with five quick taps in the top-left corner.
2. Allow/manage storage access when Android asks for it.
3. Confirm the panel shows:
   - stable tablet device id
   - editable device name
   - active manifest source
   - media folder path
   - Tablet Cue -> StageCore Cue mapping
4. Tap `Prepare 1`.
5. Tap `GO 1`.
6. Confirm `main_01.mp4` plays and loops.
7. Tap `Overlay 2`.
8. Confirm `overlay_01.mp4` fades above the main video, then hides while main continues.
9. Tap `Live 3`.
10. Confirm the live URL layer appears if the URL is reachable on the local network.
11. Tap `Blackout 4`.
12. Confirm blackout covers visible media without crashing the app.
13. Tap `Clear`.

## Show mode

Controls and status text are hidden by default. Tap the top-left corner five times quickly to show or hide the Arabic settings/control panel.

`Identify` may briefly show a status badge, then it should disappear again if the panel is not pinned open.

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
- Clean show mode has no persistent writing over the video.
- Portrait and landscape orientations are both available from settings.
- Device ID, device name, server host/port, brightness, scale mode, orientation, and show mode preferences persist after restart.
- Bonjour discovery fills server host/port when a matching StageCore service is found.
- The app advertises StageCore-controllable settings and media-scan capabilities.
- Legacy OSC remains a rehearsal/debug path, not the final StageCore authority path.
