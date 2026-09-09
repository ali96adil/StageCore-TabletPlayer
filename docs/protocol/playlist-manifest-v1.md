# Playlist / Cue Manifest v1

## Decision

The preferred production design is to publish a complete tablet cue manifest from StageCore to each paired tablet before rehearsal/show runtime, then dispatch small cue and transition commands during execution.

This is better than sending full media/action details on every cue because tablets can validate readiness ahead of time and StageCore can send compact, deterministic runtime signals.

## StageCore Cue List vs Tablet Cue List

StageCore keeps the authoritative show cue list. A tablet may receive a filtered tablet-specific cue list derived from the published StageCore Runtime Snapshot.

That means tablet cue numbering is local to that tablet manifest and does not have to match the StageCore cue sequence.

Example:

| StageCore Cue Sequence | StageCore Cue ID | Tablet Local Cue Sequence | Tablet Action |
|---:|---|---:|---|
| 10 | `sc_cue_010` | 1 | `main.play main_01` |
| 14 | `sc_cue_014` | 2 | `overlay.play overlay_01 dissolve=1000ms` |
| 22 | `sc_cue_022` | 3 | `live.show live_camera_01` |
| 23 | `sc_cue_023` | 4 | `live.hide live_camera_01` |

The tablet executes by stable IDs, not by human sequence labels alone. The local sequence is for readability and offline fallback.

## Why this is safer

- StageCore remains authoritative for the whole show.
- Tablets only receive the cues/actions relevant to them.
- A tablet can preload and validate all local media before the show.
- Runtime commands can be small and fast: `prepare cue_id`, `go cue_id`, `transition action_id`.
- Cue renumbering in StageCore does not break tablet playback if stable IDs remain the same.

## High-level flow

```text
1. Operator edits the StageCore Cue List / Playlist.
2. StageCore publishes a Runtime Snapshot.
3. StageCore derives one tablet manifest per target tablet/player role.
4. Each paired tablet receives/downloads its manifest.
5. Each tablet validates local media availability and capabilities.
6. Tablet reports READY or MISMATCH/DEGRADED with clear reasons.
7. During show, StageCore sends only cue/transition commands by stable cue/action IDs.
```

## Protocol identity

Initial proposed protocol:

```text
protocol: stagecore.device/1
device_type: TABLET_PLAYER
```

The tablet must reject unsupported protocol versions clearly instead of guessing.

## Manifest responsibilities

The manifest defines what the tablet may execute in the current Runtime Snapshot:

- project identity;
- runtime snapshot identity;
- show name;
- device identity / target role;
- StageCore cue IDs and optional StageCore sequence labels;
- tablet local cue IDs and local cue sequence labels;
- per-tablet actions;
- media logical keys;
- local filename expectations;
- transitions such as dissolve;
- live source definitions;
- capability requirements;
- expiry/version metadata.

The manifest does not make the tablet authoritative for the show. StageCore remains the authority.

## Media keys

StageCore uses logical media keys, not Android file paths.

Examples:

```text
main_01
overlay_01
live_camera_01
```

The tablet maps keys to local files or network URLs according to the manifest and local storage rules.

## File naming convention

Primary video files:

```text
main_01.mp4
main_02.mp4
main_03.mp4
main_04.mp4
main_05.mp4
main_06.mp4
```

Overlay video files:

```text
overlay_01.mp4
overlay_02.mp4
...
overlay_99.mp4
```

Default local folder:

```text
/sdcard/TheatreVideos/
```

This folder path is local app/device configuration. StageCore should not depend on it directly.

## Minimal manifest shape

```json
{
  "schema_version": 1,
  "protocol": "stagecore.device/1",
  "device_type": "TABLET_PLAYER",
  "project_id": "project_001",
  "runtime_snapshot_id": "snapshot_001",
  "show_name": "العميان",
  "target_device_id": "tablet-001",
  "media": [
    {
      "key": "main_01",
      "kind": "MAIN_VIDEO",
      "filename": "main_01.mp4"
    },
    {
      "key": "overlay_01",
      "kind": "OVERLAY_VIDEO",
      "filename": "overlay_01.mp4"
    },
    {
      "key": "live_camera_01",
      "kind": "LIVE_SOURCE",
      "url": "http://192.168.3.50:8080/live.m3u8"
    }
  ],
  "tablet_cues": [
    {
      "tablet_cue_id": "tablet_cue_001",
      "tablet_sequence": 1,
      "source_stagecore_cue_id": "sc_cue_010",
      "source_stagecore_sequence": 10,
      "label": "Start main video",
      "actions": [
        {
          "action_id": "action_001",
          "capability": "tablet.media.main.play",
          "media_key": "main_01"
        }
      ]
    },
    {
      "tablet_cue_id": "tablet_cue_002",
      "tablet_sequence": 2,
      "source_stagecore_cue_id": "sc_cue_014",
      "source_stagecore_sequence": 14,
      "label": "Overlay dissolve",
      "actions": [
        {
          "action_id": "action_002",
          "capability": "tablet.media.overlay.play",
          "media_key": "overlay_01",
          "transition": {
            "type": "DISSOLVE",
            "in_ms": 1000,
            "out_ms": 1000
          }
        }
      ]
    }
  ]
}
```

## Runtime command model

After the manifest is prepared, runtime commands should be small signals such as:

```json
{
  "type": "tablet.cue.prepare",
  "command_id": "command_001",
  "runtime_snapshot_id": "snapshot_001",
  "tablet_cue_id": "tablet_cue_002",
  "source_stagecore_cue_id": "sc_cue_014",
  "deadline_at": "2026-09-09T19:30:05.000Z"
}
```

```json
{
  "type": "tablet.cue.go",
  "command_id": "command_002",
  "runtime_snapshot_id": "snapshot_001",
  "tablet_cue_id": "tablet_cue_002",
  "source_stagecore_cue_id": "sc_cue_014",
  "target_at": "2026-09-09T19:30:06.000Z"
}
```

## Command execution rules

- `command_id` executes at most once.
- Expired commands are rejected.
- A command with mismatched `runtime_snapshot_id` is rejected.
- `PREPARE` returns `COMPLETED` only after local media is actually ready.
- Missing media returns `FAILED` with a clear error such as `MEDIA_NOT_FOUND`.
- Reconnect does not replay old `PLAY`, `ALERT`, or `CHIME` commands.
- Countdown and scheduled GO use absolute `target_at` timestamps.

## Result reporting

Tablet command result statuses:

```text
ACCEPTED
REJECTED
COMPLETED
FAILED
TIMED_OUT
CANCELLED
```

A tablet may report `ACCEPTED` quickly, then later report the final result. It must not report `COMPLETED` until the requested operation is actually true.

## Observations

The tablet sends `device.observation` periodically and on important changes:

- connection/readiness state;
- current main media;
- prepared media;
- overlay state;
- live layer state;
- blackout/display state;
- battery if available;
- measured latency/jitter only if actually measured.
