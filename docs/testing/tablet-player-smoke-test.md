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

## Show lock behavior

Open show mode and verify:

- The screen stays awake while the app is open.
- Android system bars/navigation are hidden with immersive sticky mode.
- Pressing the Android Back button does not close the app during show mode.
- Pressing Back while the settings panel is open hides the panel and returns to show mode.
- The settings panel still opens only with five quick taps in the top-left corner.
- The app can only be closed from the Arabic settings panel using `خروج من التطبيق`.
- `قفل التطبيق` requests Android Lock Task / Screen Pinning mode when the device allows it.

Important limitation: a normal Android app cannot completely disable the physical power button on every tablet unless Android kiosk/device-owner policy is configured. `KEEP_SCREEN_ON`, immersive mode, and optional Lock Task / Screen Pinning are the supported app-side protections.

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
- Show mode keeps the screen awake and hides Android system UI.
- Back does not exit the app during show mode.
- App exit is only exposed inside the Arabic settings panel.
- Portrait and landscape orientations are both available from settings.
- Device ID, device name, server host/port, brightness, scale mode, orientation, and show mode preferences persist after restart.
- Bonjour discovery fills server host/port when a matching StageCore service is found.
- Main video, overlay video, live video, and blackout layers are all testable.
- Legacy OSC remains a rehearsal/debug path, not the final StageCore authority path.
