# StageCore Tablet Health Heartbeat Foundation

## Purpose

StageCore Player tablets should report lightweight health/status information back to StageCore during setup, rehearsal, and show mode. The goal is operator awareness without wasting tablet battery or flooding the local network.

This is a foundation for the final StageCore device channel. The first implementation may reuse the local rehearsal/status path, but the production path should use the authenticated StageCore channel after pairing.

## Default update rate

Use a moderate heartbeat interval by default:

```text
Normal / show-ready: every 10 seconds
During active problem: every 3 seconds until the problem is acknowledged or cleared
Low power mode: every 20-30 seconds
```

The tablet should also send an immediate heartbeat when an important state changes, such as media missing, permission failure, live playback error, blackout, low battery, or StageCore disconnect.

## Status fields

Each heartbeat should include:

```json
{
  "type": "tablet.health.heartbeat",
  "schema_version": "stagecore.tablet.health/1",
  "device_id": "tablet-...",
  "device_name": "Tablet 01 - Actor A",
  "timestamp_ms": 0,
  "app_mode": "SHOW",
  "show_lock_enabled": true,
  "stagecore_connection": "CONNECTED",
  "battery_percent": 82,
  "battery_charging": false,
  "brightness_percent": 85,
  "orientation_mode": "PORTRAIT",
  "video_scale_mode": "CROP",
  "project_id": "...",
  "runtime_snapshot_id": "...",
  "tablet_manifest_id": "...",
  "media_readiness": "READY",
  "missing_media": [],
  "permission_state": "OK",
  "player_state": {
    "main": "main_01.mp4",
    "main_playing": true,
    "overlay": "none",
    "live": "none",
    "blackout": false
  },
  "last_error": null
}
```

## Readiness values

```text
READY       all required local media is readable and permissions are OK
DEGRADED    non-critical problem exists, such as live URL unreachable during rehearsal
MISMATCH    active manifest requires media files that are missing or unreadable
BLOCKED     permissions prevent media scan/playback
OFFLINE     StageCore has not received a heartbeat inside the stale timeout
```

## Alert rules in StageCore

StageCore should create operator notifications for:

- Battery below a configured threshold, suggested first threshold: 25% warning, 15% critical.
- Tablet stopped sending heartbeat for more than 30 seconds during rehearsal or show mode.
- `media_readiness` changes from `READY` to `MISMATCH`, `BLOCKED`, or `DEGRADED`.
- Missing required files from the active manifest.
- Storage permission is missing.
- Live playback error or unreachable live URL when a live cue is required.
- App leaves show mode unexpectedly.
- Tablet is on a different project/snapshot than the active StageCore show.

## Power/network rule

Heartbeat must stay lightweight:

- Send compact JSON only.
- Do not include video thumbnails or large logs.
- Do not scan the file system on every heartbeat. Cache the last media scan and refresh it only when StageCore asks, the manifest changes, the media folder is prepared, or the operator presses scan.
- Prefer 10-second normal interval so six tablets do not create unnecessary traffic or battery drain.

## StageCore command relation

The health heartbeat complements these StageCore commands:

```text
tablet.permissions.check
tablet.media.prepare_folder
tablet.media.scan
tablet.settings.read
tablet.settings.apply
```

StageCore can use heartbeat data for a dashboard, but final readiness should still be confirmed by explicit `tablet.media.scan` before the show starts.
