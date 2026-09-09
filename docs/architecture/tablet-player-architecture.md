# Tablet Player Architecture

## Goal

`StageCore Player` is an Android endpoint for local media playback under StageCore control. It is also able to operate in standalone rehearsal/fallback mode.

The application must support actor-worn tablets, stage-mounted tablets, and show display/callboard behavior without turning the tablet into an authoritative show controller.

## Runtime model

```text
StageCore Hub
  -> trusted pairing / identity
  -> show manifest publication
  -> cue and transition commands
  -> command lifecycle and audit

StageCore Player
  -> local media manifest
  -> main video layer
  -> overlay video layer
  -> live source layer
  -> display/callboard states
  -> device observations
```

## Internal Android components

### `TabletPlayer`

Owns local playback and visual state:

- main media player for long-running primary videos;
- overlay media player for secondary videos above the main layer;
- live layer for network live video sources;
- blackout and clear states;
- playback state observation;
- media lookup from logical keys to local files.

The main player must continue running when an overlay or live layer is shown above it unless the command explicitly pauses/stops it.

### `StageCoreClient`

Owns StageCore integration:

- pairing and trusted identity;
- WebSocket connection;
- protocol version negotiation;
- exponential reconnect with jitter;
- command validation;
- at-most-once `command_id` execution;
- deadline rejection;
- command result reporting;
- periodic and change-driven observations.

`StageCoreClient` must never block the Android UI thread or the video render/decoder path.

### `LegacyOscServer`

Provides backward compatibility only:

- direct OSC commands for existing rehearsal tests;
- emergency/fallback operation when StageCore Hub is unavailable;
- compatibility with the older Theatre/AlOmian server package.

Legacy OSC is not the primary production protocol once StageCore pairing is available.

## Layer model

The player has three visual layers:

```text
Layer 1: Main Video
Layer 2: Overlay Video
Layer 3: Live Video / Callboard / Display Alerts
```

### Main layer

Used for long primary videos such as:

```text
main_01.mp4
main_02.mp4
...
main_06.mp4
```

### Overlay layer

Used for short secondary videos that appear above the main layer. Overlay playback supports dissolve in/out by animating the overlay view alpha.

When overlay playback ends, the overlay layer clears and the main video is visible at its current running position.

### Live layer

Used to display one network live source, such as an IP camera, phone camera, Mac relay, or StageCore relay on the local router.

The live layer is shown/hidden by cue command. It should not imply frame-perfect sync unless the chosen live transport proves it.

## State safety

- Reconnect must not replay old non-idempotent commands.
- `PLAY`, `ALERT`, and `CHIME` are transient unless StageCore explicitly sends a fresh command.
- Safe display states such as `MESSAGE`, `COUNTDOWN`, `IDLE`, and `BLACKOUT` may be reconciled after reconnect.
- Missing media fails clearly and must not fall back to a different file silently.
- No command is considered successful just because it was received.

## Standalone operation

When StageCore is not reachable, the app keeps local playback and legacy OSC behavior available. StageCore integration is additive, not a hard dependency for rehearsals.
