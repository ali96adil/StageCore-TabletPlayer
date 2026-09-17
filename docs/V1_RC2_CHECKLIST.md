# V1 RC2 Physical Checklist

- Install RC2 APK on target tablet.
- Confirm versionCode 101, versionName 1.0.0-rc2 and owner build label.
- Confirm clean Show Mode launch and Show Lock recovery path.
- Pair with StageCore over the official authenticated device channel.
- Confirm ONLINE / READY state appears in StageCore Tablet Controller.
- Confirm project/runtime snapshot scope matches the active tablet manifest.
- PREPARE and GO main media from StageCore.
- Pause and stop main media.
- Play and clear overlay without interrupting main media.
- Show and hide live layer.
- Blackout and clear blackout.
- Execute a graphical Cue Action through the StageCore Cue Engine.
- Disconnect network, reconnect, and verify no command replay.
- Verify mismatched project/snapshot command is rejected.
- Verify missing media produces an operator-visible failure.

Only after all checks pass should RC2 be promoted to V1 final.
