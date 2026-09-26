# Experimental MJPEG Live trial (not a show release)

Scope: the installed RC3 (102) remains untouched. This PR's debug APK has a
separate package com.stagecore.player.mjpegtrial and the launcher label
StageCore MJPEG Trial. Do not uninstall or overwrite RC3 to run this test.

## Gate A — CI and network

1. Confirm PR #27 latest head has green Android CI for testDebugUnitTest and
   assembleDebug. Download its stagecore-player-debug-apk artifact.
2. Verify camera and relay health independently. Expose relay only on a
   trusted show LAN with explicit -allow-lan, never port-forward 9081.
3. Close direct camera and relay stream browser tabs; use only relay MJPEG URL.
4. Check relay viewers=0 before opening the app. Force-stop RC3 during the
   trial to avoid concurrent OSC servers and duplicate device heartbeats.

## Gate B — physical tablet

1. Install the separately named trial debug APK; preserve installed RC3.
2. Open settings with five top-left taps and set Live URL to
   http://<pi-show-lan-ip>:9081/api/v0/stream.
3. Tap Test Live URL once. Expect CONNECTING, then READY only after decoding
   and displaying the first JPEG. Capture details on any FAILED status.
4. Query relay health: expect one extra viewer (viewer is an HTTP connection,
   not necessarily a distinct device).
5. Tap Hide Live; confirm viewer slot released. Repeat three times.
6. While playing Live, physically turn the camera into portrait and switch the new\n   Live Rotation buttons through 0/90/180/270 degrees; choose 90 or 270 so\n   the image is upright. Fit must show the complete 4:3 sensor frame rotated\n   to 3:4, Crop may cut sides, Full may distort aspect. Restart trial and\n   confirm the selected Live Rotation persists for this device. Verify MP4\n   main/overlay still work and tablet screen orientation is independent.
7. Temporarily interrupt camera Wi-Fi and restore; record bounded retry and
   READY recovery (if firmware instead remains in provisioning, log separately).
8. From StageCore, dispatch `TABLET_LIVE_SHOW` with the relay Direct URL.
   The command may be ACCEPTED while connecting, but it must not become
   COMPLETED until the first frame is rendered. If no frame arrives within the
   first-frame deadline, expect one TIMED_OUT result and the relay viewer slot
   to be released.
9. With the tablet already assigned ACTIVE to a Project, cold-start the app.
   The process-wide device connection may register before MainActivity finishes
   loading media state, but the tablet must become READY without a manual
   disconnect/reconnect once the local manifest is ready. Legacy Project and
   Runtime Snapshot fields in tablet_manifest.json must not block the Hub-owned
   ACTIVE assignment.

## Gate C — four physical tablets

After Gate B passes, run trial app on four physical tablets on trusted show
LAN; verify viewers=4 during sustained playback, no freezes, acceptable
latency, and valid release after Hide Live. A Pi-only four-reader smoke
test does not establish four-tablet qualification. Measure RSSI in venue.

## Rollback

Close the trial app and stop the standalone relay with Ctrl+C. Reopen RC3.
Trial has separate Android package/settings. Keep current Hub and camera
firmware unchanged.
