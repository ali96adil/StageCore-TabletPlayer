# Tablet Player V1 RC2

This release candidate is based on the current official StageCore device-channel mainline.

## Identity

- versionCode: 101
- versionName: 1.0.0-rc2
- build label: © 2026 Ali Adil — ali96adil@gmail.com — All rights reserved

## Included integration

- authenticated StageCore pairing
- authenticated Stage Device runtime over WSS
- project/runtime-snapshot/tablet-manifest scope enforcement
- main, overlay, live and blackout command handling
- reconnect without command replay
- legacy OSC retained for rehearsal/debug compatibility only

## Physical qualification gate

RC2 is not final until it is installed on a target Android tablet and verified against the qualified StageCore Hub for pairing, readiness, media playback, overlay clear, live layer, blackout/clear, disconnect/reconnect and scope mismatch rejection.
