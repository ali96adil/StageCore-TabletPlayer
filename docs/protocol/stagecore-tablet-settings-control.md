# StageCore Tablet Settings Control Foundation

## Purpose

StageCore Player must be usable locally on the tablet, but the architecture must assume that StageCore can eventually control every production-critical tablet setting remotely.

The tablet settings screen remains useful for setup and rehearsals. StageCore becomes the authoritative production controller when a tablet is paired and running a StageCore project.

## Control boundary

StageCore may request or apply the following settings through the authenticated device protocol:

- `device_id` for stable tablet identity.
- `device_name` for the human-readable actor/tablet name.
- `server_host` and `server_port` for the selected StageCore server.
- `auto_discover` for Bonjour/NSD discovery behavior.
- `brightness_percent` for screen brightness.
- `video_scale_mode` with values `FULL`, `FIT`, or `CROP`.
- `orientation_mode` with values `AUTO`, `PORTRAIT`, or `LANDSCAPE`.
- `show_mode_on_launch` for clean launch behavior.
- `show_lock_enabled` for locked show behavior when implemented.

The app advertises these as capabilities in `device.hello`. StageCore should not send a settings command unless the tablet reports the relevant capability.

## Command types

The planned command names are:

```text
/tablet.settings.read
/tablet.settings.apply
/tablet.settings.reset
/tablet.permissions.check
/tablet.media.prepare_folder
/tablet.media.scan
```

When transported through the final StageCore channel, these names should live inside the normal StageCore command envelope with:

- `command_id`
- `project_id`
- `runtime_snapshot_id`
- `deadline_at`
- `correlation_id`
- `payload`

The tablet must reject stale project/snapshot commands the same way it rejects stale cue commands.

## Settings apply payload

Example payload:

```json
{
  "device_name": "Tablet 01 - Actor A",
  "brightness_percent": 85,
  "video_scale_mode": "CROP",
  "orientation_mode": "PORTRAIT",
  "show_mode_on_launch": true,
  "show_lock_enabled": true
}
```

The tablet should apply only recognized keys, validate value ranges, persist the accepted settings, and return a result that includes both accepted and rejected keys.

## Media scan payload

StageCore can ask a tablet to scan local files before a show.

The scan result should include:

- media folder path.
- manifest file state.
- discovered `main_*.mp4` files.
- discovered `overlay_*.mp4` files.
- required manifest media keys.
- missing required media files.
- live URLs from the manifest.
- storage permission state.

A tablet is `READY` only when all required local media for its active manifest is readable. If files are missing, the tablet should report `MISMATCH` or `DEGRADED`, not silently continue.

## Production rule

Manual tablet settings are allowed for setup, but during a StageCore-controlled show the active StageCore project/snapshot wins. The tablet should not execute settings or cue commands from a different project just because the visible cue numbers or tablet names match.
