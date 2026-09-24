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
6. Rotate, test Fit/Crop/Full, and verify MP4 main/overlay still work.
7. Temporarily interrupt camera Wi-Fi and restore; record bounded retry and
   READY recovery (if firmware instead remains in provisioning, log separately).

## Gate C — four physical tablets

After Gate B passes, run trial app on four physical tablets on trusted show
LAN; verify viewers=4 during sustained playback, no freezes, acceptable
latency, and valid release after Hide Live. A Pi-only four-reader smoke
test does not establish four-tablet qualification. Measure RSSI in venue.

## Rollback

Close the trial app and stop the standalone relay with Ctrl+C. Reopen RC3.
Trial has separate Android package/settings. Keep current Hub and camera
firmware unchanged.
